package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * IO 线程 ↔ 游戏线程的桥接（PLAN WP-3 ②，PRD §6.3 线程规则）。
 * <p>
 * <b>核心契约：游戏线程绝不 park。</b> 桥接实现为「单向派发 + 调用方在 IO 线程等待」：
 * <ol>
 *   <li>IO 线程（模型推理所在线程）调用 {@link #call}；</li>
 *   <li>本类创建 {@link CompletableFuture}，把「执行任务并 complete」这段代码
 *       {@link GameThreadExecutor#execute 派发}到游戏线程，然后<b>立即返回</b> ——
 *       派发动作本身不含任何等待；</li>
 *   <li><b>IO 线程</b>在 future 上等待结果（带超时）。此时游戏线程是空闲的，
 *       可以继续跑 tick、处理玩家输入；</li>
 *   <li>游戏线程执行完任务后 complete future，IO 线程被唤醒。</li>
 * </ol>
 * 因此「等待」全部发生在 IO 线程侧。这与「把任务丢进游戏线程再在游戏线程里 join」
 * 的写法有本质区别——后者会直接卡死服务器。
 * <p>
 * <b>超时语义</b>：超时后返回 {@link #TIMEOUT_OBSERVATION_PREFIX 超时 observation} 而非抛异常
 * （PRD §4.2：工具失败转文本）。注意超时**不会**取消已经派发的游戏线程任务——
 * 该任务可能仍在排队，稍后仍会执行并尝试 complete 一个已被放弃的 future（无害，
 * {@link CompletableFuture#complete} 对已完成的 future 是空操作）。
 *
 * @author liudongyu
 */
public final class ThreadBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.runtime");

	/** 工具超时 observation 前缀（PRD §4.2 要求的文本形态）。 */
	public static final String TIMEOUT_OBSERVATION_PREFIX = "tool timeout after ";
	/** 工具执行抛异常时的 observation 前缀（PRD §4.2：失败转文本，禁止抛异常）。 */
	public static final String ERROR_OBSERVATION_PREFIX = "tool error: ";

	/**
	 * 测试钩子：在 {@link #call} 完成派发、即将进入等待的那一刻触发。
	 * <p>
	 * 存在的理由是消除测试竞态。断言「drain 之前 IO 线程仍在等待」需要一个确定的前提：
	 * 派发已完成<b>且</b>尚未开始等待。仅靠「观察到提交」无法排除「游戏线程已经 drain 完」，
	 * 于是断言会随线程调度随机失败（CI 上少核机器尤其容易命中）。
	 * <p>
	 * 生产环境该 ThreadLocal 始终为 null，只多一次 {@code get()}，无实际开销。
	 */
	private static final ThreadLocal<Runnable> WAITING_HOOK = ThreadLocal.withInitial(() -> () -> {
	});

	private ThreadBridge() {
	}

	/**
	 * 仅供测试：安装「即将进入等待」钩子。
	 *
	 * @param hook 钩子；{@code null} 表示恢复为默认空实现
	 */
	static void setWaitingHook(@Nullable Runnable hook) {
		WAITING_HOOK.set(hook == null ? () -> {
		} : hook);
	}

	/** 仅供测试：清除等待钩子，避免污染同线程的后续用例。 */
	static void clearWaitingHook() {
		WAITING_HOOK.remove();
	}

	/**
	 * 在游戏线程上执行一个有返回值的任务，并在<b>当前（IO）线程</b>等待其结果。
	 *
	 * @param executor 本端游戏线程执行器
	 * @param task 任务体；在游戏线程上执行，其返回值作为结果
	 * @param timeout 等待上限
	 * @param <T> 结果类型
	 * @return 任务结果；超时返回 {@code null}（调用方据此产出超时 observation），
	 *         被中断时同样返回 {@code null} 并恢复中断标志
	 */
	@Nullable
	public static <T> T call(GameThreadExecutor executor, GameThreadTask<T> task, Duration timeout) {
		Objects.requireNonNull(executor, "executor");
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(timeout, "timeout");

		CompletableFuture<@Nullable T> future = new CompletableFuture<>();
		// 派发阶段绝不等待：这里只登记任务，随后立即落到下面的 future.get(...)。
		executor.execute(() -> {
			try {
				future.complete(task.run());
			} catch (RuntimeException ex) {
				// 异常不逃逸到游戏线程的事件循环，而是交给 IO 线程侧统一转 observation。
				future.completeExceptionally(ex);
			}
		});

		// 测试钩子：在真正进入等待前发信号，让单测能建立「派发已完成且即将等待」的确定前提，
		// 而不是靠 sleep 赌线程调度（生产环境该 ThreadLocal 为空，零开销）。
		WAITING_HOOK.get().run();

		try {
			// 等待发生在 IO 线程，游戏线程不受影响（本 WP 的零 park 核心）。
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException _) {
			LOGGER.debug("Bridged task timed out after {}", timeout);
			return null;
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException ex) {
			// 任务体异常：包成运行时异常向上抛，由 ToolBridge 转 observation。
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException("bridged task failed", cause);
		}
	}

	/**
	 * 超时 observation 文本（PRD §4.2 指定形态：{@code "tool timeout after Xms"}）。
	 *
	 * @param timeout 超时时长
	 * @return 形如 {@code "tool timeout after 5000ms"} 的文本
	 */
	public static String timeoutObservation(Duration timeout) {
		return TIMEOUT_OBSERVATION_PREFIX + timeout.toMillis() + "ms";
	}

	/**
	 * 异常 observation 文本（PRD §4.2：失败转文本，禁止抛异常给模型）。
	 *
	 * @param error 异常
	 * @return 形如 {@code "tool error: <message>"} 的文本
	 */
	public static String errorObservation(Throwable error) {
		String message = error.getMessage();
		return ERROR_OBSERVATION_PREFIX + (message == null || message.isBlank() ? error.getClass().getSimpleName() : message);
	}

	/**
	 * 可在游戏线程执行、允许返回值的任务。
	 * <p>
	 * 与 {@link java.util.concurrent.Callable} 的区别：这里不允许抛受检异常——
	 * 工具契约（PRD §4.5）规定工具失败必须是文本 observation，而不是异常传播。
	 *
	 * @param <T> 返回值类型
	 */
	@FunctionalInterface
	public interface GameThreadTask<T> {
		/**
		 * 在游戏线程上执行。
		 *
		 * @return 结果
		 */
		@Nullable
		T run();
	}

	/**
	 * 按宿主侧选择执行器的辅助方法。
	 * <p>
	 * 存在意义是让「SERVER 用服务器执行器 / CLIENT 用客户端执行器」这一映射有唯一落点，
	 * 避免各处各写一遍 {@code switch} 而写错方向。
	 *
	 * @param side 宿主侧
	 * @param serverExecutor 服务器线程执行器
	 * @param clientExecutor 客户端线程执行器
	 * @return 该侧对应的执行器
	 */
	@Contract("_, null, null -> fail")
	public static GameThreadExecutor executorFor(AgentHostSide side,
												 @Nullable GameThreadExecutor serverExecutor,
												 @Nullable GameThreadExecutor clientExecutor) {
		return switch (Objects.requireNonNull(side, "side")) {
			case SERVER -> Objects.requireNonNull(serverExecutor, "serverExecutor");
			case CLIENT -> Objects.requireNonNull(clientExecutor, "clientExecutor");
		};
	}
}
