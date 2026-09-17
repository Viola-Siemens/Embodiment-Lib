package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.attach.AgentTestSupport.FakeAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-2 验收标准 ④ + ⑤：双端注册表隔离，以及未附着实体不触发任何兜底副作用。
 * <p>
 * 对应 PLAN WP-2 验收标准第 4、5 条，同时覆盖 PRD §4.1.1 / §6.3 的隔离硬约束。
 *
 * @author liudongyu
 */
class AgentSideIsolationTest {
	@Test
	@DisplayName("AgentRegistry.server() 与 client() 是两个不同实例")
	void serverAndClientAreDifferentInstances() {
		assertNotSame(AgentRegistry.server(), AgentRegistry.client(),
			"双端注册表必须是不同实例，否则智能体与会话会跨端串台");
	}

	@Test
	@DisplayName("forSide 与 server()/client() 返回同一实例（单例语义稳定）")
	void forSideReturnsSingletons() {
		assertSame(AgentRegistry.server(), AgentRegistry.forSide(AgentHostSide.SERVER));
		assertSame(AgentRegistry.client(), AgentRegistry.forSide(AgentHostSide.CLIENT));
	}

	@Test
	@DisplayName("在 server 注册表登记的条目，在 client 注册表里查不到")
	void entriesDoNotLeakAcrossSides() {
		assertTrue(AgentRegistry.server().isEmpty() && AgentRegistry.client().isEmpty(),
			"用例开始前两个全局注册表都应为空（若有残留说明前面的用例污染了全局状态）");

		FakeAgent agent = new FakeAgent("server-side");
		try {
			AgentRegistry.server().register(
				AgentTestSupport.entry("village_npc", "shared-session", AgentHostSide.SERVER, agent));

			assertNull(AgentRegistry.client().get("shared-session"),
				"服务端条目绝不可出现在客户端注册表（PRD §4.1.1 隔离硬约束）");
			assertSame(agent, AgentRegistry.server().get("shared-session").agent());
			assertTrue(AgentRegistry.client().isEmpty());
		} finally {
			// 全局单例必须恢复原状，避免污染同批次其他用例。
			AgentRegistry.server().unregister("shared-session");
		}
	}

	@Test
	@DisplayName("同名 session 可双端并存且互不影响：注销一端不关闭另一端句柄")
	void sameSessionIdCanCoexistOnBothSides() {
		FakeAgent serverAgent = new FakeAgent("server");
		FakeAgent clientAgent = new FakeAgent("client");
		try {
			AgentRegistry.server().register(
				AgentTestSupport.entry("village_npc", "same-id", AgentHostSide.SERVER, serverAgent));
			AgentRegistry.client().register(
				AgentTestSupport.entry("village_npc", "same-id", AgentHostSide.CLIENT, clientAgent));

			AgentRegistry.server().unregister("same-id");

			assertEquals(1, serverAgent.closeCount(), "服务端句柄应被关闭");
			assertEquals(0, clientAgent.closeCount(), "客户端句柄不得被服务端注销误关");
			assertSame(clientAgent, AgentRegistry.client().get("same-id").agent());
		} finally {
			AgentRegistry.server().unregister("same-id");
			AgentRegistry.client().unregister("same-id");
		}
	}

	@Test
	@DisplayName("side() 自报的宿主侧与实际归属一致")
	void sideIsReported() {
		assertEquals(AgentHostSide.SERVER, AgentRegistry.server().side());
		assertEquals(AgentHostSide.CLIENT, AgentRegistry.client().side());
	}

	@Test
	@DisplayName("未附着实体不触发兜底注册：注册表在扫描前后完全不变")
	void unattachedEntitiesProduceNoSideEffects() {
		int serverBefore = AgentRegistry.server().size();
		int clientBefore = AgentRegistry.client().size();
		AgentTestSupport.FakeAttachmentTarget target = new AgentTestSupport.FakeAttachmentTarget();

		// 模拟「加入世界」的兜底扫描路径
		AgentLifecyclePlan.Action joinAction =
			AgentLifecyclePlan.onJoin(AgentHostSide.SERVER, AgentAttachment.isAttached(target));

		assertEquals(AgentLifecyclePlan.Action.NONE, joinAction);
		assertNull(AgentLifecyclePlan.sessionIdOf(target), "未附着实体不应解析出 session-id");
		assertEquals(serverBefore, AgentRegistry.server().size(), "兜底扫描不得写入服务端注册表");
		assertEquals(clientBefore, AgentRegistry.client().size(), "兜底扫描不得写入客户端注册表");
	}

	@Test
	@DisplayName("已附着实体本身也不会被隐式注册：构造智能体是 addon 的职责")
	void attachedEntityIsStillNotAutoRegistered() {
		AgentTestSupport.FakeAttachmentTarget target = new AgentTestSupport.FakeAttachmentTarget();
		AgentAttachment.setType(target, "village_npc");
		AgentAttachment.setSessionId(target, "auto-1");

		// 即便附着完整，库也不得代为注册——否则会用默认模型悄悄发起真实计费请求。
		assertEquals(AgentLifecyclePlan.Action.NONE,
			AgentLifecyclePlan.onJoin(AgentHostSide.SERVER, AgentAttachment.isAttached(target)));
		assertNull(AgentRegistry.server().get("auto-1"));
		assertFalse(AgentRegistry.server().all().stream().anyMatch(e -> e.sessionId().equals("auto-1")));
	}
}
