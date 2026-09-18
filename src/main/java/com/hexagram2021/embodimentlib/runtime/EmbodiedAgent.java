package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.hexagram2021.embodimentlib.attach.AgentState;
import com.hexagram2021.embodimentlib.attach.EmbodiedAgentHandle;
import com.hexagram2021.embodimentlib.attach.ToolCallRecord;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillFilter;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 智能体运行时包装（PLAN WP-3 ①③，PRD §4.2 / §6.2）。
 * <p>
 * 把 AgentScope 的 {@link HarnessAgent} 包装成 Minecraft 侧可用的对象，并实现 WP-2 定义的
 * {@link EmbodiedAgentHandle} 契约，使其可直接放入 {@code AgentRegistry}。
 * <p>
 * <b>关键设计</b>：
 * <ul>
 *   <li><b>串行化</b>：同一个 agent 的并发 {@code reply} 会被拒绝（见 {@link #reply}），
 *       而不是排队。理由：AgentScope 的会话状态在并发调用下语义不明，
 *       静默排队会让调用方以为「马上就有回复」却实际排到很后面；
 *       显式失败能让命令层（WP-8）立刻回一句「agent busy」——这正是 PLAN WP-9 ③
 *       要求的行为；</li>
 *   <li><b>状态机</b>：{@code IDLE → REASONING → WAITING_TOOL → … → IDLE}。
 *       终止路径（正常/异常/取消）都必须回到 IDLE，否则 agent 会永久卡在忙碌态；</li>
 *   <li><b>关闭幂等</b>：{@link #close()} 可重复调用（WP-2 注册表的替换与注销两条路径
 *       都可能关闭同一句柄）。</li>
 * </ul>
 * <p>
 * <b>线程</b>：{@code reply} 返回 {@link Mono}，订阅与执行发生在调用方选择的线程
 * （AgentScope 默认在 boundedElastic 上跑 HTTP）。本类自身不作线程切换；
 * 工具到游戏线程的桥接由 WP-5/6 的工具体经 {@link ToolBridge} 完成。
 *
 * @author liudongyu
 */
public final class EmbodiedAgent implements EmbodiedAgentHandle {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.runtime");
	/** 会话记录保留条数（与 RegistryEntry 的上限一致）。 */
	private static final int RECENT_TOOL_CALL_LIMIT = 8;

	private final AgentHostSide side;
	private final String agentType;
	private final String sessionId;
	private final HarnessAgent delegate;
	private final AgentLoop loop;

	/** 串行化守卫：忙碌即拒绝，不做排队（见 {@link ExecutionGuard}）。 */
	private final ExecutionGuard guard = new ExecutionGuard();
	private final Deque<ToolCallRecord> recentToolCalls = new ArrayDeque<>(RECENT_TOOL_CALL_LIMIT);
	private volatile boolean closed;

	private EmbodiedAgent(AgentHostSide side, String agentType, String sessionId,
						  HarnessAgent delegate, AgentLoop loop) {
		this.side = side;
		this.agentType = agentType;
		this.sessionId = sessionId;
		this.delegate = delegate;
		this.loop = loop;
	}

	/**
	 * 构建智能体。
	 *
	 * @param side 宿主侧
	 * @param agentType 智能体类型名
	 * @param sessionId 会话身份
	 * @param profile 模型提供方 profile
	 * @param systemPrompt 系统提示词
	 * @param toolkit 工具集（可为 null 表示无工具）
	 * @param sessionDir 会话工作目录（AgentScope workspace；null 则用其默认目录）
	 * @param loop 循环策略
	 * @param modelFactory 模型工厂（生产用 {@link ModelFactory#defaultFactory()}）
	 * @return 构建好的智能体
	 */
	public static EmbodiedAgent create(AgentHostSide side, String agentType, String sessionId,
									   AgentProfile profile, @Nullable String systemPrompt, @Nullable Toolkit toolkit,
									   @Nullable Path sessionDir, AgentLoop loop, ModelFactory modelFactory) {
		Objects.requireNonNull(side, "side");
		Objects.requireNonNull(agentType, "agentType");
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(loop, "loop");
		Objects.requireNonNull(modelFactory, "modelFactory");

		Model model = modelFactory.create(profile);

		HarnessAgent.Builder builder = HarnessAgent.builder()
				// name 参与 AgentScope 的默认 workspace/agentId 推导，必须带上类型以便日志辨认。
				.name("embodimentlib-" + agentType)
				.model(model)
				.sysPrompt(systemPrompt == null ? "" : systemPrompt)
				// 逐步/批量环的预算换算在此裁决（见 AgentLoop#delegateMaxIters）。
				.maxIters(loop.delegateMaxIters());

		if (toolkit != null) {
			builder.toolkit(toolkit);
		}
		if (sessionDir != null) {
			// 会话持久化交给 AgentScope workspace（PLAN §8 决策 6）。
			builder.workspace(sessionDir);
		}

		// 关掉与 Minecraft 场景无关的 harness 能力：这些默认开启的特性会创建额外的
		// 文件系统工具、子 agent 与 plan 模式，既无用又会扩大攻击面/开销。
		// 注意 disableSkills() 是 varargs（语义为「排除指定技能」），传空参等于不排除任何技能，
		// 因此这里必须用显式的 SkillFilter.none()。
		builder.disableSubagents()
				.disableDynamicSubagents()
				.disableFilesystemTools()
				.disableShellTool()
				.skillFilter(SkillFilter.none())
				.disableMemoryTools();

		HarnessAgent delegate = builder.build();
		LOGGER.debug("Created agent {} for session {} on {} ({})", agentType, sessionId, side, loop);
		return new EmbodiedAgent(side, agentType, sessionId, delegate, loop);
	}

	/** @return 宿主侧 */
	public AgentHostSide side() {
		return this.side;
	}

	/** @return 智能体类型名 */
	public String agentType() {
		return this.agentType;
	}

	/** @return 会话身份 */
	public String sessionId() {
		return this.sessionId;
	}

	/** @return 循环策略 */
	public AgentLoop loop() {
		return this.loop;
	}

	@Override
	public AgentState state() {
		return this.guard.state();
	}

	/** @return 智能体是否已关闭 */
	public boolean isClosed() {
		return this.closed;
	}

	/**
	 * 当前是否忙碌（正在推理或等待工具）。
	 *
	 * @return 非 IDLE 时为 {@code true}
	 */
	public boolean isBusy() {
		return this.guard.isBusy();
	}

	@Override
	public List<ToolCallRecord> recentToolCalls() {
		synchronized (this.recentToolCalls) {
			return List.copyOf(this.recentToolCalls);
		}
	}

	/**
	 * 记录一次工具调用（供 WP-5/6 的工具体在管线中回调）。
	 *
	 * @param toolCallRecord 记录
	 */
	public void recordToolCall(ToolCallRecord toolCallRecord) {
		Objects.requireNonNull(toolCallRecord, "toolCallRecord must not be null");
		synchronized (this.recentToolCalls) {
			this.recentToolCalls.addFirst(toolCallRecord);
			while (this.recentToolCalls.size() > RECENT_TOOL_CALL_LIMIT) {
				this.recentToolCalls.removeLast();
			}
		}
	}

	/**
	 * 把一条用户文本送入推理循环。
	 * <p>
	 * <b>并发语义</b>：同一 agent 同时只允许一次 {@code reply}。第二次调用会立即
	 * 以 {@link IllegalStateException} 失败（而不是排队）。调用方（WP-8 命令层）
	 * 应先查 {@link #isBusy()} 并回复 {@code "agent busy"}。
	 * <p>
	 * 返回的 {@link Mono} <b>冷</b>：状态在订阅时才置为 REASONING、结束时回到 IDLE。
	 * 因此「忙碌判定」与「实际执行」之间存在一个窗口——这是刻意的：
	 * 命令层在订阅前就已通过 {@link #isBusy()} 完成检查，此处的锁是第二道防线。
	 *
	 * @param userText 用户输入文本
	 * @return 最终回答文本的 Mono
	 * @throws IllegalStateException 已在处理另一次 reply，或 agent 已关闭
	 */
	public Mono<String> reply(String userText) {
		Objects.requireNonNull(userText, "userText must not be null");
		if (this.closed) {
			return Mono.error(new IllegalStateException("agent is closed: " + this.agentType + "/" + this.sessionId));
		}
		if (!this.guard.tryEnter(AgentState.REASONING)) {
			// 不排队：显式拒绝，让上层能给用户一句「agent busy」（PLAN WP-9 ③）。
			return Mono.error(new IllegalStateException(
					"agent is busy: " + this.agentType + "/" + this.sessionId));
		}
		return Mono.defer(() -> {
			RuntimeContext context = RuntimeContext.builder()
					.sessionId(this.sessionId)
					.build();
			return this.delegate.call(userText, context)
					.map(Msg::getTextContent)
					.doOnSuccess(text -> LOGGER.debug("Agent {} replied ({} chars)", this.agentType,
							text == null ? 0 : text.length()))
					.doOnError(error -> LOGGER.warn("Agent {} reply failed", this.agentType, error))
					// 三条终止路径都必须回到 IDLE，否则 agent 永久卡在忙碌态。
					.doFinally(signal -> this.guard.exit());
		});
	}

	/**
	 * 会话预览（供 WP-8 检查命令；PLAN WP-3 ③）。
	 * <p>
	 * TODO WP-4 提供会话读取接口后接真实历史；当前返回会话身份摘要，
	 * 且<b>绝不</b>包含 api_key 或完整会话内容（PRD §4.1.1 隐私硬约束）。
	 *
	 * @param maxChars 最大字符数
	 * @return 截断后的预览文本
	 */
	public String conversationPreview(int maxChars) {
		if (maxChars < 1) {
			throw new IllegalArgumentException("maxChars must be positive, got " + maxChars);
		}
		String summary = this.side + " " + this.agentType + "/" + this.sessionId
				+ " state=" + this.guard.state() + " recentTools=" + recentToolCalls().size();
		return AgentLoop.truncateObservation(summary, maxChars);
	}

	/**
	 * 关闭智能体并释放底层资源。
	 * <p>
	 * 幂等（WP-2 的 {@link EmbodiedAgentHandle#close()} 契约要求）。
	 */
	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		try {
			this.delegate.close();
		} catch (RuntimeException ex) {
			// 关闭失败不应阻断注册表的替换/注销流程（与 AgentRegistry#closeQuietly 一致）。
			LOGGER.error("Failed to close agent {} for session {}", this.agentType, this.sessionId, ex);
		} finally {
			this.guard.exit();
		}
	}

	/**
	 * 工具执行超时的默认值（供调用方参考）。
	 * <p>
	 * 单独暴露为常量而非散落的字面量，方便未来配置化（PLAN 未要求 0.1 可配）。
	 */
	public static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

	/** 供测试断言：当前记录数的便捷方法。 */
	int recentToolCallCount() {
		return recentToolCalls().size();
	}
}
