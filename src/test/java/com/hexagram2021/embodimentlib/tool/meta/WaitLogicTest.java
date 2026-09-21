package com.hexagram2021.embodimentlib.tool.meta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WaitLogic} 的单测（PLAN WP-6 #22）。
 * <p>
 * 覆盖：ticks 边界（1..200）、越界错误文本、默认值。
 */
class WaitLogicTest {
	@Test
	@DisplayName("ticks 在 [1, 200] 内返回 waited")
	void validTicks() {
		assertEquals(WaitLogic.WAITED, WaitLogic.validate(1));
		assertEquals(WaitLogic.WAITED, WaitLogic.validate(20));
		assertEquals(WaitLogic.WAITED, WaitLogic.validate(200));
	}

	@Test
	@DisplayName("ticks 越界返回带边界的错误文本")
	void outOfRangeTicks() {
		assertTrue(WaitLogic.validate(0).startsWith("invalid input: ticks must be between 1 and 200"));
		assertTrue(WaitLogic.validate(-5).contains("got -5"));
		assertTrue(WaitLogic.validate(201).contains("got 201"));
	}

	@Test
	@DisplayName("默认与边界常量")
	void constants() {
		assertEquals(1, WaitLogic.MIN_TICKS);
		assertEquals(200, WaitLogic.MAX_TICKS);
		assertEquals(20, WaitLogic.DEFAULT_TICKS);
	}
}