package com.hexagram2021.embodimentlib.attach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ToolCallRecord} 的截断合约单测。
 * <p>
 * 截断在这里是<b>强制</b>的而非调用方自觉：工具输入可能包含玩家聊天内容，
 * PRD §4.1.1 / §6.3 要求此类文本不得随网络包下发到客户端，
 * 因此无论谁读取记录都拿不到超长原文。
 */
class ToolCallRecordTest {
	@Test
	@DisplayName("短文本原样保留")
	void shortTextIsPreserved() {
		ToolCallRecord record = new ToolCallRecord("move_to", "{\"x\":1}", "moved", 1234L);

		assertEquals("move_to", record.toolName());
		assertEquals("{\"x\":1}", record.input());
		assertEquals("moved", record.observation());
		assertEquals(1234L, record.timestampMillis());
	}

	@Test
	@DisplayName("超长输入被截断到上限，且带省略标记")
	void longInputIsTruncated() {
		String longInput = "x".repeat(ToolCallRecord.MAX_TEXT_LENGTH * 2);

		ToolCallRecord record = new ToolCallRecord("say", longInput, "ok", 0L);

		assertEquals(ToolCallRecord.MAX_TEXT_LENGTH, record.input().length());
		assertTrue(record.input().endsWith(ToolCallRecord.TRUNCATION_SUFFIX));
	}

	@Test
	@DisplayName("超长 observation 同样被截断")
	void longObservationIsTruncated() {
		String longObservation = "y".repeat(ToolCallRecord.MAX_TEXT_LENGTH + 1);

		ToolCallRecord record = new ToolCallRecord("look", "{}", longObservation, 0L);

		assertEquals(ToolCallRecord.MAX_TEXT_LENGTH, record.observation().length());
		assertTrue(record.observation().endsWith(ToolCallRecord.TRUNCATION_SUFFIX));
	}

	@Test
	@DisplayName("恰好等于上限的文本不追加省略标记")
	void exactLimitIsNotTruncated() {
		String exact = "z".repeat(ToolCallRecord.MAX_TEXT_LENGTH);

		assertEquals(exact, ToolCallRecord.truncate(exact));
	}

	@Test
	@DisplayName("null 字段被拒绝")
	void nullFieldsAreRejected() {
		assertThrows(NullPointerException.class, () -> new ToolCallRecord(null, "{}", "ok", 0L));
		assertThrows(NullPointerException.class, () -> new ToolCallRecord("t", null, "ok", 0L));
		assertThrows(NullPointerException.class, () -> new ToolCallRecord("t", "{}", null, 0L));
	}

	@Test
	@DisplayName("null 输入经 truncate 直接拒绝，而不是静默产出 'null' 字面量")
	void truncateRejectsNull() {
		assertThrows(NullPointerException.class, () -> ToolCallRecord.truncate(null));
	}
}
