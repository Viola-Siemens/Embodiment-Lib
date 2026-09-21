package com.hexagram2021.embodimentlib.api.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentSayEvent} 的数据规约单测（PLAN WP-6 #23）。
 * <p>
 * 事件构造需要真实 {@code LivingEntity}（纯 JUnit 构造不出，见 PLAN §8 决策 18），
 * 因此这里只测可纯函数化的文本校验规则 {@link AgentSayEvent#validateText}。
 */
class AgentSayEventTest {
	@Test
	@DisplayName("validateText：null 抛 NPE")
	void nullTextThrows() {
		assertThrows(NullPointerException.class, () -> AgentSayEvent.validateText(null));
	}

	@Test
	@DisplayName("validateText：空白文本抛 IllegalArgumentException")
	void blankTextThrows() {
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class, () -> AgentSayEvent.validateText("  "));
		assertTrue(ex.getMessage().contains("blank"));
	}

	@Test
	@DisplayName("validateText：非空白文本放行")
	void nonBlankTextPasses() {
		AgentSayEvent.validateText("hello");
		AgentSayEvent.validateText(" 你好 ");
	}
}