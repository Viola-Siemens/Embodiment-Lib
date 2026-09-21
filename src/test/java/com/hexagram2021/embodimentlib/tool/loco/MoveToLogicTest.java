package com.hexagram2021.embodimentlib.tool.loco;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MoveToLogic} 的单测（PLAN WP-6 #7）。
 * <p>
 * 覆盖：非寻路实体、已到达（距离 ≤ reach）、路径阻塞、距离文本（1 位小数）。
 */
class MoveToLogicTest {
	@Test
	@DisplayName("非 Mob 实体返回 pathfinding not supported")
	void nonMob() {
		assertEquals(MoveToLogic.NOT_SUPPORTED, MoveToLogic.describe(false, 100.0, 2.0, true));
	}

	@Test
	@DisplayName("距离 ≤ reach 返回 arrived")
	void arrivedWhenWithinReach() {
		assertEquals(MoveToLogic.ARRIVED, MoveToLogic.describe(true, 2.0, 2.0, true));
		assertEquals(MoveToLogic.ARRIVED, MoveToLogic.describe(true, 0.5, 2.0, false));
		// 距离严格大于 reach 才算「未到达」。
		assertEquals("distance 2.1 remaining", MoveToLogic.describe(true, 2.1, 2.0, true));
	}

	@Test
	@DisplayName("路径未找到返回 path blocked（即使已经不远）")
	void pathBlocked() {
		assertEquals(MoveToLogic.PATH_BLOCKED, MoveToLogic.describe(true, 50.0, 2.0, false));
	}

	@Test
	@DisplayName("其余返回 distance D remaining（保留一位小数）")
	void distanceRemainingFormatted() {
		assertEquals("distance 3.5 remaining", MoveToLogic.describe(true, 3.5, 2.0, true));
		assertEquals("distance 123.4 remaining", MoveToLogic.describe(true, 123.42, 2.0, true));
	}
}