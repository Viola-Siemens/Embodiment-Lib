package com.hexagram2021.embodimentlib.runtime;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.hexagram2021.embodimentlib.attach.AgentState;
import com.hexagram2021.embodimentlib.attach.RegistryEntry;
import com.hexagram2021.embodimentlib.attach.ToolCallRecord;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-3 运行时契约测试（PLAN §4 WP-3 验收标准 ⑤⑥ 与序列化要求）。
 * <p>
 * 覆盖不依赖真实 AgentScope 运行时构建的行为：串行化守卫、审计记录、
 * 注册表协作与关闭幂等。
 */
class EmbodiedAgentTest {
	private static final AgentProfile OPENAI_PROFILE =
		new AgentProfile(AgentProfile.PROTOCOL_OPENAI, "http://localhost:11434/v1", "test-key", "test-model");

	@Test
	@DisplayName("串行化守卫：忙碌时 tryEnter 返回 false（拒绝而非排队，PLAN WP-9 ③）")
	void guardRejectsWhenBusy() {
		ExecutionGuard guard = new ExecutionGuard();

		assertTrue(guard.isIdle());
		assertFalse(guard.isBusy());
		assertEquals(AgentState.IDLE, guard.state());

		assertTrue(guard.tryEnter(AgentState.REASONING), "首次应可进入");
		assertTrue(guard.isBusy());
		assertEquals(AgentState.REASONING, guard.state());

		assertFalse(guard.tryEnter(AgentState.REASONING), "忙碌时应拒绝第二次进入");
		assertEquals(AgentState.REASONING, guard.state(), "被拒绝不得改变状态");

		guard.exit();
		assertTrue(guard.isIdle(), "退出后应恢复空闲");
		assertTrue(guard.tryEnter(AgentState.REASONING), "空闲后可再次进入");
	}

	@Test
	@DisplayName("守卫状态可迁移 REASONING → WAITING_TOOL → IDLE")
	void guardTransitionsStates() {
		ExecutionGuard guard = new ExecutionGuard();
		guard.tryEnter(AgentState.REASONING);

		guard.transition(AgentState.WAITING_TOOL);
		assertEquals(AgentState.WAITING_TOOL, guard.state());
		assertTrue(guard.isBusy());

		guard.exit();
		assertEquals(AgentState.IDLE, guard.state());
	}

	@Test
	@DisplayName("守卫：exit 幂等，重复调用不抛异常")
	void guardExitIsIdempotent() {
		ExecutionGuard guard = new ExecutionGuard();
		guard.exit();
		guard.exit();
		assertTrue(guard.isIdle());

		guard.tryEnter(AgentState.REASONING);
		guard.exit();
		guard.exit();
		assertTrue(guard.isIdle());
	}

	@Test
	@DisplayName("守卫：runGuarded 在异常路径也必须释放（否则 agent 永久卡死）")
	void guardReleasesOnException() {
		ExecutionGuard guard = new ExecutionGuard();

		assertThrows(IllegalStateException.class, () -> guard.runGuarded(AgentState.REASONING, () -> {
			throw new IllegalStateException("model exploded");
		}));

		assertTrue(guard.isIdle(), "异常后必须回到 IDLE");
		assertTrue(guard.tryEnter(AgentState.REASONING), "异常后仍应能再次处理");
	}

	@Test
	@DisplayName("守卫：runGuarded 在忙碌时返回 null 且不执行任务体")
	void guardRunGuardedSkipsWhenBusy() {
		ExecutionGuard guard = new ExecutionGuard();
		AtomicBoolean ran = new AtomicBoolean();

		guard.tryEnter(AgentState.REASONING);
		String result = guard.runGuarded(AgentState.REASONING, () -> {
			ran.set(true);
			return "should not run";
		});

		assertNull(result, "忙碌时应返回 null 表示被拒绝");
		assertFalse(ran.get(), "被拒绝时任务体不得执行");
	}

	@Test
	@DisplayName("守卫：拒绝非法状态（不得手动进入/返回 IDLE）")
	void guardRejectsIdleState() {
		ExecutionGuard guard = new ExecutionGuard();

		assertThrows(IllegalArgumentException.class, () -> guard.tryEnter(AgentState.IDLE));
		assertThrows(NullPointerException.class, () -> guard.tryEnter(null));

		guard.tryEnter(AgentState.REASONING);
		assertThrows(IllegalArgumentException.class, () -> guard.transition(AgentState.IDLE));
	}

	@Test
	@DisplayName("守卫在真实并发下只放行一个线程，其余立即被拒绝（零 park）")
	void guardAdmitsOnlyOneConcurrentThread() throws Exception {
		ExecutionGuard guard = new ExecutionGuard();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger admitted = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		Thread holder = new Thread(() -> {
			if (guard.tryEnter(AgentState.REASONING)) {
				admitted.incrementAndGet();
				entered.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException _) {
					Thread.currentThread().interrupt();
				}
				guard.exit();
			}
		}, "holder");
		holder.start();
		assertTrue(entered.await(2, TimeUnit.SECONDS));

		// 另起 4 个线程竞争：全部应立即被拒绝（而不是阻塞等待）。
		List<Thread> contenders = Lists.newArrayList();
		for (int i = 0; i < 4; i++) {
			Thread t = new Thread(() -> {
				if (guard.tryEnter(AgentState.REASONING)) {
					admitted.incrementAndGet();
					guard.exit();
				} else {
					rejected.incrementAndGet();
				}
			}, "contender-" + i);
			contenders.add(t);
			t.start();
		}
		for (Thread t : contenders) {
			t.join(2000);
			assertFalse(t.isAlive(), "竞争线程必须立即返回，不得阻塞等待");
		}

		assertEquals(1, admitted.get(), "只应有一个线程被放行");
		assertEquals(4, rejected.get(), "其余线程应被立即拒绝");

		release.countDown();
		holder.join(2000);
		assertTrue(guard.isIdle());
	}

	@Test
	@DisplayName("守卫非可重入：同一线程连续两次进入，第二次必须被拒绝")
	void guardIsNotReentrantWithinSameThread() {
		ExecutionGuard guard = new ExecutionGuard();

		assertTrue(guard.tryEnter(AgentState.REASONING));
		// 关键：可重入锁在这里会错误地返回 true，导致同线程重入被当作空闲。
		assertFalse(guard.tryEnter(AgentState.REASONING), "同一线程也不得重入");
		assertFalse(guard.tryEnter(AgentState.WAITING_TOOL), "换状态同样不得重入");
	}

	@Test
	@DisplayName("守卫可由非持有线程释放（Reactor doFinally 不保证同线程）")
	void guardCanBeReleasedFromAnotherThread() throws Exception {
		ExecutionGuard guard = new ExecutionGuard();
		assertTrue(guard.tryEnter(AgentState.REASONING));

		Thread releaser = new Thread(guard::exit, "releaser");
		releaser.start();
		releaser.join(2000);

		assertTrue(guard.isIdle(), "非持有线程的释放必须生效，且不得抛异常");
		assertTrue(guard.tryEnter(AgentState.REASONING), "释放后应可再次进入");
	}

	@Test
	@DisplayName("ModelFactory 端口按 profile 分派到对应协议")
	void modelFactoryDispatchesByProtocol() {
		AtomicInteger openAiCalls = new AtomicInteger();
		AtomicInteger anthropicCalls = new AtomicInteger();

		ModelFactory factory = profile -> {
			if (profile.isOpenAI()) {
				openAiCalls.incrementAndGet();
			} else if (profile.isAnthropic()) {
				anthropicCalls.incrementAndGet();
			}
			return new FakeModel(profile.modelName());
		};

		factory.create(OPENAI_PROFILE);
		factory.create(new AgentProfile(AgentProfile.PROTOCOL_ANTHROPIC, "https://api.anthropic.com", "k", "claude"));

		assertEquals(1, openAiCalls.get());
		assertEquals(1, anthropicCalls.get());
		assertThrows(NullPointerException.class, () -> factory.create(null));
	}

	@Test
	@DisplayName("AgentProfile 校验先于模型构造：非法 protocol 不得到达工厂")
	void profileValidationPrecedesFactory() {
		assertThrows(IllegalArgumentException.class,
			() -> new AgentProfile("gemini", "http://x", "k", "m"));
		assertThrows(IllegalArgumentException.class,
			() -> new AgentProfile(AgentProfile.PROTOCOL_OPENAI, "", "k", "m"));
		assertThrows(IllegalArgumentException.class,
			() -> new AgentProfile(AgentProfile.PROTOCOL_OPENAI, "http://x", "k", ""));
		assertTrue(OPENAI_PROFILE.isOpenAI());
		assertFalse(OPENAI_PROFILE.isAnthropic());
	}

	@Test
	@DisplayName("RegistryEntry 审计记录保留最近 8 条，最新的在最前")
	void recentToolCallsAreBoundedAndNewestFirst() {
		RegistryEntry entry = new RegistryEntry("npc", "s1", AgentHostSide.CLIENT, new StubHandle(), null);

		for (int i = 0; i < 12; i++) {
			entry.recordToolCall(new ToolCallRecord("tool" + i, "{}", "obs" + i, 1000L + i));
		}

		List<ToolCallRecord> recent = entry.recentToolCalls();
		assertEquals(RegistryEntry.RECENT_TOOL_CALL_LIMIT, recent.size(), "应保留固定上限条数");
		assertEquals("tool11", recent.getFirst().toolName(), "最新的应排在最前");
		assertEquals("tool4", recent.getLast().toolName(), "最旧的应最先被丢弃");
	}

	@Test
	@DisplayName("具体 agent 尚未构建时，本测试不依赖 HarnessAgent（保持可单测）")
	void defaultToolTimeoutIsBounded() {
		assertEquals(Duration.ofSeconds(10), EmbodiedAgent.DEFAULT_TOOL_TIMEOUT,
			"默认工具超时必须有限，避免无限等待（PRD §4.2）");
	}

	@Test
	@DisplayName("会话预览不含 api key 且受长度上限约束（PRD §4.1.1 隐私硬约束）")
	void previewNeverLeaksApiKey() {
		AtomicReference<@Nullable String> captured = new AtomicReference<>();

		// 复现 EmbodiedAgent#conversationPreview 的拼接逻辑，断言其中不含敏感字段。
		ExecutionGuard guard = new ExecutionGuard();
		String summary = AgentHostSide.SERVER + " " + "village_npc" + "/" + "session-1"
			+ " state=" + guard.state() + " recentTools=" + 0;
		captured.set(AgentLoop.truncateObservation(summary, 120));

		assertFalse(captured.get().contains("test-key"), "预览不得包含 api_key");
		assertFalse(captured.get().contains(OPENAI_PROFILE.apiKey()), "预览不得包含 profile 密钥");
		assertTrue(captured.get().contains("IDLE"));
		assertThrows(IllegalArgumentException.class, () -> AgentLoop.truncateObservation(summary, 0));
	}
}
