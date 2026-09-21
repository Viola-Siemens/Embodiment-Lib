package com.hexagram2021.embodimentlib.tool.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DropItemLogic} 的单测（PLAN WP-7 #17）。
 * <p>
 * 覆盖：整叠掉落、部分掉落、请求量超过现有量时截断、空槽返回 0。
 */
class DropItemLogicTest {
	@Test
	@DisplayName("resolveCount：未给 count 时掉落整叠")
	void wholeStackWhenCountMissing() {
		assertEquals(5, DropItemLogic.resolveCount(null, 5));
	}

	@Test
	@DisplayName("resolveCount：给定 count 不超过现有量时按给定值掉落")
	void honoursRequestedCount() {
		assertEquals(3, DropItemLogic.resolveCount(3, 5));
		assertEquals(1, DropItemLogic.resolveCount(1, 5));
	}

	@Test
	@DisplayName("resolveCount：请求量超过现有量时截断到现有量（不是错误）")
	void clampsToAvailable() {
		assertEquals(5, DropItemLogic.resolveCount(64, 5));
	}

	@Test
	@DisplayName("resolveCount：空槽返回 0（调用方在此之前已判空）")
	void emptySlotYieldsZero() {
		assertEquals(0, DropItemLogic.resolveCount(null, 0));
		assertEquals(0, DropItemLogic.resolveCount(3, 0));
		assertEquals(0, DropItemLogic.resolveCount(3, -1));
	}

	@Test
	@DisplayName("契约常量：成功文本与 PRD #17 一致")
	void contractConstants() {
		assertEquals("dropped", DropItemLogic.DROPPED);
	}
}
