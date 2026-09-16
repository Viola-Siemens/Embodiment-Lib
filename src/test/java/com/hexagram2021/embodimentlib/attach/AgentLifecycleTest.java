package com.hexagram2021.embodimentlib.attach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.attach.AgentLifecyclePlan.Action;
import com.hexagram2021.embodimentlib.attach.AgentTestSupport.FakeAgent;
import com.hexagram2021.embodimentlib.attach.AgentTestSupport.FakeAttachmentTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WP-2 验收标准 ③：实体卸载/移除后注册表条目被清理（以桩驱动 {@code unregister} 路径）。
 * <p>
 * 对应 PLAN WP-2 验收标准第 3 条，以及 PRD §4.4「卸载的实体 agent 不运行，直接关闭」。
 * <p>
 * 这些用例驱动的是 {@link AgentLifecyclePlan} 的决策函数 + 真实的 {@link AgentRegistry}：
 * 事件处理器（{@code AgentLifecycle}）本身只做转发，其行为完全由这里覆盖的组合决定。
 */
class AgentLifecycleTest {
	@Test
	@DisplayName("实体离开世界且登记过条目：决策为注销，执行后条目与句柄都被清理")
	void leaveLevelUnregistersExistingEntry() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);
		FakeAgent agent = new FakeAgent("a");
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent));

		Action action = AgentLifecyclePlan.onRemove(AgentHostSide.SERVER, registry.get("s-1") != null);
		registry.unregister("s-1");

		assertEquals(Action.UNREGISTER, action);
		assertEquals(1, agent.closeCount(), "卸载必须关闭 agent（WP-3 提供 close()）");
		assertNull(registry.get("s-1"));
	}

	@Test
	@DisplayName("实体离开世界但没有条目：决策为空操作，不抛异常")
	void leaveLevelWithoutEntryIsNoOp() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);

		Action action = AgentLifecyclePlan.onRemove(AgentHostSide.SERVER, registry.get("ghost") != null);

		assertEquals(Action.NONE, action, "未知 session 的卸载路径必须静默，否则每个普通实体离开都会刷屏");
	}

	@Test
	@DisplayName("客户端实体离开：决策为空操作，绝不触达服务端注册表")
	void clientSideLeaveNeverTouchesServerRegistry() {
		FakeAgent agent = new FakeAgent("server-agent");
		try {
			AgentRegistry.server().register(
				AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent));

			// 单人游戏里客户端也会收到 leave 事件；若不过滤宿主侧，
			// 客户端实体的卸载会把服务端 agent 关掉。
			Action action = AgentLifecyclePlan.onRemove(AgentHostSide.CLIENT, true);
			assertEquals(Action.NONE, action);

			assertEquals(0, agent.closeCount(), "客户端侧事件不得关闭服务端句柄");
			assertEquals("s-1", AgentRegistry.server().get("s-1").sessionId());
		} finally {
			AgentRegistry.server().unregister("s-1");
		}
	}

	@Test
	@DisplayName("实体已清掉附着但条目仍在：仍决策为注销（否则句柄泄漏）")
	void removalStillWorksAfterAttachmentCleared() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);
		FakeAgent agent = new FakeAgent("a");
		registry.register(AgentTestSupport.entry("village_npc", "s-1", AgentHostSide.SERVER, agent));

		// addon 把村民转回普通村民：附着没了，但注册表条目还在。
		FakeAttachmentTarget target = new FakeAttachmentTarget();

		// 决策依据是「注册表里有没有条目」，而不是实体当前长什么样。
		Action action = AgentLifecyclePlan.onRemove(AgentHostSide.SERVER, registry.get("s-1") != null);
		registry.unregister("s-1");

		assertEquals(Action.UNREGISTER, action);
		assertEquals(1, agent.closeCount());
		assertNull(AgentLifecyclePlan.sessionIdOf(target));
	}

	@Test
	@DisplayName("死亡事件与服务端 tick 扫描：两条清理路径都以 UNREGISTER 收口")
	void deathAndSweepBothUnregister() {
		AgentRegistry registry = AgentRegistry.isolated(AgentHostSide.SERVER);
		FakeAgent died = new FakeAgent("died");
		FakeAgent leaked = new FakeAgent("leaked");
		registry.register(AgentTestSupport.entry("village_npc", "dead-1", AgentHostSide.SERVER, died));
		registry.register(AgentTestSupport.entry("village_npc", "leaked-1", AgentHostSide.SERVER, leaked));

		// 路径 A：LivingDeathEvent（实体死了但 chunk 还没卸载）
		if (AgentLifecyclePlan.onRemove(AgentHostSide.SERVER, registry.get("dead-1") != null) == Action.UNREGISTER) {
			registry.unregister("dead-1");
		}
		// 路径 B：ServerTickEvent.Post 兜底扫描（实体已消失，但 leave 事件没投递）
		if (AgentLifecyclePlan.onSweep(AgentHostSide.SERVER, true, false) == Action.UNREGISTER) {
			registry.unregister("leaked-1");
		}

		assertEquals(1, died.closeCount());
		assertEquals(1, leaked.closeCount());
		assertEquals(0, registry.size());
	}

	@Test
	@DisplayName("扫描决策：实体仍在世界中时不得误杀条目")
	void sweepKeepsLiveEntries() {
		assertEquals(Action.NONE, AgentLifecyclePlan.onSweep(AgentHostSide.SERVER, true, true));
		assertEquals(Action.NONE, AgentLifecyclePlan.onSweep(AgentHostSide.SERVER, false, false),
			"无条目时无需动作");
		assertEquals(Action.NONE, AgentLifecyclePlan.onSweep(AgentHostSide.CLIENT, true, false),
			"客户端侧不持有服务端注册表条目");
		assertEquals(Action.UNREGISTER, AgentLifecyclePlan.onSweep(AgentHostSide.SERVER, true, false));
	}

	@Test
	@DisplayName("sessionIdOf：未附着与空白 session-id 都返回 null，避免脏键查询")
	void sessionIdOfRejectsBlank() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();
		assertNull(AgentLifecyclePlan.sessionIdOf(target));

		target.setAttachment(AttachmentTypes.SESSION_ID.get(), "  ");
		assertNull(AgentLifecyclePlan.sessionIdOf(target), "纯空白不算有效键");

		target.setAttachment(AttachmentTypes.SESSION_ID.get(), "real-session");
		assertEquals("real-session", AgentLifecyclePlan.sessionIdOf(target));
	}

	@Test
	@DisplayName("服务器停止：清空整个服务端注册表并关闭全部句柄")
	void serverStopClearsRegistry() {
		FakeAgent a = new FakeAgent("a");
		FakeAgent b = new FakeAgent("b");
		AgentRegistry.server().register(
			AgentTestSupport.entry("village_npc", "stop-1", AgentHostSide.SERVER, a));
		AgentRegistry.server().register(
			AgentTestSupport.entry("guard", "stop-2", AgentHostSide.SERVER, b));

		AgentRegistry.server().clear();

		assertEquals(1, a.closeCount());
		assertEquals(1, b.closeCount());
		assertEquals(0, AgentRegistry.server().size());
	}
}
