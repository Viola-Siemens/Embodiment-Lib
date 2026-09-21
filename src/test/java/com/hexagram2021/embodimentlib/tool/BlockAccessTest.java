package com.hexagram2021.embodimentlib.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BlockAccess} 的单测（PLAN WP-7 #12/#14/#15/#20/#21 共用的可及性判定）。
 * <p>
 * 覆盖：两种距离常量、以及「严格小于」的边界语义。
 */
class BlockAccessTest {
	@Test
	@DisplayName("within：平方距离严格小于 reach² 时算够得着")
	void withinUsesStrictLessThan() {
		double reach = BlockAccess.INTERACT_REACH;
		assertTrue(BlockAccess.within(0.0, reach));
		assertTrue(BlockAccess.within(reach * reach - 0.001, reach));
		// 恰好等于 reach：原版 closerThan 用严格小于，边界上算「够不着」。
		assertFalse(BlockAccess.within(reach * reach, reach));
		assertFalse(BlockAccess.within(reach * reach + 1.0, reach));
	}

	@Test
	@DisplayName("within：容器距离比交互距离宽松")
	void containerReachIsMoreGenerous() {
		double justOutsideInteract = BlockAccess.INTERACT_REACH * BlockAccess.INTERACT_REACH + 1.0;
		assertFalse(BlockAccess.within(justOutsideInteract, BlockAccess.INTERACT_REACH));
		assertTrue(BlockAccess.within(justOutsideInteract, BlockAccess.CONTAINER_REACH));
	}

	@Test
	@DisplayName("契约常量：4.5 / 6.0 与 PLAN 指定值一致，且失败文本为 PRD 约定形态")
	void contractConstants() {
		assertEquals(4.5, BlockAccess.INTERACT_REACH);
		assertEquals(6.0, BlockAccess.CONTAINER_REACH);
		assertEquals("out of reach", BlockAccess.OUT_OF_REACH);
		assertEquals("target out of world", BlockAccess.OUT_OF_WORLD);
	}
}
