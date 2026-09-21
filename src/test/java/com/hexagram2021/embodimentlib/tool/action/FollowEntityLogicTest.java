package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.loco.MoveToLogic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FollowEntityLogic} 的单测（PLAN WP-7 #18）。
 * <p>
 * 覆盖：跟随距离的规约、起始裁决的三条分支，以及与移动类工具的文本一致性。
 */
class FollowEntityLogicTest {
	@Test
	@DisplayName("sanitizeDistance：合法值原样保留，越界/非数回落默认值")
	void sanitizeDistanceRules() {
		assertEquals(4.0, FollowEntityLogic.sanitizeDistance(4.0));
		assertEquals(FollowEntityLogic.MIN_DISTANCE, FollowEntityLogic.sanitizeDistance(1.0));
		assertEquals(FollowEntityLogic.MAX_DISTANCE, FollowEntityLogic.sanitizeDistance(32.0));
		assertEquals(FollowEntityLogic.DEFAULT_DISTANCE, FollowEntityLogic.sanitizeDistance(0.5));
		assertEquals(FollowEntityLogic.DEFAULT_DISTANCE, FollowEntityLogic.sanitizeDistance(-3.0));
		assertEquals(FollowEntityLogic.DEFAULT_DISTANCE, FollowEntityLogic.sanitizeDistance(100.0));
		assertEquals(FollowEntityLogic.DEFAULT_DISTANCE, FollowEntityLogic.sanitizeDistance(Double.NaN));
	}

	@Test
	@DisplayName("describe：不支持寻路时返回与移动类工具逐字相同的文本")
	void notSupported() {
		assertEquals(MoveToLogic.NOT_SUPPORTED, FollowEntityLogic.describe(false, true));
		assertEquals(FollowEntityLogic.NOT_SUPPORTED, FollowEntityLogic.describe(false, false));
	}

	@Test
	@DisplayName("describe：目标不存在时返回 lost target")
	void lostTarget() {
		assertEquals(FollowEntityLogic.LOST_TARGET, FollowEntityLogic.describe(true, false));
	}

	@Test
	@DisplayName("describe：支持寻路且目标存在时返回 following")
	void following() {
		assertEquals(FollowEntityLogic.FOLLOWING, FollowEntityLogic.describe(true, true));
	}

	@Test
	@DisplayName("契约常量：默认距离 4、失败文本与 PRD #18 一致")
	void contractConstants() {
		assertEquals(4.0, FollowEntityLogic.DEFAULT_DISTANCE);
		assertEquals("following", FollowEntityLogic.FOLLOWING);
		assertEquals("lost target", FollowEntityLogic.LOST_TARGET);
		assertEquals("invalid input: entity_id must be a uuid", FollowEntityLogic.INVALID_ID);
	}
}
