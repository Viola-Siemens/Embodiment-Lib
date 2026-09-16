package com.hexagram2021.embodimentlib.attach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexagram2021.embodimentlib.attach.AgentTestSupport.FakeAttachmentTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WP-2 验收标准 ①：附着读写与「空串视为未附着」判定。
 * <p>
 * 对应 PLAN WP-2 验收标准第 1 条。
 */
class AgentAttachmentTest {
	@Test
	@DisplayName("写入后能读回 agent_type 与 session_id")
	void writeThenReadRoundTrip() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();

		AgentAttachment.setType(target, "village_npc");
		AgentAttachment.setSessionId(target, "11111111-2222-3333-4444-555555555555");

		assertEquals("village_npc", AgentAttachment.getType(target));
		assertEquals("11111111-2222-3333-4444-555555555555", AgentAttachment.getSessionId(target));
		assertTrue(AgentAttachment.isAttached(target), "两个字段都已写入时应判定为已附着");
	}

	@Test
	@DisplayName("无附着的实体：读取返回空串而不是抛异常，且判定为未附着")
	void unsetAttachmentReadsAsEmpty() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();

		assertEquals(AgentAttachment.EMPTY, AgentAttachment.getType(target));
		assertEquals(AgentAttachment.EMPTY, AgentAttachment.getSessionId(target));
		assertFalse(AgentAttachment.isAttached(target));
	}

	@Test
	@DisplayName("只写了 agent_type：仍判定为未附着（缺 session-id 无法隔离会话）")
	void typeOnlyIsNotAttached() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();
		AgentAttachment.setType(target, "guard");

		assertFalse(AgentAttachment.isAttached(target), "缺 session-id 时不应视为已附着");
	}

	@Test
	@DisplayName("只写了 session_id：仍判定为未附着（缺 agent_type 无法路由 profile）")
	void sessionIdOnlyIsNotAttached() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();
		AgentAttachment.setSessionId(target, "session-1");

		assertFalse(AgentAttachment.isAttached(target), "缺 agent-type 时不应视为已附着");
	}

	@Test
	@DisplayName("显式写入空串 / 空白串：等同未附着（addon 清空标记的语义）")
	void blankStringsCountAsDetached() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();
		AgentAttachment.setType(target, "   ");
		AgentAttachment.setSessionId(target, "");

		assertEquals(AgentAttachment.EMPTY, AgentAttachment.getSessionId(target),
			"空串应原样读出，由 isAttached 的 isBlank 判定兜底");
		assertFalse(AgentAttachment.isAttached(target));

		AgentAttachment.setType(target, "village_npc");
		AgentAttachment.setSessionId(target, " \t ");
		assertFalse(AgentAttachment.isAttached(target), "纯空白 session-id 不应视为已附着");
	}

	@Test
	@DisplayName("读取附着不产生副作用：未附着的实体被读过之后依然是未附着")
	void readingDoesNotMaterializeDefaultAttachment() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();

		// 若误用 IAttachmentHolder#getData，这一步会把默认值写进实体，
		// 使「扫描一遍世界」变成「给每个实体都挂上附着」。
		AgentAttachment.getType(target);
		AgentAttachment.getSessionId(target);
		AgentAttachment.isAttached(target);

		assertFalse(target.hasAnyAttachment(), "纯读取不得写入任何附着");
	}

	@Test
	@DisplayName("清掉附着后：isAttached 立刻转为 false，但读取依旧安全")
	void detachMakesEntityUnattached() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();
		AgentAttachment.setType(target, "demo_agent");
		AgentAttachment.setSessionId(target, "demo-1");
		assertTrue(AgentAttachment.isAttached(target));

		target.removeAttachment(AttachmentTypes.AGENT_TYPE.get());

		assertFalse(AgentAttachment.isAttached(target));
		assertEquals(AgentAttachment.EMPTY, AgentAttachment.getType(target));
		assertEquals("demo-1", AgentAttachment.getSessionId(target));
	}

	@Test
	@DisplayName("写入 null 被拒绝：附着值不允许为空引用")
	void nullWritesAreRejected() {
		FakeAttachmentTarget target = new FakeAttachmentTarget();

		org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
			() -> AgentAttachment.setType(target, null));
		org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
			() -> AgentAttachment.setSessionId(target, null));
	}
}
