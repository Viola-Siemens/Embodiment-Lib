package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ThreadBridge} 的线程契约测试（PLAN WP-3 ② 验收：线程桥接 / 零 park）。
 */
class ThreadBridgeTest {
	@Test
	@DisplayName("call 返回游戏线程上的计算结果")
	void callReturnsTaskResult() {
		RecordingExecutor executor = RecordingExecutor.synchronous();

		String result = ThreadBridge.call(executor, () -> "mined stone", Duration.ofSeconds(1));

		assertEquals("mined stone", result);
		assertEquals(1, executor.submitCount());
	}

	@Test
	@DisplayName("零 park：派发后立即返回，任务未执行前 IO 线程已在等待（游戏线程空闲）")
	void dispatchDoesNotBlockTheCaller() throws Exception {
		// 手动模式：任务只入队，永不自动执行 —— 模拟「游戏线程此刻没空」。
		RecordingExecutor executor = RecordingExecutor.manual();
		AtomicBoolean taskRan = new AtomicBoolean();
		CountDownLatch callerEntered = new CountDownLatch(1);
		CountDownLatch aboutToWait = new CountDownLatch(1);
		AtomicReference<@Nullable String> outcome = new AtomicReference<>("unset");

		Thread io = new Thread(() -> {
			callerEntered.countDown();
			ThreadBridge.setWaitingHook(aboutToWait::countDown);
			try {
				// 超时设得足够短，使「派发本身是否阻塞」与「任务是否执行」可被区分观察。
				outcome.set(ThreadBridge.call(executor, () -> {
					taskRan.set(true);
					return "done";
				}, Duration.ofMillis(300)));
			} finally {
				ThreadBridge.clearWaitingHook();
			}
		}, "fake-io-thread");
		io.start();

		assertTrue(callerEntered.await(2, TimeUnit.SECONDS), "调用线程应已启动");
		// 关键断言：派发已完成（即将进入等待），但任务没跑 —— 说明派发阶段不含等待。
		assertTrue(aboutToWait.await(2, TimeUnit.SECONDS), "IO 线程应已完成派发并进入等待");
		assertFalse(taskRan.get(), "游戏线程未 drain 前，任务不应被执行");
		assertEquals(1, executor.pendingCount(), "任务应停留在游戏线程队列中");

		// IO 线程最终因超时返回 null，全程没有任何一方 park 游戏线程。
		io.join(3000);
		assertFalse(io.isAlive(), "IO 线程应在超时后自行结束");
		assertNull(outcome.get(), "超时后 call 应返回 null");
		assertFalse(taskRan.get(), "任务始终未被 drain，因此未执行");
	}

	@Test
	@DisplayName("drain 后任务在游戏线程执行并唤醒等待中的 IO 线程")
	void drainCompletesTheWaitingCaller() throws Exception {
		RecordingExecutor executor = RecordingExecutor.manual();
		AtomicReference<@Nullable String> outcome = new AtomicReference<>("unset");
		CountDownLatch returned = new CountDownLatch(1);
		// 该闭锁由 ThreadBridge 在「即将进入 future.get」前触发，使下面的断言有确定前提。
		CountDownLatch aboutToWait = new CountDownLatch(1);

		Thread io = new Thread(() -> {
			ThreadBridge.setWaitingHook(aboutToWait::countDown);
			try {
				outcome.set(ThreadBridge.call(executor, () -> "from game thread", Duration.ofSeconds(5)));
			} finally {
				ThreadBridge.clearWaitingHook();
			}
			returned.countDown();
		}, "fake-io-thread");
		io.start();

		// 等到「派发已完成且即将等待」——此时任务必定还在队列里，且 IO 线程尚未拿到结果。
		assertTrue(aboutToWait.await(2, TimeUnit.SECONDS), "IO 线程应已完成派发并进入等待");
		assertEquals(1, executor.pendingCount(), "drain 之前任务应仍在游戏线程队列中");
		assertEquals(1, returned.getCount(), "drain 之前 IO 线程应仍在等待结果");
		assertEquals(1, executor.drain(), "游戏线程应执行 1 个任务");

		assertTrue(returned.await(2, TimeUnit.SECONDS), "任务完成后 IO 线程应被唤醒");
		assertEquals("from game thread", outcome.get());
		io.join(2000);
	}

	@Test
	@DisplayName("任务抛运行时异常时向上抛出，供调用方转 observation")
	void taskExceptionPropagates() {
		RecordingExecutor executor = RecordingExecutor.synchronous();

		IllegalStateException ex = assertThrows(IllegalStateException.class,
			() -> ThreadBridge.call(executor, () -> {
				throw new IllegalStateException("entity unavailable");
			}, Duration.ofSeconds(1)));

		assertEquals("entity unavailable", ex.getMessage());
	}

	@Test
	@DisplayName("超时 observation 文本符合 PRD §4.2 约定的形态")
	void timeoutObservationFormat() {
		assertEquals("tool timeout after 5000ms", ThreadBridge.timeoutObservation(Duration.ofSeconds(5)));
		assertEquals("tool timeout after 250ms", ThreadBridge.timeoutObservation(Duration.ofMillis(250)));
	}

	@Test
	@DisplayName("异常 observation 使用异常 message；无 message 时退化为类名")
	void errorObservationFormat() {
		assertEquals("tool error: boom", ThreadBridge.errorObservation(new RuntimeException("boom")));
		assertEquals("tool error: IllegalStateException",
			ThreadBridge.errorObservation(new IllegalStateException()));
		assertEquals("tool error: IllegalArgumentException",
			ThreadBridge.errorObservation(new IllegalArgumentException("   ")));
	}

	@Test
	@DisplayName("executorFor 按宿主侧选择执行器，绝不交叉（PRD §6.3 双端隔离）")
	void executorForSelectsBySide() {
		RecordingExecutor server = RecordingExecutor.synchronous();
		RecordingExecutor client = RecordingExecutor.synchronous();

		assertEquals(server, ThreadBridge.executorFor(AgentHostSide.SERVER, server, client));
		assertEquals(client, ThreadBridge.executorFor(AgentHostSide.CLIENT, server, client));
	}

	@Test
	@DisplayName("executorFor 缺少对应侧执行器时立即失败，而不是静默跨端")
	void executorForRejectsMissingSide() {
		assertThrows(NullPointerException.class,
			() -> ThreadBridge.executorFor(AgentHostSide.SERVER, null, RecordingExecutor.synchronous()));
		assertThrows(NullPointerException.class,
			() -> ThreadBridge.executorFor(AgentHostSide.CLIENT, RecordingExecutor.synchronous(), null));
	}

	@Test
	@DisplayName("call 拒绝 null 参数")
	void callRejectsNulls() {
		assertThrows(NullPointerException.class,
			() -> ThreadBridge.call(null, () -> "x", Duration.ofSeconds(1)));
		assertThrows(NullPointerException.class,
			() -> ThreadBridge.call(RecordingExecutor.synchronous(), null, Duration.ofSeconds(1)));
		assertThrows(NullPointerException.class,
			() -> ThreadBridge.call(RecordingExecutor.synchronous(), () -> "x", null));
	}

	@Test
	@DisplayName("派发在调用线程完成；任务在执行器线程运行")
	void taskRunsOnExecutorThread() {
		RecordingExecutor executor = RecordingExecutor.synchronous();
		AtomicReference<@Nullable String> taskThread = new AtomicReference<>();

		ThreadBridge.call(executor, () -> {
			taskThread.set(Thread.currentThread().getName());
			return "ok";
		}, Duration.ofSeconds(1));

		assertNotNull(taskThread.get());
		assertEquals(Thread.currentThread().getName(), taskThread.get());
		// synchronous 桩在当前线程执行任务，因此记录的线程名应与调用线程一致。
		assertEquals(1, executor.executedThreadNames().size());
	}
}
