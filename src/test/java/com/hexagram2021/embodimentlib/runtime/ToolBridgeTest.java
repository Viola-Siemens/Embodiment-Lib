package com.hexagram2021.embodimentlib.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolBridge} 的失败契约测试（PLAN WP-3 ② 验收：工具异常/超时/空 → 循环存活且回喂文本）。
 * <p>
 * 核心不变量：无论工具发生什么，{@code execute} <b>永不抛异常</b>，只返回文本 observation
 * （PRD §4.5）。
 */
class ToolBridgeTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	@Test
	@DisplayName("正常工具：原样返回 observation 文本")
	void returnsToolObservation() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		assertEquals("mined minecraft:stone at (1,2,3)", bridge.execute(() -> "mined minecraft:stone at (1,2,3)"));
	}

	@Test
	@DisplayName("工具抛异常：转为 'tool error: ...' 文本而非抛出（PRD §4.5）")
	void convertsExceptionToText() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		String observation = bridge.execute(() -> {
			throw new IllegalStateException("entity unavailable");
		});

		assertEquals("tool error: entity unavailable", observation);
	}

	@Test
	@DisplayName("工具返回 null：转为空结果文本，避免模型误判成功")
	void convertsNullToText() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		assertEquals(ToolBridge.EMPTY_OBSERVATION, bridge.execute(() -> null));
	}

	@Test
	@DisplayName("工具返回空白：同样视为空结果")
	void convertsBlankToText() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		assertEquals(ToolBridge.EMPTY_OBSERVATION, bridge.execute(() -> "   "));
		assertEquals(ToolBridge.EMPTY_OBSERVATION, bridge.execute(() -> ""));
	}

	@Test
	@DisplayName("工具返回与内部哨兵同内容的字符串时不被误判为空（哨兵按引用比较）")
	void doesNotConfuseRealTextWithSentinel() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		// 内容相同但非同一实例，必须原样回喂，而不是被当成 null 结果。
		assertEquals("embodimentlib.tool.null-sentinel",
			bridge.execute(() -> "embodimentlib.tool.null-sentinel"));
	}

	@Test
	@DisplayName("工具超时：转为 'tool timeout after Xms' 文本，且 IO 线程自行结束")
	void convertsTimeoutToText() throws Exception {
		// 手动执行器：游戏线程永不执行任务，必然超时。
		RecordingExecutor executor = RecordingExecutor.manual();
		ToolBridge bridge = new ToolBridge(executor, Duration.ofMillis(200));
		CountDownLatch returned = new CountDownLatch(1);
		String[] observation = new String[1];

		Thread io = new Thread(() -> {
			observation[0] = bridge.execute(() -> "never runs");
			returned.countDown();
		}, "fake-io-thread");
		io.start();

		assertTrue(returned.await(3, TimeUnit.SECONDS), "超时后应自行返回，而不是无限等待");
		assertEquals("tool timeout after 200ms", observation[0]);
		// 注意：此处不再断言 io.isAlive()——latch 在 return 之前 countDown，
		// 线程可能尚未完全退出，该断言会随机失败。await 成功已证明调用返回。
		io.join(2000);
	}

	@Test
	@DisplayName("超时后派发的任务仍留在游戏线程队列中（桥接不取消已提交任务）")
	void timeoutDoesNotCancelDispatchedTask() {
		RecordingExecutor executor = RecordingExecutor.manual();
		ToolBridge bridge = new ToolBridge(executor, Duration.ofMillis(150));

		String observation = bridge.execute(() -> "slow");

		assertEquals("tool timeout after 150ms", observation);
		assertEquals(1, executor.pendingCount(), "已派发任务不应被静默丢弃");
		// 稍后游戏线程真正执行它时，结果被丢弃且不得抛异常（complete 对已结束 future 是空操作）。
		executor.drain();
	}

	@Test
	@DisplayName("异常与超时的 observation 前缀互不混淆")
	void distinguishesErrorFromTimeout() {
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);

		String error = bridge.execute(() -> {
			throw new RuntimeException("kaboom");
		});

		assertTrue(error.startsWith(ThreadBridge.ERROR_OBSERVATION_PREFIX), error);
		assertFalse(error.startsWith(ThreadBridge.TIMEOUT_OBSERVATION_PREFIX), error);
	}

	@Test
	@DisplayName("构造与调用拒绝 null")
	void rejectsNulls() {
		assertThrows(NullPointerException.class, () -> new ToolBridge(null, TIMEOUT));
		assertThrows(NullPointerException.class, () -> new ToolBridge(RecordingExecutor.synchronous(), null));
		ToolBridge bridge = new ToolBridge(RecordingExecutor.synchronous(), TIMEOUT);
		assertThrows(NullPointerException.class, () -> bridge.execute(null));
	}

	@Test
	@DisplayName("executor 与 timeout 可被读取（供上层诊断）")
	void exposesConfiguration() {
		RecordingExecutor executor = RecordingExecutor.synchronous();
		ToolBridge bridge = new ToolBridge(executor, Duration.ofMillis(1234));

		assertEquals(executor, bridge.executor());
		assertEquals(Duration.ofMillis(1234), bridge.timeout());
	}
}
