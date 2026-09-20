package com.hexagram2021.embodimentlib.tool;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * {@link ToolContext} 的线程作用域（PLAN WP-5 ②）。
 * <p>
 * <b>为什么用 ThreadLocal 而不是把 ctx 当参数传</b>：工具体由 LLM 通过 AgentScope 的
 * {@code callAsync(ToolCallParam)} 调用，而该签名是 AgentScope 固定的，无法追加自定义参数。
 * 同时「当前工具正为哪个实体执行」天然是<b>每个游戏线程同一时刻唯一</b>的
 * （游戏线程串行执行 tick，多个 agent 的工具调用不会真正并行），
 * 因此 ThreadLocal 是语义最贴合的载体。
 * <p>
 * <b>安全边界</b>：作用域必须在<b>游戏线程</b>上、包裹工具体执行的那一小段代码内设置。
 * 若在 IO 线程设置，工具体在游戏线程上取不到；若忘记清理，会污染同一线程后续的调用。
 * 因此对外只暴露 {@link #runWith} 这种「设置—执行—必定还原」的形式，不提供裸的 set。
 * <p>
 * <b>嵌套语义</b>：{@link #runWith} 支持嵌套并正确还原外层作用域（保存旧值而非直接置 null），
 * 这样「工具内部再触发一次工具执行」不会把外层绑定弄丢。
 */
public final class ToolContextScope {
	private static final ThreadLocal<@Nullable ToolContext> CURRENT = new ThreadLocal<>();

	private ToolContextScope() {
	}

	/**
	 * 取当前线程绑定的工具上下文。
	 *
	 * @return 当前上下文
	 * @throws IllegalStateException 不在工具作用域内调用（说明有代码路径绕过了
	 *         {@link #runWith}，属于编程错误而非运行时状况，故不返回 null）
	 */
	public static ToolContext get() {
		ToolContext ctx = CURRENT.get();
		if (ctx == null) {
			throw new IllegalStateException("tool invoked outside agent scope");
		}
		return ctx;
	}

	/**
	 * 取当前线程绑定的工具上下文，不在作用域内时返回 {@code null}。
	 * <p>
	 * 供「可能被工具内/外两处调用」的辅助代码使用，避免为了探测作用域而捕获异常。
	 *
	 * @return 当前上下文，或 null
	 */
	public static @Nullable ToolContext getOrNull() {
		return CURRENT.get();
	}

	/**
	 * 当前线程是否处于工具作用域内。
	 *
	 * @return 在作用域内返回 true
	 */
	public static boolean isActive() {
		return CURRENT.get() != null;
	}

	/**
	 * 在指定上下文下执行任务，执行结束后<b>必定</b>还原先前的作用域（含异常路径）。
	 *
	 * @param ctx 本次执行绑定的上下文
	 * @param task 任务体
	 * @param <T> 返回值类型
	 * @return 任务返回值
	 */
	@Nullable
	public static <T> T runWith(ToolContext ctx, Supplier<@Nullable T> task) {
		ToolContext previous = CURRENT.get();
		CURRENT.set(ctx);
		try {
			return task.get();
		} finally {
			// 还原而非 clear：支持嵌套调用，且异常时也必须还原，否则后续工具会读到陈旧绑定。
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}

	/**
	 * 无返回值的 {@link #runWith} 变体。
	 *
	 * @param ctx 本次执行绑定的上下文
	 * @param task 任务体
	 */
	public static void runWith(ToolContext ctx, Runnable task) {
		runWith(ctx, () -> {
			task.run();
			return null;
		});
	}

	/**
	 * 清空当前线程的作用域。
	 * <p>
	 * 仅供测试在用例之间做隔离，避免上一个用例残留的绑定影响下一个。
	 * 生产代码不应调用它——正常路径由 {@link #runWith} 的 finally 负责。
	 */
	static void clearForTesting() {
		CURRENT.remove();
	}
}
