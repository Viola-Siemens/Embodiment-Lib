package com.hexagram2021.embodimentlib.tool.perceive;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code perceive.inventory_slot}（PLAN WP-7 #4，PRD §4.5 #4）。
 * <p>
 * 详细检视绑定实体库存中的<b>单个</b>槽位：物品、数量、自定义名、附魔、耐久、是否可消耗。
 * 与 {@code perceive.inventory_contents}（只列非空槽的一行摘要）互补：模型先看摘要，
 * 对某个槽感兴趣时再用本工具看细节。
 * 纯逻辑（槽号校验、描述形状）见 {@link InventorySlotLogic}。
 *
 * <h2>只读</h2>
 * 标记 {@code readOnly = true}：本工具不修改世界也不改库存，受限执行模式下应自动放行。
 *
 * @author liudongyu
 */
public final class InventorySlotTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public InventorySlotTool() {
		super(
			"perceive.inventory_slot",
			"Inspect a single slot of the executing entity's inventory in detail: item, count, "
				+ "custom name, enchantments, durability. Reports \"no inventory\", \"slot empty\", "
				+ "or invalid-input text.",
			ToolResults.objectSchema(Map.of(
				"slot", ToolResults.prop("integer", "Inventory slot index to inspect")), List.of("slot")),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Container container = Containers.inventoryOf(ctx);
		if (container == null) {
			return Slots.NO_INVENTORY;
		}
		Integer slot = Slots.parseIndex(input.get("slot"));
		String error = Slots.validateIndex(slot, container.getContainerSize());
		if (error != null) {
			return error;
		}
		ItemStack stack = container.getItem(slot);
		if (stack.isEmpty()) {
			return Slots.SLOT_EMPTY;
		}
		return InventorySlotLogic.describe(slot, itemId(stack), stack.getCount(), traits(stack));
	}

	/** 取物品完整资源标识符（{@code "minecraft:diamond_sword"}）。 */
	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
			+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	/**
	 * 收集「模型看得见」的物品特征。
	 * <p>
	 * 附魔在内部是哈希表，迭代顺序随实现而变；此处按渲染文本排序，
	 * 保证同一物品每次输出<b>逐字相同</b>——observation 不稳定会干扰模型比对前后状态。
	 */
	private static List<String> traits(ItemStack stack) {
		List<String> traits = Lists.newArrayList();
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName != null) {
			traits.add(InventorySlotLogic.customName(customName.getString()));
		}
		List<String> enchantments = Lists.newArrayList();
		// 用 getIntValue() 而非 getValue()：fastutil 的原始类型 Entry 已弃用装箱版本。
		for (var entry : stack.getTagEnchantments().entrySet()) {
			String enchantmentId = entry.getKey().unwrapKey()
				.map(key -> key.identifier().getPath())
				.orElse("unknown");
			enchantments.add(enchantmentId + " " + entry.getIntValue());
		}
		if (!enchantments.isEmpty()) {
			enchantments.sort(String::compareTo);
			traits.add(InventorySlotLogic.enchantments(enchantments));
		}
		if (stack.isDamageableItem()) {
			traits.add(InventorySlotLogic.durability(stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()));
		}
		if (stack.get(DataComponents.CONSUMABLE) != null) {
			traits.add(InventorySlotLogic.consumable());
		}
		return traits;
	}
}
