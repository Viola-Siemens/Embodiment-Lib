package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-3 与 WP-2 注册表的集成契约（PLAN §4 WP-3 验收：close 释放注册表项）。
 * <p>
 * 放在 {@code attach} 包内是因为 {@link AgentRegistry#isolated} 是包私有测试缝，
 * 不应为了让别包的测试方便而放宽为 public。
 * <p>
 * 这里使用桩句柄而非真实 {@code EmbodiedAgent}：后者的构造需要真实 AgentScope 运行时
 * 与模型客户端（会创建 HTTP 传输层），不适合放在单测里。真实 agent 与注册表的
 * 行为一致性由二者各自的行为测试 + 本集成契约共同保证。
 */
class AgentRegistryRuntimeIntegrationTest {
	/** 记录关闭次数的桩句柄。 */
	private static final class RecordingHandle implements EmbodiedAgentHandle {
		private int closeCount;

		@Override
		public AgentState state() {
			return AgentState.IDLE;
		}

		@Override
		public List<ToolCallRecord> recentToolCalls() {
			return List.of();
		}

		@Override
		public void close() {
			this.closeCount++;
		}

		int closeCount() {
			return this.closeCount;
		}
	}

	@Test
	@DisplayName("注册后可查得；注销时句柄被关闭一次（close 释放注册表项）")
	void unregisterClosesHandle() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);
		RecordingHandle handle = new RecordingHandle();

		RegistryEntry entry = new RegistryEntry("village_npc", "session-1",
			AgentHostSide.SERVER, handle, null);
		registry.register(entry);

		assertEquals(1, registry.size());
		assertNotNull(registry.get("session-1"));
		assertEquals(0, handle.closeCount(), "注册期间不应关闭句柄");

		RegistryEntry removed = registry.unregister("session-1");

		assertNotNull(removed);
		assertTrue(registry.isEmpty(), "注销后注册表应为空");
		assertNull(registry.get("session-1"));
		assertEquals(1, handle.closeCount(), "注销应关闭句柄一次");
	}

	@Test
	@DisplayName("同一会话重复注册替换旧句柄并关闭它，不泄漏运行时资源")
	void reRegisterClosesPreviousHandle() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.CLIENT);
		RecordingHandle first = new RecordingHandle();
		RecordingHandle second = new RecordingHandle();

		registry.register(new RegistryEntry("npc", "s1", AgentHostSide.CLIENT, first, null));
		registry.register(new RegistryEntry("npc", "s1", AgentHostSide.CLIENT, second, null));

		assertEquals(1, registry.size(), "同会话只应保留一个条目");
		assertEquals(1, first.closeCount(), "被替换的旧句柄应被关闭");
		assertEquals(0, second.closeCount(), "新句柄应保持存活");
	}

	@Test
	@DisplayName("句柄 close 幂等：重复注销不会重复关闭")
	void repeatedUnregisterIsSafe() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);
		RecordingHandle handle = new RecordingHandle();

		registry.register(new RegistryEntry("npc", "s1", AgentHostSide.SERVER, handle, null));
		registry.unregister("s1");
		registry.unregister("s1");

		assertEquals(1, handle.closeCount(), "重复注销不应重复关闭句柄");
		assertTrue(registry.isEmpty());
	}

	@Test
	@DisplayName("SERVER 注册表不含 CLIENT 注册的会话（PRD 双端隔离硬约束）")
	void serverAndClientRegistriesStayIsolated() {
		AgentRegistry server = AgentRegistry.isolated(AgentHostSide.SERVER);
		AgentRegistry client = AgentRegistry.isolated(AgentHostSide.CLIENT);

		server.register(new RegistryEntry("npc", "server-session",
			AgentHostSide.SERVER, new RecordingHandle(), null));
		client.register(new RegistryEntry("npc", "client-session",
			AgentHostSide.CLIENT, new RecordingHandle(), null));

		assertNotNull(server.get("server-session"));
		assertNull(server.get("client-session"), "SERVER 注册表不得看到 CLIENT 会话");
		assertNotNull(client.get("client-session"));
		assertNull(client.get("server-session"), "CLIENT 注册表不得看到 SERVER 会话");

		// 清理，避免影响同一 JVM 内其他测试。
		server.clear();
		client.clear();
	}

	@Test
	@DisplayName("注册跨端的条目被拒绝，避免配置/密钥串端")
	void rejectsCrossSideRegistration() {
		AgentRegistry server = AgentRegistry.isolated(AgentHostSide.SERVER);

		RegistryEntry clientEntry = new RegistryEntry("npc", "s1",
			AgentHostSide.CLIENT, new RecordingHandle(), null);

		assertThrows(IllegalArgumentException.class, () -> server.register(clientEntry));
		assertTrue(server.isEmpty(), "被拒绝的注册不得留下痕迹");
	}

	@Test
	@DisplayName("未附着会话查询返回 null，不抛异常")
	void unknownSessionReturnsNull() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);

		assertNull(registry.get("nope"));
		assertNull(registry.find("nope"));
		assertNull(registry.unregister("nope"));
		assertTrue(registry.isEmpty());
		assertEquals(0, registry.size());
	}
}
