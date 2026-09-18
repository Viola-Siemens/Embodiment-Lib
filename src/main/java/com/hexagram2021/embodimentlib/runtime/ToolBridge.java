package com.hexagram2021.embodimentlib.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * 工具执行桥：把工具体投递到游戏线程并把一切失败转成 observation 文本
 * （PLAN WP-3 ② 第 2–3 点，PRD §4.2 / §4.5）。
 * <p>
 * <b>失败契约（PRD §4.5 硬约束）</b>：工具异常 / 超时 / 返回空 / 无有效目标
 * <b>一律</b>转成纯文本 observation 回喂模型，<b>禁止</b>把异常抛进推理循环。原因：
 * 推理循环是 LLM 驱动的，异常会让整个会话崩溃；而文本 observation 能让模型看见
 * 「我这一步失败了，原因是 X」并自行纠正——这正是 ReAct 环的价值所在。
 * <p>
 * 本类刻意不依赖 AgentScope 的 {@code ToolResultBlock}：它只负责产出
 * <b>observation 文本</b>，由 {@code EmbodiedAgent} 侧包装成 AgentScope 所需的类型。
 * 这样工具执行的部分就能脱离 AgentScope 类型被单测覆盖。
 *
 * @author liudongyu
 */
public record ToolBridge(GameThreadExecutor executor, Duration timeout) {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.runtime");

	/**
	 * 工具返回 null 或空白时的观测文本。
	 */
	public static final String EMPTY_OBSERVATION = "tool returned no result";
	/**
	 * 工具无可执行实体（实体已死亡/卸载）时的观测文本（PLAN WP-5 ② 约定）。
	 */
	public static final String ENTITY_UNAVAILABLE_OBSERVATION = "entity unavailable";

	/**
	 * 「工具正常返回 null」的哨兵值。
	 * <p>
	 * 刻意用 {@code new String(...)} 而非字面量：比较依赖<b>引用相等</b>，
	 * 这样即便某个工具真的返回了与哨兵同内容的字符串，也不会被误判为 null。
	 * 因此本类的 null 判定必须始终用 {@code ==}，不得改用 {@code equals}。
	 */
	@SuppressWarnings({"java:S2129", "StringOperationCanBeSimplified"})
	private static final String NULL_RESULT_SENTINEL = new String("embodimentlib.tool.null-sentinel");

	/**
	 * 构造工具桥。
	 *
	 * @param executor 本端游戏线程执行器
	 * @param timeout  单次工具执行的等待上限
	 */
	public ToolBridge(GameThreadExecutor executor, Duration timeout) {
		this.executor = Objects.requireNonNull(executor, "executor must not be null");
		this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
	}

	/**
	 * 在游戏线程上执行工具，并把结果/失败统一规约为 observation 文本。
	 * <p>
	 * 本方法<b>只会在 IO 线程上阻塞</b>（见 {@link ThreadBridge#call}），游戏线程零 park。
	 *
	 * @param task 工具体（在游戏线程执行，返回 observation 文本）
	 * @return 永不为 null 的 observation 文本
	 */
	public String execute(GameThreadTask<String> task) {
		Objects.requireNonNull(task, "task must not be null");
		// 用哨兵值区分「工具正常返回 null」与「ThreadBridge 因超时/中断返回 null」——
		// 二者都是 null，若不区分会把「工具没结果」误报成「工具超时」。
		String observation;
		try {
			observation = ThreadBridge.call(this.executor, () -> {
				String result = task.run();
				return result == null ? NULL_RESULT_SENTINEL : result;
			}, this.timeout);
		} catch (RuntimeException ex) {
			// 工具体抛异常：转文本，不进异常路径（PRD §4.5）。
			LOGGER.debug("Tool execution failed; converting to observation", ex);
			return ThreadBridge.errorObservation(ex);
		}
		if (observation == null) {
			// ThreadBridge 用 null 表示「超时/被中断」；两者对外都表现为超时文本——
			// 被中断意味着服务器正在关停，模型不会再有机会消费这条消息。
			return ThreadBridge.timeoutObservation(this.timeout);
		}
		if (observation == NULL_RESULT_SENTINEL || observation.isBlank()) {
			// 空白观测会让模型误以为工具成功了却什么也没做，必须显式说明。
			return EMPTY_OBSERVATION;
		}
		return observation;
	}

	/**
	 * 在游戏线程上执行工具的回调（同 {@link ThreadBridge.GameThreadTask}，
	 * 此处单独命名以便阅读时明确语义是「工具体」）。
	 *
	 * @param <T> 运行结果类型
	 *
	 * @author liudongyu
	 */
	@FunctionalInterface
	public interface GameThreadTask<T> extends ThreadBridge.GameThreadTask<T> {
	}
}
