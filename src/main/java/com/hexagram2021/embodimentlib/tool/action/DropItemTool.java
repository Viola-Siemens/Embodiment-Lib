package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code action.drop_item}（PLAN WP-7 #17，PRD §4.5 #17）。
 * <p>
 * 把某个槽位里的物品（整叠或其中一部分）丢到地上。
 * 纯逻辑（掉落数量规约）见 {@link DropItemLogic}，槽位/数量解析与文本见
 * {@link Slots}。
 *
 * <h2>为什么也要过「权威世界」检查</h2>
 * 掉落会在世界里生成一个 {@code ItemEntity}——这是货真价实的世界写入，
 * 因此在客户端侧必须拒绝（PRD §4.1.1：CLIENT 不产生权威世界变更）。
 * 原版 {@code LivingEntity#drop} 在客户端只是空实现（不生成实体），
 * 若不拦，工具会对着一次「什么都没发生」回一句 {@code "dropped"}——那是假话。
 * 这里复用库内统一的拒绝文本 {@link Griefing#DENIED}（与 WP-6 的
 * {@code action.attack_entity} 同一处理），语义是「本端无权产生这次世界变更」。
 *
 * @author liudongyu
 */
public final class DropItemTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public DropItemTool() {
		super(
			"action.drop_item",
			"Drop a stack (or part of it) from an inventory slot onto the ground. "
				+ "Reports \"dropped\" or \"slot empty\"; count defaults to the whole stack and "
				+ "is clamped to what the slot actually holds.",
			ToolResults.objectSchema(Map.of(
				"slot", ToolResults.prop("integer", "Inventory slot index to drop from"),
				"count", ToolResults.prop("integer", "How many to drop (default: the whole stack)")),
				List.of("slot")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		if (!(ctx.level() instanceof ServerLevel)) {
			return Griefing.DENIED;
		}
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

		Integer requested = null;
		if (input.get("count") != null) {
			requested = Slots.parseCount(input.get("count"));
			if (requested == null) {
				return Slots.INVALID_COUNT;
			}
		}
		int count = DropItemLogic.resolveCount(requested, stack.getCount());
		ItemStack dropped = stack.copyWithCount(count);
		stack.shrink(count);
		// 显式写回并标记变更：部分 Container 实现（如只读包装）返回的是拷贝，
		// 只改本地引用不会落盘到容器里。
		container.setItem(slot, stack);
		container.setChanged();
		ctx.entity().drop(dropped, false, true);
		return DropItemLogic.DROPPED;
	}
}
