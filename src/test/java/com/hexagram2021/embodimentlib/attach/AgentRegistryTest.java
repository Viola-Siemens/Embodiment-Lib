package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.attach.AgentTestSupport.FakeAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-2 验收标准 ②：注册表 register / get / unregister / 重复 register 关闭旧条目。
 * <p>
 * 对应 PLAN WP-2 验收标准第 2 条。
 *
 * @author liudongyu
 */
class AgentRegistryTest {
	@Test
	@DisplayName("register 后可以 get 到同一个条目")
	void registerThenGet() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent agent = new FakeAgent("a");
		RegistryEntry entry = AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent);

		RegistryEntry registered = registry.register(entry);

		assertSame(entry, registered);
		assertSame(entry, registry.get("s-1"));
		assertEquals(1, registry.size());
		assertFalse(registry.isEmpty());
	}

	@Test
	@DisplayName("get 未注册的 session 返回 null；null 键也安全返回 null")
	void getUnknownReturnsNull() {
		AgentRegistry registry = new AgentRegistryFixture().registry();

		assertNull(registry.get("nobody"));
		assertNull(registry.get(null));
		assertNull(registry.find("nobody"));
		assertTrue(registry.isEmpty());
	}

	@Test
	@DisplayName("unregister 移除条目并关闭句柄，且只关闭一次")
	void unregisterClosesHandleOnce() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent agent = new FakeAgent("a");
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent));

		RegistryEntry removed = registry.unregister("s-1");

		assertEquals("s-1", removed.sessionId());
		assertEquals(1, agent.closeCount(), "卸载必须关闭句柄");
		assertNull(registry.get("s-1"));
		assertTrue(registry.isEmpty());
	}

	@Test
	@DisplayName("unregister 不存在的 session 是幂等空操作，不抛异常")
	void unregisterUnknownIsNoOp() {
		AgentRegistry registry = new AgentRegistryFixture().registry();

		assertNull(registry.unregister("nobody"));
		assertNull(registry.unregister(null));
		assertTrue(registry.isEmpty());
	}

	@Test
	@DisplayName("重复 register 同一 session：旧条目被关闭，新条目生效")
	void duplicateRegisterClosesPrevious() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent first = new FakeAgent("first");
		FakeAgent second = new FakeAgent("second");

		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, first));
		RegistryEntry current = registry.register(AgentTestSupport.entry("elite", "s-1", AgentHostSide.SERVER, second));

		assertEquals(1, first.closeCount(), "被替换掉的旧句柄必须关闭，否则泄漏");
		assertEquals(0, second.closeCount(), "新句柄不应被关闭");
		assertSame(second, current.agent());
		assertSame(current, registry.get("s-1"));
		assertEquals(1, registry.size(), "同 session 只应保留一个条目");
		assertEquals("elite", registry.get("s-1").agentType());
	}

	@Test
	@DisplayName("重复 register 多次：除最后一个句柄外，其余全部被关闭")
	void repeatedRegisterClosesAllButLast() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent[] agents = new FakeAgent[4];
		for (int i = 0; i < agents.length; i++) {
			agents[i] = new FakeAgent("a" + i);
			registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agents[i]));
		}

		for (int i = 0; i < agents.length - 1; i++) {
			assertEquals(1, agents[i].closeCount(), "第 " + i + " 个句柄应被关闭一次");
		}
		assertEquals(0, agents[agents.length - 1].closeCount(), "最后一个句柄仍在使用");
		assertEquals(1, registry.size());
	}

	@Test
	@DisplayName("不同 session 互不干扰，各自独立关闭")
	void distinctSessionsAreIndependent() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent a = new FakeAgent("a");
		FakeAgent b = new FakeAgent("b");
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, a));
		registry.register(AgentTestSupport.entry("village_npc", "s-2", AgentHostSide.SERVER, b));

		registry.unregister("s-1");

		assertEquals(1, a.closeCount());
		assertEquals(0, b.closeCount(), "注销 s-1 不得影响 s-2");
		assertNull(registry.get("s-1"));
		assertSame(b, registry.get("s-2").agent());
	}

	@Test
	@DisplayName("all() 返回不可变快照，且随注册表变化")
	void allReturnsImmutableSnapshot() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, new FakeAgent("a")));
		registry.register(AgentTestSupport.entry("guard", "s-2", AgentHostSide.SERVER, new FakeAgent("b")));

		List<String> sessionIds = registry.all().stream().map(RegistryEntry::sessionId).sorted().toList();

		assertEquals(List.of("s-1", "s-2"), sessionIds);
		assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
	}

	@Test
	@DisplayName("clear 关闭全部条目并清空注册表")
	void clearClosesEverything() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		FakeAgent a = new FakeAgent("a");
		FakeAgent b = new FakeAgent("b");
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, a));
		registry.register(AgentTestSupport.entry("guard", "s-2", AgentHostSide.SERVER, b));

		registry.clear();

		assertEquals(1, a.closeCount());
		assertEquals(1, b.closeCount());
		assertTrue(registry.isEmpty());
	}

	@Test
	@DisplayName("跨端注册被拒绝：隔离约束不可被静默绕过")
	void crossSideRegistrationIsRejected() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		RegistryEntry clientEntry = AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.CLIENT, new FakeAgent("c"));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> registry.register(clientEntry));

		assertTrue(ex.getMessage().contains("CLIENT") && ex.getMessage().contains("SERVER"),
			"异常信息应点名两端：" + ex.getMessage());
		assertTrue(registry.isEmpty(), "被拒绝的注册不得留下痕迹");
	}

	@Test
	@DisplayName("关闭抛异常的句柄：注册表仍完成替换，异常被吞并记录")
	void closeFailureDoesNotBlockReplacement() {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		EmbodiedAgentHandle throwing = new EmbodiedAgentHandle() {
			@Override
			public AgentState state() {
				return AgentState.IDLE;
			}

			@Override
			public List<ToolCallRecord> recentToolCalls() {
				return List.of();
			}

			@Override
			public String conversationPreview(int maxChars) {
				return "throwing handle";
			}

			@Override
			public void close() {
				throw new IllegalStateException("boom");
			}
		};
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, throwing));
		FakeAgent replacement = new FakeAgent("replacement");

		registry.register(AgentTestSupport.entry("elite", "s-1", AgentHostSide.SERVER, replacement));

		assertSame(
				replacement, registry.get("s-1").agent(),
			"旧句柄关闭失败不得阻断替换——否则该 session 永远无法恢复"
		);
	}

	@Test
	@DisplayName("并发重复 register 同一 session：每个旧句柄恰好被关闭一次")
	void concurrentDuplicateRegisterClosesEachHandleExactlyOnce() throws InterruptedException {
		AgentRegistry registry = new AgentRegistryFixture().registry();
		int threads = 8;
		FakeAgent[] agents = new FakeAgent[threads];
		for (int i = 0; i < threads; i++) {
			agents[i] = new FakeAgent("a" + i);
		}
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			for (int i = 0; i < threads; i++) {
				FakeAgent agent = agents[i];
				pool.execute(() -> {
					try {
						start.await();
						registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent));
					} catch (InterruptedException _) {
						Thread.currentThread().interrupt();
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertTrue(done.await(10, TimeUnit.SECONDS), "并发注册未在超时内完成");
		} finally {
			pool.shutdownNow();
		}

		// 8 个句柄只有 1 个能留在注册表里，其余 7 个必须各自恰好被关闭一次：
		// compute 的原子性保证不会出现两个线程都认为自己替换成功、导致重复关闭或漏关。
		int totalCloses = 0;
		int live = 0;
		for (FakeAgent agent : agents) {
			totalCloses += agent.closeCount();
			if (agent.closeCount() == 0) {
				live++;
			}
		}
		assertEquals(threads - 1, totalCloses, "每个被替换掉的句柄都应恰好关闭一次");
		assertEquals(1, live, "只应剩下一个存活句柄");
		assertEquals(1, registry.size());
	}

	@Test
	@DisplayName("四参构造校验：agent_type / session_id 为空即拒绝")
	void entryRejectsBlankIdentity() {
		FakeAgent agent = new FakeAgent("a");

		assertThrows(IllegalArgumentException.class,
			() -> new RegistryEntry(" ", "s-1", AgentHostSide.SERVER, agent, null));
		assertThrows(IllegalArgumentException.class,
			() -> new RegistryEntry("village_npc", "", AgentHostSide.SERVER, agent, null));
		assertThrows(NullPointerException.class,
			() -> new RegistryEntry("village_npc", "s-1", AgentHostSide.SERVER, null, null));
	}

	@Test
	@DisplayName("最近工具调用记录：新的在前，超出上限丢弃最旧的")
	void recentToolCallsAreBoundedAndOrdered() {
		RegistryEntry entry = AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, new FakeAgent("a"));

		for (int i = 0; i < RegistryEntry.RECENT_TOOL_CALL_LIMIT + 3; i++) {
			entry.recordToolCall(new ToolCallRecord("tool" + i, "{}", "ok", i));
		}

		List<ToolCallRecord> records = entry.recentToolCalls();
		assertEquals(RegistryEntry.RECENT_TOOL_CALL_LIMIT, records.size());
		assertEquals("tool" + (RegistryEntry.RECENT_TOOL_CALL_LIMIT + 2), records.get(0).toolName(),
			"最新的记录应排在最前");
		assertFalse(records.stream().anyMatch(r -> r.toolName().equals("tool0")), "最旧的记录应被丢弃");
	}

	@Test
	@DisplayName("状态默认 IDLE，可被 WP-3 更新")
	void stateDefaultsToIdleAndIsMutable() {
		RegistryEntry entry = AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, new FakeAgent("a"));

		assertEquals(AgentState.IDLE, entry.state());
		entry.setState(AgentState.REASONING);
		assertEquals(AgentState.REASONING, entry.state());
		entry.setState(AgentState.WAITING_TOOL);
		assertEquals(AgentState.WAITING_TOOL, entry.state());
	}

	@Test
	@DisplayName("toString 只含身份与状态，不含任何敏感字段")
	void toStringHasNoSecrets() {
		RegistryEntry entry = AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, new FakeAgent("a"));

		String text = entry.toString();

		assertTrue(text.contains("village_npc") && text.contains("SERVER"));
		assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("key"), "toString 不得泄漏 key 相关字段");
	}

	/** 每个用例独立的注册表实例：全局单例（server/client）会跨用例互相污染。 */
	private static final class AgentRegistryFixture {
		private final AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);

		AgentRegistry registry() {
			return registry;
		}
	}

	@Test
	@DisplayName("isolated 每次返回全新实例，保证用例间互不污染")
	void isolatedReturnsFreshInstance() {
		assertNotSame(AgentRegistry.isolated(AgentHostSide.SERVER), AgentRegistry.isolated(AgentHostSide.SERVER));
	}
}
