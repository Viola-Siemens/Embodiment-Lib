package com.hexagram2021.embodimentlib.tool.perceive;

import com.hexagram2021.embodimentlib.tool.Slots;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link InventorySlotLogic} 的单测（PLAN WP-7 #4）。
 * <p>
 * 覆盖：无特征槽的描述、多特征拼接顺序、各类特征文本形态。
 * 槽号解析与校验由 {@link com.hexagram2021.embodimentlib.tool.Slots} 负责，已在
 * {@code SlotsTest} 覆盖。
 */
class InventorySlotLogicTest {
	@Test
	@DisplayName("describe：无附加特征时只输出槽号、数量与物品 id")
	void describePlainStack() {
		assertEquals("slot 0: 64x minecraft:cobblestone",
				InventorySlotLogic.describe(0, "minecraft:cobblestone", 64, List.of()));
	}

	@Test
	@DisplayName("describe：多特征按调用方给定顺序用分号拼接")
	void describeWithTraits() {
		assertEquals(
				"slot 3: 1x minecraft:diamond_sword (custom name \"Excalibur\"; "
						+ "enchantments sharpness 5, unbreaking 3; durability 250/250; consumable)",
				InventorySlotLogic.describe(3, "minecraft:diamond_sword", 1, List.of(
						InventorySlotLogic.customName("Excalibur"),
						InventorySlotLogic.enchantments(List.of("sharpness 5", "unbreaking 3")),
						InventorySlotLogic.durability(250, 250),
						InventorySlotLogic.consumable())));
	}

	@Test
	@DisplayName("customName：用双引号包住名字，避免与后续特征混淆")
	void customNameText() {
		assertEquals("custom name \"Excalibur\"", InventorySlotLogic.customName("Excalibur"));
	}

	@Test
	@DisplayName("enchantments：多条附魔用逗号加空格连接")
	void enchantmentsText() {
		assertEquals("enchantments sharpness 5", InventorySlotLogic.enchantments(List.of("sharpness 5")));
		assertEquals("enchantments mending 1, unbreaking 3",
				InventorySlotLogic.enchantments(List.of("mending 1", "unbreaking 3")));
	}

	@Test
	@DisplayName("durability：输出剩余/最大而不是已损值")
	void durabilityText() {
		assertEquals("durability 100/250", InventorySlotLogic.durability(100, 250));
	}

	@Test
	@DisplayName("consumable：固定文本")
	void consumableText() {
		assertEquals("consumable", InventorySlotLogic.consumable());
	}

	@Test
	@DisplayName("契约常量：槽空文本与 Slots 保持单一来源")
	void emptyTextComesFromSlots() {
		assertEquals("slot empty", Slots.SLOT_EMPTY);
	}
}
