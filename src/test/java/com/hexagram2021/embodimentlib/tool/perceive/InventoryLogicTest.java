package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link InventoryLogic} 的单测（PLAN WP-6 #3）。
 * <p>
 * 覆盖：槽位行规约（无耐久/带耐久）、多行拼接、全空与不支持形态。
 */
class InventoryLogicTest {
	@Test
	@DisplayName("slotLine：无耐久物品输出 slot: countx id")
	void slotLinePlain() {
		assertEquals("slot 0: 64x minecraft:cobblestone",
				InventoryLogic.slotLine(0, "minecraft:cobblestone", 64));
	}

	@Test
	@DisplayName("slotLineDurability：可损物品附带剩余/最大耐久")
	void slotLineWithDurability() {
		// 250 最大耐久、已损 150 → 剩余 100。
		assertEquals("slot 1: 1x minecraft:iron_pickaxe (durability 100/250)",
				InventoryLogic.slotLineDurability(1, "minecraft:iron_pickaxe", 1, 150, 250));
	}

	@Test
	@DisplayName("join：空列表返回 inventory empty")
	void joinEmpty() {
		assertEquals(InventoryLogic.EMPTY, InventoryLogic.join(List.of()));
	}

	@Test
	@DisplayName("join：多行用分号空格拼接为一行（保持槽序）")
	void joinLines() {
		List<String> lines = List.of(
				"slot 0: 1x minecraft:iron_pickaxe (durability 100/250)",
				"slot 2: 5x minecraft:bread");
		assertEquals(
				"slot 0: 1x minecraft:iron_pickaxe (durability 100/250); slot 2: 5x minecraft:bread",
				InventoryLogic.join(lines));
	}

	@Test
	@DisplayName("契约常量：不支持/空 的文本形态")
	void contractConstants() {
		assertEquals("inventory not supported on this entity", InventoryLogic.NOT_SUPPORTED);
		assertEquals("inventory empty", InventoryLogic.EMPTY);
	}
}