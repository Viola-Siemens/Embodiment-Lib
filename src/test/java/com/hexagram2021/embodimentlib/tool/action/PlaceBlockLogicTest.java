package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.BlockAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PlaceBlockLogic} 的单测（PLAN WP-7 #12）。
 * <p>
 * 覆盖：四种裁决结果、以及 PLAN 指定的判定优先级（手上没方块 &gt; 落点被占 &gt; 超范围）。
 */
class PlaceBlockLogicTest {
	@Test
	@DisplayName("decide：三条件全部满足时返回 placed")
	void placed() {
		assertEquals(PlaceBlockLogic.PLACED, PlaceBlockLogic.decide(true, true, true));
	}

	@Test
	@DisplayName("decide：手上没有 BlockItem 时返回 no block in hand")
	void noBlockInHand() {
		assertEquals(PlaceBlockLogic.NO_BLOCK_IN_HAND, PlaceBlockLogic.decide(false, true, true));
	}

	@Test
	@DisplayName("decide：落点不可替换时返回 target occupied")
	void targetOccupied() {
		assertEquals(PlaceBlockLogic.TARGET_OCCUPIED, PlaceBlockLogic.decide(true, false, true));
	}

	@Test
	@DisplayName("decide：超出手臂可及范围时返回 out of reach")
	void outOfReach() {
		assertEquals(BlockAccess.OUT_OF_REACH, PlaceBlockLogic.decide(true, true, false));
	}

	@Test
	@DisplayName("decide：优先级为「没方块 &gt; 落点被占 &gt; 超范围」")
	void decisionOrderFollowsPlan() {
		assertEquals(PlaceBlockLogic.NO_BLOCK_IN_HAND, PlaceBlockLogic.decide(false, false, false));
		assertEquals(PlaceBlockLogic.TARGET_OCCUPIED, PlaceBlockLogic.decide(true, false, false));
	}

	@Test
	@DisplayName("契约常量：成功/失败文本与 PRD #12 一致")
	void contractConstants() {
		assertEquals("placed", PlaceBlockLogic.PLACED);
		assertEquals("no block in hand", PlaceBlockLogic.NO_BLOCK_IN_HAND);
		assertEquals("target occupied", PlaceBlockLogic.TARGET_OCCUPIED);
		assertEquals("place failed", PlaceBlockLogic.PLACE_FAILED);
	}
}
