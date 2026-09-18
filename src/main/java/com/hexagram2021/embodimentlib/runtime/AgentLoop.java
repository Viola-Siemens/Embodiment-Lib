package com.hexagram2021.embodimentlib.runtime;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 推理循环策略（PLAN WP-3 ② 第 5–6 点）。
 * <p>
 * AgentScope 的 {@code ReActAgent} 本身已经实现了「模型 → 工具 → 观测 → 模型」的循环，
 * 因此本包<b>不重新发明循环</b>，而是把本库需要区分的两种环语义抽成策略对象：
 * <ul>
 *   <li>{@link Mode#REACT_STEP}（P0）：逐步环——模型每轮至多推进一个工具调用，
 *       观测回喂后再继续，直到给出最终答案或达到 {@code maxIterations}；</li>
 *   <li>{@link Mode#BATCH_TOOLS}（P1）：批量环——模型一轮返回多个工具调用，
 *       一次性全部执行后统一回喂。</li>
 * </ul>
 * 两种模式的差异体现在 {@code maxIters} 的预算换算与「同轮多调用是否允许」上，
 * 由 {@link AgentLoop} 统一裁决，避免把这个判断散落到调用点。
 *
 * @author liudongyu
 */
public final class AgentLoop {
	/** 默认最大迭代步数（PLAN WP-3 ② 第 5 点）。 */
	public static final int DEFAULT_MAX_ITERATIONS = 12;
	/** 允许配置的迭代上限：防止误配出一个实际无限、持续烧 token 的循环。 */
	public static final int MAX_ITERATIONS_CEILING = 100;

	/** 循环模式。 */
	public enum Mode {
		/** ReAct 逐步环（P0，默认）。 */
		REACT_STEP,
		/** 批量工具调用环（P1）。 */
		BATCH_TOOLS
	}

	private final Mode mode;
	private final int maxIterations;

	private AgentLoop(Mode mode, int maxIterations) {
		this.mode = mode;
		this.maxIterations = maxIterations;
	}

	/**
	 * 构造循环策略。
	 *
	 * @param mode 循环模式
	 * @param maxIterations 最大迭代步数，必须在 {@code [1, MAX_ITERATIONS_CEILING]} 内
	 *
	 * @return 循环策略
	 *
	 * @throws IllegalArgumentException 步数越界
	 * @throws NullPointerException mode 为 null
	 */
	public static AgentLoop of(Mode mode, int maxIterations) {
		Objects.requireNonNull(mode, "mode");
		if (maxIterations < 1 || maxIterations > MAX_ITERATIONS_CEILING) {
			throw new IllegalArgumentException(
				"maxIterations must be in [1, " + MAX_ITERATIONS_CEILING + "], got " + maxIterations);
		}
		return new AgentLoop(mode, maxIterations);
	}

	/**
	 * 默认策略：ReAct 逐步环 + {@value #DEFAULT_MAX_ITERATIONS} 步。
	 *
	 * @return 默认循环策略
	 */
	public static AgentLoop defaults() {
		return new AgentLoop(Mode.REACT_STEP, DEFAULT_MAX_ITERATIONS);
	}

	/** @return 循环模式 */
	public Mode mode() {
		return this.mode;
	}

	/** @return 最大迭代步数 */
	public int maxIterations() {
		return this.maxIterations;
	}

	/**
	 * 是否允许模型在同一轮返回多个工具调用。
	 *
	 * @return 批量模式下 {@code true}，逐步模式下 {@code false}
	 */
	public boolean allowsParallelToolCalls() {
		return this.mode == Mode.BATCH_TOOLS;
	}

	/**
	 * 换算为 AgentScope {@code HarnessAgent.Builder#maxIters(int)} 的取值。
	 * <p>
	 * 逐步环下一步 = 一次模型往返，故直接使用配置值。
	 * 批量环下一轮可能并行执行多个工具，AgentScope 对其内部计数口径与「步」不完全一致，
	 * 因此为批量模式预留一倍余量，避免因预算过早耗尽而在工具执行中途被截断——
	 * 截断发生在工具调用之后、观测回喂之前时，模型会看到一段没有结果的对话，
	 * 表现为「莫名其妙停下来」，比直接给足预算更难排查。
	 *
	 * @return 传给 AgentScope 的 maxIters
	 */
	public int delegateMaxIters() {
		return this.mode == Mode.BATCH_TOOLS ? this.maxIterations * 2 : this.maxIterations;
	}

	/**
	 * 把一条 observation 规约为回喂模型的消息文本。
	 * <p>
	 * 纯函数，便于单测：空观测必须被显式替换，否则模型会收到空串而无法判断工具是否成功。
	 *
	 * @param observation 工具观测文本（可为 null）
	 * @return 非空文本
	 */
	public static String normalizeObservation(@Nullable String observation) {
		if (observation == null || observation.isBlank()) {
			return ToolBridge.EMPTY_OBSERVATION;
		}
		return observation;
	}

	/**
	 * 截断过长的 observation，避免单条工具输出撑爆上下文。
	 * <p>
	 * 与 {@code ToolCallRecord} 的审计截断不同，这里的上限宽松得多（工具输出本身
	 * 是给模型看的信息，截断过狠会让模型失去判断依据），仅用于防御「工具返回整个
	 * 区块扫描结果」这类异常放大。
	 *
	 * @param observation 观测文本
	 * @param maxChars 上限字符数
	 * @return 截断后的文本
	 */
	public static String truncateObservation(String observation, int maxChars) {
		Objects.requireNonNull(observation, "observation");
		if (maxChars < 1) {
			throw new IllegalArgumentException("maxChars must be positive, got " + maxChars);
		}
		if (observation.length() <= maxChars) {
			return observation;
		}
		return observation.substring(0, maxChars) + "...";
	}

	/**
	 * 计算本轮实际要执行的工具调用（逐步模式只取第一个）。
	 * <p>
	 * 逐步环的语义是「每轮只允许模型推进一步」，因此当模型越权返回多个调用时，
	 * 只执行第一个并丢弃其余——而不是全部执行后再回喂，否则就退化成了批量环，
	 * 使两种模式的区分失去意义。
	 *
	 * @param requested 模型本轮请求的工具调用名（顺序即模型给出的顺序）
	 * @return 实际执行列表
	 */
	public List<String> selectInvocations(List<String> requested) {
		Objects.requireNonNull(requested, "requested");
		if (requested.isEmpty()) {
			return List.of();
		}
		if (this.mode == Mode.BATCH_TOOLS) {
			return List.copyOf(requested);
		}
		return List.of(requested.getFirst());
	}

	/**
	 * 构造用于日志/诊断的模式描述。
	 *
	 * @return 形如 {@code "REACT_STEP(max=12)"} 的文本
	 */
	@Override
	public String toString() {
		return this.mode + "(max=" + this.maxIterations + ")";
	}
}
