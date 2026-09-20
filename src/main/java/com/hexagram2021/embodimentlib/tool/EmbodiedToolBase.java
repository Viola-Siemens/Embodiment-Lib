package com.hexagram2021.embodimentlib.tool;

import com.hexagram2021.embodimentlib.runtime.ThreadBridge;
import com.hexagram2021.embodimentlib.runtime.ToolBridge;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 所有内置/扩展工具的基类（PLAN WP-5 ①，PRD §4.5 工具契约）。
 * <p>
 * 本类把三件事揉在一起，让子类只需关心「对绑定实体做什么」：
 * <ol>
 *   <li><b>绑定</b>：从 {@link ToolContextScope} 取出「执行时实体」，子类通过 {@link #ctx()} 使用；</li>
 *   <li><b>线程</b>：AgentScope 在 IO 线程调用 {@link #callAsync}，本类确保
 *       {@link #run} <b>只在游戏线程</b>执行（见下方「线程契约」）；</li>
 *   <li><b>失败转文本</b>：子类抛出的任何异常都被转成 observation 文本，绝不进入异常路径。</li>
 * </ol>
 *
 * <h2>线程契约（PRD §6.3，本库最核心的约束）</h2>
 * 子类实现 {@link #run} 时<b>可以安全地直接操作世界</b>，因为它保证运行在游戏线程上。
 * 这一点由 {@link ToolBridge#execute} 落实：等待发生在 IO 线程侧，游戏线程零 park。
 * <p>
 * 但有一个必须说清的前提：<b>{@link #callAsync} 自身并不负责线程切换</b>。
 * AgentScope 从 IO 线程调用它，而本类无法自行把一个任务「推回」游戏线程——
 * 那需要服务器/客户端执行器，只有库的调用链（addon 注册处）才知道。
 * 因此职责划分是：
 * <ul>
 *   <li><b>库侧</b>（WP-5）：本类提供 {@link #executeOnGameThread} 这一明确的桥接入口，
 *       以及 {@link #callAsync} 的默认实现（假定已在游戏线程，适用于
 *       {@code ThreadBridge} 已经包裹过的场景与全部单测）；</li>
 *   <li><b>接线侧</b>（WP-9/10）：用 {@link ToolBridge} 包一层后再执行，
 *       即「{@code bridge.execute(() -> ToolContextScope.runWith(ctx, () -> tool.run(...)))}」。</li>
 * </ul>
 * 这样设计是为了让 23 个工具的实现与测试都不必接触线程细节，
 * 同时不把执行器硬编码进基类（CLIENT 侧要的是 {@code Minecraft#execute}，不是 server）。
 *
 * <h2>失败契约（PRD §4.5 硬约束）</h2>
 * {@link #run} 抛出的异常、返回的 {@code null}/空白，都会被规约为文本 observation。
 * 理由见 {@link ToolBridge}：推理循环由 LLM 驱动，异常会让整个会话崩溃，
 * 而文本 observation 能让模型看见失败原因并自行纠正。
 */
public abstract class EmbodiedToolBase extends ToolBase {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.tool");

	/**
	 * 构造工具（AgentScope 风格 builder 入口）。
	 * <p>
	 * {@code ToolBase.Builder} 没有 {@code build()}——它只承载字段，由子类构造器
	 * {@code super(builder)} 消费。这是 AgentScope 刻意留给子类的扩展点。
	 *
	 * @param name 工具名（建议 {@code "category.action"} 形式，如 {@code "action.mine_block"}）
	 * @param description 给 LLM 看的描述
	 * @param inputSchema 输入参数的 JSON Schema
	 * @param readOnly 是否为只读工具（只读工具在受限执行模式下自动放行）
	 */
	protected EmbodiedToolBase(String name, String description, Map<String, Object> inputSchema,
			boolean readOnly) {
		super(ToolBase.builder()
			.name(name)
			.description(description)
			.inputSchema(inputSchema)
			.readOnly(readOnly)
			// Minecraft 世界操作全部必须在游戏线程串行执行，不存在「并发安全」的工具。
			// 标记为 false 让 AgentScope 不把同轮多个调用并行派发。
			.concurrencySafe(false));
	}

	/**
	 * 取当前工具调用绑定的上下文。
	 *
	 * @return 工具上下文
	 * @throws IllegalStateException 在工具作用域之外调用
	 */
	protected final ToolContext ctx() {
		return ToolContextScope.get();
	}

	/**
	 * 子类实现：对绑定实体执行动作，返回给 LLM 的 observation 文本。
	 * <p>
	 * 实现约定：
	 * <ul>
	 *   <li>保证在<b>游戏线程</b>执行，可直接调用 Minecraft API；</li>
	 *   <li>「可预期的失败」应自行返回文本（如 {@code "not found"} / {@code "out of reach"}），
	 *       而不是抛异常——这样的文本更精确，模型也更容易据此纠正；</li>
	 *   <li>无法预料的异常可以抛出，外层会兜底转成 {@code "tool error: ..."}。</li>
	 * </ul>
	 *
	 * @param ctx 工具上下文（与 {@link #ctx()} 相同，显式传入便于子类签名自解释）
	 * @param input 模型给出的参数（已由 AgentScope 解析为 Map）
	 * @return observation 文本
	 * @throws Exception 任意异常；由外层兜底转文本
	 */
	public abstract String run(ToolContext ctx, Map<String, Object> input) throws Exception;

	/**
	 * 本工具执行前是否要求实体可用。
	 * <p>
	 * 默认要求。极少数工具（如 {@code meta.say} 只是想输出一句话）可在实体消失后
	 * 仍选择不检查，故留此开关。
	 *
	 * @return 实体不可用时是否直接返回 {@code "entity unavailable"}
	 */
	protected boolean requiresUsableEntity() {
		return true;
	}

	/**
	 * AgentScope 调用入口：把 {@link #run} 规约为 {@link ToolResultBlock}。
	 * <p>
	 * 本方法<b>不抛异常</b>（除了参数校验），所有失败都被转成文本块，
	 * 以满足 PRD §4.5「failures are returned as observations, not thrown」。
	 *
	 * @param param AgentScope 的调用参数
	 * @return 恒定发射一个文本结果块（或错误文本块）的 Mono
	 */
	@Override
	public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
		Objects.requireNonNull(param, "param");
		String observation = executeSafely(param.getInput());
		return Mono.just(ToolResultBlock.text(observation));
	}

	/**
	 * 执行工具并把一切失败转成 observation 文本。
	 * <p>
	 * 抽成 public 是为了让调用链（WP-9/10 的接线）与单测可以绕过 AgentScope 的
	 * {@link ToolCallParam} 包装直接驱动工具——这正是 PLAN §3.8
	 * 「工具单测直接以 ToolContext 直驱工具」所要求的能力。
	 *
	 * @param input 模型参数；{@code null} 视为空参数
	 * @return 永不为 null 的 observation 文本
	 */
	public String executeSafely(@Nullable Map<String, Object> input) {
		ToolContext ctx;
		try {
			ctx = ctx();
		} catch (IllegalStateException ex) {
			// 没有绑定上下文说明调用方绕过了 ToolContextScope：这是编程错误，
			// 但对 LLM 而言仍需表现为文本，否则一个接线 bug 会炸掉整轮对话。
			LOGGER.warn("Tool {} invoked without a ToolContext", getName(), ex);
			return ThreadBridge.errorObservation(ex);
		}

		if (requiresUsableEntity() && !ctx.isEntityUsable()) {
			// PRD §4.5 / PLAN WP-5 ② 约定的文本形态。
			return ToolResults.ENTITY_UNAVAILABLE_OBSERVATION;
		}

		Map<String, Object> effectiveInput = ToolResults.normalizeInput(input);
		try {
			return ToolResults.normalizeObservation(run(ctx, effectiveInput));
		} catch (Exception ex) {
			// 兜底：子类没自吞的异常在此转为文本（PRD §4.5）。
			LOGGER.debug("Tool {} failed; converting to observation", getName(), ex);
			return ThreadBridge.errorObservation(ex);
		}
	}

	/**
	 * 在指定执行器上桥接执行本工具（游戏线程安全）。
	 * <p>
	 * 这是给接线侧（WP-9/10）用的便捷入口，把「绑上下文 + 桥线程 + 转文本」三步合一，
	 * 避免每个调用点各写一遍而漏掉某一步。注意本方法<b>会阻塞调用线程</b>（IO 线程），
	 * 但游戏线程零 park——等待语义见 {@link ThreadBridge#call}。
	 *
	 * @param bridge 绑定了本端执行器与超时的工具桥
	 * @param ctx 本次调用的绑定上下文
	 * @param input 模型参数
	 * @return observation 文本
	 */
	public String executeOnGameThread(ToolBridge bridge, ToolContext ctx, @Nullable Map<String, Object> input) {
		return bridge.execute(() -> ToolContextScope.runWith(ctx, () -> executeSafely(input)));
	}

	/**
	 * 读取字符串参数，缺失/空白时返回默认值。
	 * <p>
	 * 纯逻辑实现见 {@link ToolResults#stringParam}——刻意放在无 Minecraft 依赖的类里，
	 * 以便在纯 JUnit 环境下完整覆盖。此处仅作便于子类书写的转发。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 缺失时的默认值
	 * @return 解析结果
	 */
	protected static String stringParam(Map<String, Object> input, String key, String fallback) {
		return ToolResults.stringParam(input, key, fallback);
	}

	/**
	 * 读取整数参数，缺失/不可解析时返回默认值。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 缺失或非法时的默认值
	 * @return 解析结果
	 */
	protected static int intParam(Map<String, Object> input, String key, int fallback) {
		return ToolResults.intParam(input, key, fallback);
	}

	/**
	 * 读取浮点参数，缺失/不可解析时返回默认值。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 缺失或非法时的默认值
	 * @return 解析结果
	 */
	protected static double doubleParam(Map<String, Object> input, String key, double fallback) {
		return ToolResults.doubleParam(input, key, fallback);
	}

	/**
	 * 读取布尔参数，缺失时返回默认值。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 缺失时的默认值
	 * @return 解析结果
	 */
	protected static boolean booleanParam(Map<String, Object> input, String key, boolean fallback) {
		return ToolResults.booleanParam(input, key, fallback);
	}

	/**
	 * 读取必填字符串参数。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @return 解析结果；缺失或空白时为 {@code null}
	 */
	protected static @Nullable String requiredParam(Map<String, Object> input, String key) {
		return ToolResults.requiredParam(input, key);
	}

	/**
	 * 构造一个「对象」类型的 JSON Schema。
	 *
	 * @param properties 属性定义
	 * @param required 必填属性名
	 * @return JSON Schema
	 */
	protected static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
		return ToolResults.objectSchema(properties, required);
	}

	/**
	 * 构造单个属性的 JSON Schema。
	 *
	 * @param type JSON 类型（{@code "string"} / {@code "integer"} / {@code "number"} / {@code "boolean"}）
	 * @param description 给 LLM 看的说明
	 * @return 属性 schema
	 */
	protected static Map<String, Object> prop(String type, String description) {
		return ToolResults.prop(type, description);
	}

	/**
	 * 构造带取值范围约束的数值属性 schema。
	 *
	 * @param type JSON 类型
	 * @param description 说明
	 * @param minimum 下界
	 * @param maximum 上界
	 * @return 属性 schema
	 */
	protected static Map<String, Object> rangedProp(String type, String description, int minimum, int maximum) {
		return ToolResults.rangedProp(type, description, minimum, maximum);
	}

	/**
	 * 构造带枚举约束的字符串属性 schema。
	 *
	 * @param description 说明
	 * @param allowed 允许取值
	 * @return 属性 schema
	 */
	protected static Map<String, Object> enumProp(String description, List<String> allowed) {
		return ToolResults.enumProp(description, allowed);
	}
}