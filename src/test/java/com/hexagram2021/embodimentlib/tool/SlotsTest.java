package com.hexagram2021.embodimentlib.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Slots} 的单测（PLAN WP-6 #3 与 WP-7 #4/#17/#20/#21 共用的槽位助手）。
 * <p>
 * 覆盖：槽位行规约（无耐久/带耐久）、多行拼接、两类空文本、
 * 槽号/数量解析（含字符串数字、负数、越界）与槽号校验。
 */
class SlotsTest {
	@Test
	@DisplayName("line：无耐久物品输出 slot: countx id")
	void linePlain() {
		assertEquals("slot 0: 64x minecraft:cobblestone",
				Slots.line(0, "minecraft:cobblestone", 64));
	}

	@Test
	@DisplayName("lineWithDurability：可损物品附带剩余/最大耐久")
	void lineWithDurability() {
		// 250 最大耐久、已损 150 → 剩余 100。
		assertEquals("slot 1: 1x minecraft:iron_pickaxe (durability 100/250)",
				Slots.lineWithDurability(1, "minecraft:iron_pickaxe", 1, 150, 250));
	}

	@Test
	@DisplayName("join：空列表返回 inventory empty")
	void joinEmpty() {
		assertEquals(Slots.INVENTORY_EMPTY, Slots.join(List.of()));
	}

	@Test
	@DisplayName("join：多行用分号空格拼接为一行（保持槽序）")
	void joinLines() {
		List<String> lines = List.of(
				"slot 0: 1x minecraft:iron_pickaxe (durability 100/250)",
				"slot 2: 5x minecraft:bread");
		assertEquals(
				"slot 0: 1x minecraft:iron_pickaxe (durability 100/250); slot 2: 5x minecraft:bread",
				Slots.join(lines));
	}

	@Test
	@DisplayName("join：空列表可指定为其它表述（方块容器用 container empty）")
	void joinWithCustomEmptyText() {
		assertEquals(Slots.CONTAINER_EMPTY, Slots.join(List.of(), Slots.CONTAINER_EMPTY));
		assertEquals("slot 0: 1x minecraft:stone",
				Slots.join(List.of("slot 0: 1x minecraft:stone"), Slots.CONTAINER_EMPTY));
	}

	@Test
	@DisplayName("parseIndex：接受整数与数字字符串，拒绝负数/小数/文本/缺失")
	void parseIndexRules() {
		assertEquals(0, Slots.parseIndex(0));
		assertEquals(7, Slots.parseIndex(7));
		assertEquals(7, Slots.parseIndex("7"));
		assertEquals(7, Slots.parseIndex(" 7 "));
		assertNull(Slots.parseIndex(-1));
		assertNull(Slots.parseIndex("-1"));
		assertNull(Slots.parseIndex(null));
		assertNull(Slots.parseIndex("first"));
	}

	@Test
	@DisplayName("parseCount：只接受正整数（0 与负数视为缺失，由调用方回落语义）")
	void parseCountRules() {
		assertEquals(1, Slots.parseCount(1));
		assertEquals(64, Slots.parseCount("64"));
		assertNull(Slots.parseCount(0));
		assertNull(Slots.parseCount(-3));
		assertNull(Slots.parseCount(null));
		assertNull(Slots.parseCount("many"));
	}

	@Test
	@DisplayName("validateIndex：缺失槽号与越界槽号给出不同的可行动文本")
	void validateIndexRules() {
		assertNull(Slots.validateIndex(0, 36));
		assertNull(Slots.validateIndex(35, 36));
		assertEquals(Slots.INVALID_SLOT, Slots.validateIndex(null, 36));
		assertEquals(Slots.SLOT_OUT_OF_RANGE, Slots.validateIndex(36, 36));
	}

	@Test
	@DisplayName("契约常量：各类文本形态与 PRD 约定一致")
	void contractConstants() {
		assertEquals("inventory not supported on this entity", Slots.NO_INVENTORY);
		assertEquals("inventory empty", Slots.INVENTORY_EMPTY);
		assertEquals("container empty", Slots.CONTAINER_EMPTY);
		assertEquals("slot empty", Slots.SLOT_EMPTY);
	}
}
