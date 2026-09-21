package com.hexagram2021.embodimentlib.tool.perceive;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code perceive.inventory_contents}（PLAN WP-6 #3，PRD §4.5 #3）。
 * <p>
 * 列出绑定实体的库存：槽位、物品、数量、耐久。实体没有库存 → {@link Slots#NO_INVENTORY}。
 * 纯逻辑（槽行规约、文本拼接）见 {@link Slots}；容器解析见
 * {@link Containers#inventoryOf(ToolContext)}。
 *
 * <h2>只列非空槽</h2>
 * 逐槽输出会包含大量 {@code "empty"} 噪声（玩家 36 槽、马 15 槽…），
 * 对 LLM 上下文是浪费。因此本工具只输出<b>非空槽</b>，且每行带真实槽位索引——
 * 模型据此知道「slot 5 有 3x 石头」，与空槽语义不冲突。
 *
 * @author liudongyu
 */
public final class InventoryContentsTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public InventoryContentsTool() {
		super(
			"perceive.inventory_contents",
			"List the executing entity's inventory: slot index, item type, count, durability. "
				+ "Reports \"inventory not supported on this entity\" if the body has no inventory.",
			ToolResults.objectSchema(Map.of(), List.of()),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Container container = Containers.inventoryOf(ctx);
		if (container == null) {
			return Slots.NO_INVENTORY;
		}
		List<String> lines = Lists.newArrayList();
		int size = container.getContainerSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
				+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (stack.isDamageableItem()) {
				lines.add(Slots.lineWithDurability(slot, itemId, stack.getCount(),
					stack.getDamageValue(), stack.getMaxDamage()));
			} else {
				lines.add(Slots.line(slot, itemId, stack.getCount()));
			}
		}
		return Slots.join(lines);
	}
}