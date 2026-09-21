package com.hexagram2021.embodimentlib.tool.perceive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NearbyEntitiesLogic} 的单测（PLAN WP-7 #5）。
 * <p>
 * 覆盖：半径边界、稳定排序（距离→UUID）、单行与整表文本、血量文本规约。
 */
class NearbyEntitiesLogicTest {
	@Test
	@DisplayName("radius：边界 [1, 32] 内合法，越界拒绝")
	void radiusBounds() {
		assertTrue(NearbyEntitiesLogic.isValidRadius(1));
		assertTrue(NearbyEntitiesLogic.isValidRadius(16));
		assertTrue(NearbyEntitiesLogic.isValidRadius(NearbyEntitiesLogic.MAX_RADIUS));
		assertFalse(NearbyEntitiesLogic.isValidRadius(0));
		assertFalse(NearbyEntitiesLogic.isValidRadius(-1));
		assertFalse(NearbyEntitiesLogic.isValidRadius(33));
	}

	@Test
	@DisplayName("sorted：按距离升序；距离相同时按 UUID 升序（保证输出可复现）")
	void sortedIsStable() {
		List<NearbyEntitiesLogic.EntityHit> hits = List.of(
				new NearbyEntitiesLogic.EntityHit("minecraft:zombie", "bbbb", 5.0, 20.0f),
				new NearbyEntitiesLogic.EntityHit("minecraft:skeleton", "aaaa", 5.0, 20.0f),
				new NearbyEntitiesLogic.EntityHit("minecraft:pig", "cccc", 1.0, 10.0f));
		List<NearbyEntitiesLogic.EntityHit> sorted = NearbyEntitiesLogic.sorted(hits);
		assertEquals(List.of("cccc", "aaaa", "bbbb"), sorted.stream()
				.map(NearbyEntitiesLogic.EntityHit::uuid).toList());
	}

	@Test
	@DisplayName("line：类型只写路径部分，距离保留一位小数，整血量不写小数")
	void lineFormat() {
		assertEquals("zombie (uuid=abc, dist=3.2, hp=20)",
				NearbyEntitiesLogic.line(
						new NearbyEntitiesLogic.EntityHit("minecraft:zombie", "abc", 3.24, 20.0f)));
	}

	@Test
	@DisplayName("line：非整血量保留一位小数")
	void lineKeepsFractionalHealth() {
		assertEquals("cow (uuid=abc, dist=1.0, hp=7.5)",
				NearbyEntitiesLogic.line(
						new NearbyEntitiesLogic.EntityHit("minecraft:cow", "abc", 1.0, 7.5f)));
	}

	@Test
	@DisplayName("describe：无命中返回 no entities found")
	void describeEmpty() {
		assertEquals(NearbyEntitiesLogic.NONE, NearbyEntitiesLogic.describe(List.of()));
	}

	@Test
	@DisplayName("describe：多命中按排序后的顺序用分号拼接")
	void describeMultiple() {
		List<NearbyEntitiesLogic.EntityHit> hits = List.of(
				new NearbyEntitiesLogic.EntityHit("minecraft:zombie", "b", 9.0, 20.0f),
				new NearbyEntitiesLogic.EntityHit("minecraft:cow", "a", 2.0, 10.0f));
		assertEquals("cow (uuid=a, dist=2.0, hp=10); zombie (uuid=b, dist=9.0, hp=20)",
				NearbyEntitiesLogic.describe(hits));
	}

	@Test
	@DisplayName("formatHealth：整数不写小数，非整数保留一位")
	void formatHealthRules() {
		assertEquals("20", NearbyEntitiesLogic.formatHealth(20.0f));
		assertEquals("0", NearbyEntitiesLogic.formatHealth(0.0f));
		assertEquals("18.5", NearbyEntitiesLogic.formatHealth(18.5f));
		assertEquals("7.3", NearbyEntitiesLogic.formatHealth(7.34f));
	}

	@Test
	@DisplayName("契约常量：默认半径 16、文本与 PRD 一致")
	void contractConstants() {
		assertEquals(16, NearbyEntitiesLogic.DEFAULT_RADIUS);
		assertEquals("no entities found", NearbyEntitiesLogic.NONE);
		assertEquals("invalid radius", NearbyEntitiesLogic.INVALID_RADIUS);
		assertEquals("invalid type filter", NearbyEntitiesLogic.INVALID_TYPE_FILTER);
	}
}
