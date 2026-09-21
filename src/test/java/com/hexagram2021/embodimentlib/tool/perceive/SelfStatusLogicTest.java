package com.hexagram2021.embodimentlib.tool.perceive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SelfStatusLogic} 的单测（PLAN WP-6 #6）。
 * <p>
 * 覆盖：快照行规约——坐标保留 1 位小数、血量保留 1 位小数、
 * 字段顺序与 PRD 示例一致。
 */
class SelfStatusLogicTest {
	@Test
	@DisplayName("format：规约一行状态快照（字段顺序与 PRD 示例一致）")
	void formatSnapshotLine() {
		String snapshot = SelfStatusLogic.format(100.0, 64.0, -200.0, "minecraft:overworld",
				18.0, "minecraft:iron_pickaxe", 6000L, 2);
		assertEquals(
				"pos=(100.0,64.0,-200.0) dim=minecraft:overworld health=18.0 "
						+ "held=minecraft:iron_pickaxe time=6000 threats=2",
				snapshot);
	}

	@Test
	@DisplayName("format：非整数值保留一位小数")
	void formatOneDecimal() {
		String snapshot = SelfStatusLogic.format(100.25, 64.0, -200.75, "minecraft:overworld",
				17.5, "empty", 0L, 0);
		assertEquals(
				"pos=(100.3,64.0,-200.8) dim=minecraft:overworld health=17.5 "
						+ "held=empty time=0 threats=0",
				snapshot);
	}
}