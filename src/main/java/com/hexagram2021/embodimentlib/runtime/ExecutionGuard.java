package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.attach.AgentState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 单次执行守卫：保证同一时刻只有一个推理在跑，且<b>忙碌时拒绝而非排队</b>
 * （PLAN WP-9 ③ 要求的 {@code "agent busy"} 语义，PLAN WP-3 ①）。
 * <p>
 * <b>为什么拒绝而不是排队</b>：排队会让调用方拿到一个「已受理但遥遥无期」的未来，
 * 玩家在聊天里看不到任何反馈；显式拒绝则允许命令层立刻回一句 {@code "agent busy"}。
 * 从 LLM 会话一致性的角度，同一 session 并发推进两条推理也会让上下文交错，
 * 结果不可复现。
 * <p>
 * <b>实现说明：为什么不用锁</b>。占用语义就是「IDLE → 忙碌」这一次原子迁移，
 * 而 {@link AtomicReference#compareAndSet} 恰好提供这一点。<b>不能</b>用
 * {@link java.util.concurrent.locks.ReentrantLock#tryLock()}：可重入锁对<b>同一线程</b>
 * 的重复获取会成功，于是「同一线程连续两次 reply」会被误判为空闲——而这恰恰是
 * 最常见的重入场景（命令处理器在 tick 线程上连续触发两次）。
 * <p>
 * 用 CAS 还有一个附带好处：{@link #exit()} 可以在<b>任意线程</b>调用。
 * Reactor 的 {@code doFinally} 不保证与订阅在同一线程，用锁会出现
 * {@code IllegalMonitorStateException}。
 * <p>
 * 把该守卫从 {@link EmbodiedAgent} 中独立出来，是为了让它能被独立单测——
 * {@code EmbodiedAgent} 的构造需要真实 AgentScope 运行时，而本类的语义
 * （拒绝/放行/状态迁移/异常后必须释放）才是真正需要回归保护的部分。
 *
 * @author liudongyu
 */
final class ExecutionGuard {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.runtime");

	private final AtomicReference<AgentState> state = new AtomicReference<>(AgentState.IDLE);

	/** @return 当前状态 */
	AgentState state() {
		return this.state.get();
	}

	/** @return 是否正忙（非 IDLE） */
	boolean isBusy() {
		return this.state.get() != AgentState.IDLE;
	}

	/** @return 是否空闲（可接受新任务） */
	boolean isIdle() {
		return this.state.get() == AgentState.IDLE;
	}

	/**
	 * 尝试进入忙碌态。
	 *
	 * @param busyState 进入后的状态（如 REASONING）
	 * @return 成功返回 true；已在忙碌则返回 false（调用方据此报 busy）
	 * @throws IllegalArgumentException busyState 为 IDLE
	 * @throws NullPointerException busyState 为 null
	 */
	boolean tryEnter(AgentState busyState) {
		Objects.requireNonNull(busyState, "busyState");
		if (busyState == AgentState.IDLE) {
			throw new IllegalArgumentException("busyState must not be IDLE");
		}
		return this.state.compareAndSet(AgentState.IDLE, busyState);
	}

	/**
	 * 在忙碌态中迁移（如 REASONING → WAITING_TOOL）。
	 *
	 * @param nextState 目标状态
	 * @throws IllegalArgumentException nextState 为 IDLE（应改用 {@link #exit()}）
	 */
	void transition(AgentState nextState) {
		Objects.requireNonNull(nextState, "nextState");
		if (nextState == AgentState.IDLE) {
			throw new IllegalArgumentException("use exit() to return to IDLE");
		}
		this.state.set(nextState);
	}

	/**
	 * 退出忙碌态。<b>幂等</b>，可在任意线程调用。
	 * <p>
	 * 所有终止路径（正常/异常/取消）都必须调用它，否则该 agent 会永久卡在忙碌态，
	 * 再也无法接受任何输入。
	 */
	void exit() {
		this.state.set(AgentState.IDLE);
	}

	/**
	 * 在守卫保护下执行一段同步逻辑，并保证退出时释放（含异常路径）。
	 * <p>
	 * 注意：本方法只适用于<b>同步</b>任务。异步任务（如返回 {@code Mono}）必须在
	 * 终止信号上调用 {@link #exit()}，否则守卫会在订阅前就被释放。
	 *
	 * @param busyState 进入后的状态
	 * @param task 任务体
	 * @param <T> 结果类型
	 * @return 任务结果；无法进入忙碌态时返回 {@code null}
	 */
	<T> @Nullable T runGuarded(AgentState busyState, Supplier<T> task) {
		Objects.requireNonNull(task, "task");
		if (!tryEnter(busyState)) {
			LOGGER.debug("Guard busy; rejecting concurrent execution");
			return null;
		}
		try {
			return task.get();
		} finally {
			exit();
		}
	}
}
