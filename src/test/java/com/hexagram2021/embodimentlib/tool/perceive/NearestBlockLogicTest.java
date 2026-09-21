package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NearestBlockLogic} 的单测（PLAN WP-6 #1）。
 * <p>
 * 覆盖：block id 合法性与 Identifier 规则一致、radius 边界、最近命中选取、
 * observation 文本规约（含 {@code "not found"}）。
 */
class NearestBlockLogicTest {
	@Test
	@DisplayName("block id：合法形态（带/不带命名空间）被接受")
	void acceptsValidBlockIds() {
		assertTrue(NearestBlockLogic.isValidBlockId("minecraft:iron_ore"));
		assertTrue(NearestBlockLogic.isValidBlockId("iron_ore"));
		assertTrue(NearestBlockLogic.isValidBlockId("tech:deep_ore.block-2"));
		assertTrue(NearestBlockLogic.isValidBlockId("minecraft:oak_door"));
	}

	@Test
	@DisplayName("block id：非法形态被拒绝（空白/大写/非法字符/多冒号/空路径）")
	void rejectsInvalidBlockIds() {
		assertFalse(NearestBlockLogic.isValidBlockId(null));
		assertFalse(NearestBlockLogic.isValidBlockId(""));
		assertFalse(NearestBlockLogic.isValidBlockId("   "));
		assertFalse(NearestBlockLogic.isValidBlockId("IronOre"));
		assertFalse(NearestBlockLogic.isValidBlockId("minecraft:iron ore"));
		assertFalse(NearestBlockLogic.isValidBlockId("a:b:c"));
		assertFalse(NearestBlockLogic.isValidBlockId("minecraft:"));
		assertFalse(NearestBlockLogic.isValidBlockId("..:stone")); // 命名空间 ".." 被 Identifier 拒绝
	}

	@Test
	@DisplayName("radius：边界 [1, 32] 内合法，越界拒绝")
	void radiusBounds() {
		assertTrue(NearestBlockLogic.isValidRadius(1));
		assertTrue(NearestBlockLogic.isValidRadius(8));
		assertTrue(NearestBlockLogic.isValidRadius(NearestBlockLogic.MAX_RADIUS));
		assertFalse(NearestBlockLogic.isValidRadius(0));
		assertFalse(NearestBlockLogic.isValidRadius(-1));
		assertFalse(NearestBlockLogic.isValidRadius(33));
		assertFalse(NearestBlockLogic.isValidRadius(1000));
	}

	@Test
	@DisplayName("findNearest：在多个命中里选距离最近的一个")
	void findNearestPicksClosest() {
		List<NearestBlockLogic.BlockHit> hits = List.of(
				NearestBlockLogic.hit("minecraft:iron_ore", 10, 64, 10),
				NearestBlockLogic.hit("minecraft:iron_ore", 5, 64, 5),
				NearestBlockLogic.hit("minecraft:iron_ore", 20, 64, 20));
		// 观察者位于 (0, 64, 0)：最近的是 (5, 64, 5)。
		NearestBlockLogic.BlockHit best = NearestBlockLogic.findNearest(
				"minecraft:iron_ore", 0, 64, 0, hits
		);
		assertEquals(5, best.x());
		assertEquals(5, best.z());
	}

	@Test
	@DisplayName("findNearest：只匹配指定 id，其它方块被忽略")
	void findNearestIgnoresOtherBlockTypes() {
		List<NearestBlockLogic.BlockHit> hits = List.of(
				NearestBlockLogic.hit("minecraft:coal_ore", 1, 64, 1),
				NearestBlockLogic.hit("minecraft:iron_ore", 10, 64, 10));
		NearestBlockLogic.BlockHit best = NearestBlockLogic.findNearest(
				"minecraft:iron_ore", 0, 64, 0, hits);
		assertEquals(10, best.x());
		assertEquals("minecraft:iron_ore", best.fullId());
	}

	@Test
	@DisplayName("describe：无匹配返回 not found")
	void describeNotFound() {
		List<NearestBlockLogic.BlockHit> hits = List.of(
				NearestBlockLogic.hit("minecraft:coal_ore", 1, 64, 1));
		assertEquals(NearestBlockLogic.NOT_FOUND,
				NearestBlockLogic.describe("minecraft:diamond_ore", 0, 64, 0, hits));
	}

	@Test
	@DisplayName("describe：命中格式化输出路径、坐标与保留一位小数的距离")
	void describeFormatsHit() {
		List<NearestBlockLogic.BlockHit> hits = List.of(
				NearestBlockLogic.hit("minecraft:iron_ore", 10, 64, 10));
		// 距离 = |(10.5, 64.5, 10.5) - (0, 64, 0)| ≈ sqrt(110.5+0.25+110.5) ≈ 14.87
		String observation = NearestBlockLogic.describe("minecraft:iron_ore", 0, 64, 0, hits);
		assertTrue(observation.startsWith("iron_ore at (10, 64, 10), distance "), observation);
		assertTrue(observation.endsWith("14.9"), observation);
	}

	@Test
	@DisplayName("pathOf：取资源标识符的路径部分，缺省时原样返回")
	void pathOfStripsNamespace() {
		assertEquals("iron_ore", NearestBlockLogic.pathOf("minecraft:iron_ore"));
		assertEquals("iron_ore", NearestBlockLogic.pathOf("iron_ore"));
		assertEquals("deep_ore", NearestBlockLogic.pathOf("tech:deep_ore"));
	}

	@Test
	@DisplayName("format：直接格式化命中与距离")
	void formatUsesPathAndOneDecimal() {
		NearestBlockLogic.BlockHit hit = NearestBlockLogic.hit("minecraft:stone", 3, 64, 3);
		assertEquals("stone at (3, 64, 3), distance 5.0", NearestBlockLogic.format(hit, 5.0));
	}
}