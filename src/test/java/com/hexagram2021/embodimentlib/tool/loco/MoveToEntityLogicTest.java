package com.hexagram2021.embodimentlib.tool.loco;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MoveToEntityLogic} 的单测（PLAN WP-7 #8）。
 * <p>
 * 覆盖：停止距离的规约、起始裁决的四种分支，以及与 {@link MoveToLogic} 的
 * 文本一致性（两个移动工具必须给出逐字相同的措辞）。
 */
class MoveToEntityLogicTest {
	@Test
	@DisplayName("sanitizeMinDistance：合法值原样保留，越界/非数回落默认值")
	void sanitizeMinDistanceRules() {
		assertEquals(3.0, MoveToEntityLogic.sanitizeMinDistance(3.0));
		assertEquals(1.0, MoveToEntityLogic.sanitizeMinDistance(1.0));
		assertEquals(MoveToEntityLogic.MAX_MIN_DISTANCE,
				MoveToEntityLogic.sanitizeMinDistance(MoveToEntityLogic.MAX_MIN_DISTANCE));
		assertEquals(MoveToEntityLogic.DEFAULT_MIN_DISTANCE, MoveToEntityLogic.sanitizeMinDistance(0.0));
		assertEquals(MoveToEntityLogic.DEFAULT_MIN_DISTANCE, MoveToEntityLogic.sanitizeMinDistance(-2.0));
		assertEquals(MoveToEntityLogic.DEFAULT_MIN_DISTANCE, MoveToEntityLogic.sanitizeMinDistance(100.0));
		assertEquals(MoveToEntityLogic.DEFAULT_MIN_DISTANCE, MoveToEntityLogic.sanitizeMinDistance(Double.NaN));
	}

	@Test
	@DisplayName("describe：不支持寻路时返回与 move_to 逐字相同的文本（能力问题优先于目标）")
	void notSupportedWinsOverTarget() {
		assertEquals(MoveToLogic.NOT_SUPPORTED,
				MoveToEntityLogic.describe(false, false, 0.0, 3.0, false));
		assertEquals(MoveToLogic.NOT_SUPPORTED,
				MoveToEntityLogic.describe(false, true, 10.0, 3.0, true));
	}

	@Test
	@DisplayName("describe：目标消失返回 lost target")
	void lostTarget() {
		assertEquals(MoveToEntityLogic.LOST_TARGET,
				MoveToEntityLogic.describe(true, false, 0.0, 3.0, false));
	}

	@Test
	@DisplayName("describe：距离不大于停止距离时返回 in range（即使路径未启动）")
	void inRange() {
		assertEquals(MoveToEntityLogic.IN_RANGE,
				MoveToEntityLogic.describe(true, true, 3.0, 3.0, false));
		assertEquals(MoveToEntityLogic.IN_RANGE,
				MoveToEntityLogic.describe(true, true, 0.5, 3.0, true));
	}

	@Test
	@DisplayName("describe：路径未找到返回 path blocked，与 move_to 文本一致")
	void pathBlockedUsesSharedText() {
		assertEquals(MoveToLogic.PATH_BLOCKED,
				MoveToEntityLogic.describe(true, true, 10.0, 3.0, false));
	}

	@Test
	@DisplayName("describe：其余情况给出与 move_to 逐字相同的剩余距离文本")
	void distanceRemainingUsesSharedText() {
		assertEquals(MoveToLogic.distanceRemaining(12.34),
				MoveToEntityLogic.describe(true, true, 12.34, 3.0, true));
		assertEquals("distance 12.3 remaining",
				MoveToEntityLogic.describe(true, true, 12.34, 3.0, true));
	}

	@Test
	@DisplayName("契约常量：默认停止距离 3、失败文本与 PRD 一致")
	void contractConstants() {
		assertEquals(3.0, MoveToEntityLogic.DEFAULT_MIN_DISTANCE);
		assertEquals("in range", MoveToEntityLogic.IN_RANGE);
		assertEquals("lost target", MoveToEntityLogic.LOST_TARGET);
		assertEquals("invalid input: entity_id must be a uuid", MoveToEntityLogic.INVALID_ID);
	}
}
