package com.hexagram2021.embodimentlib.tool.container;

import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code container.transfer}（PLAN WP-7 #21，PRD §4.5 #21）。
 * <p>
 * 一次调用把一叠物品在「绑定实体库存」与「坐标上的容器」之间搬运。
 * <b>没有</b>「当前打开的容器」这种隐式状态：目标坐标每次都要显式传入
 * （PRD 明确要求，也是 mob 没有 UI 状态的必然结果）。
 * 纯逻辑（搬运量算术与失败文本）见 {@link TransferLogic}。
 *
 * <h2>方向</h2>
 * {@code direction} 缺省为 {@code "deposit"}（身上 → 容器）；
 * {@code "withdraw"} 为容器 → 身上。{@code from_slot} 永远是<b>来源</b>侧槽号，
 * {@code to_slot} 永远是<b>目标</b>侧槽号。需要这个参数的原因见
 * {@link TransferLogic} 的类 Javadoc（PLAN 原 schema 无法区分方向）。
 *
 * <h2>与其它容器检查的一致性</h2>
 * 前置检查与 {@code container.inspect} 同源：可读写坐标 → 是否容器 → 距离（6 格）
 * → {@link Griefing}。本工具<b>不做</b>视线检查：搬运是「伸手进去拿/放」，
 * 而模型已经通过 {@code container.inspect} 确认过内容物；再加一道视线检查只会
 * 让同一次操作在两个工具里得到不同结论。
 *
 * @author liudongyu
 */
public final class TransferContainerTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public TransferContainerTool() {
		super(
			"container.transfer",
			"Move a stack between the executing entity's inventory and the container at pos, "
				+ "in one call. direction is \"deposit\" (default, entity to container) or "
				+ "\"withdraw\"; from_slot is on the source side, to_slot on the destination side. "
				+ "Reports \"transferred\", \"not a container\", \"out of reach\", "
				+ "\"not enough items\", or \"target slot occupied\".",
			ToolResults.objectSchema(Map.of(
				"pos", ToolResults.prop("array", "Block coordinate [x, y, z] of the container"),
				"from_slot", ToolResults.prop("integer", "Source slot index"),
				"to_slot", ToolResults.prop("integer", "Destination slot index"),
				"count", ToolResults.prop("integer", "How many to move (default: the whole stack)"),
				"direction", ToolResults.enumProp("Transfer direction (default \"deposit\")",
					List.of(TransferLogic.DEPOSIT, TransferLogic.WITHDRAW))),
				List.of("pos", "from_slot", "to_slot")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		// Griefing 前置：搬运会改变容器内容，属破坏性动作（PRD §4.9）。
		if (Griefing.denied(ctx)) {
			return Griefing.DENIED;
		}
		BlockCoordinates coordinates = BlockCoordinates.parse(input, BlockCoordinates.POS);
		if (coordinates.error() != null) {
			return coordinates.error();
		}
		String direction = TransferLogic.parseDirection(input.get(TransferLogic.DIRECTION_KEY));
		if (direction == null) {
			return TransferLogic.INVALID_DIRECTION;
		}

		Container inventory = Containers.inventoryOf(ctx);
		if (inventory == null) {
			return Slots.NO_INVENTORY;
		}
		Level level = ctx.level();
		BlockPos pos = new BlockPos(coordinates.x(), coordinates.y(), coordinates.z());
		if (!Blocks.isWritable(level, pos)) {
			return BlockAccess.OUT_OF_WORLD;
		}
		Container blockContainer = Containers.blockContainerAt(level, pos);
		if (blockContainer == null) {
			return InspectContainerLogic.NOT_A_CONTAINER;
		}
		if (!BlockAccess.within(ctx.entity().blockPosition().distSqr(pos), BlockAccess.CONTAINER_REACH)) {
			return BlockAccess.OUT_OF_REACH;
		}

		boolean deposit = TransferLogic.DEPOSIT.equals(direction);
		Container source = deposit ? inventory : blockContainer;
		Container destination = deposit ? blockContainer : inventory;

		Integer fromSlot = Slots.parseIndex(input.get("from_slot"));
		String error = Slots.validateIndex(fromSlot, source.getContainerSize());
		if (error != null) {
			return error;
		}
		Integer toSlot = Slots.parseIndex(input.get("to_slot"));
		error = Slots.validateIndex(toSlot, destination.getContainerSize());
		if (error != null) {
			return error;
		}

		Integer requested = null;
		if (input.get("count") != null) {
			requested = Slots.parseCount(input.get("count"));
			if (requested == null) {
				return Slots.INVALID_COUNT;
			}
		}

		ItemStack sourceStack = source.getItem(fromSlot);
		ItemStack targetStack = destination.getItem(toSlot);
		TransferLogic.Decision decision = TransferLogic.plan(
			view(sourceStack), view(targetStack), requested,
			Math.min(sourceStack.getMaxStackSize(), destination.getMaxStackSize()));
		if (decision.error() != null) {
			return decision.error();
		}

		ItemStack moved = sourceStack.copyWithCount(decision.count());
		if (!destination.canPlaceItem(toSlot, moved)) {
			// 目标槽存在物品过滤规则（熔炉燃料槽、漏斗朝向等），放不进去。
			return TransferLogic.TARGET_SLOT_OCCUPIED;
		}
		sourceStack.shrink(decision.count());
		source.setItem(fromSlot, sourceStack);
		source.setChanged();
		if (targetStack.isEmpty()) {
			destination.setItem(toSlot, moved);
		} else {
			targetStack.grow(decision.count());
			destination.setItem(toSlot, targetStack);
		}
		destination.setChanged();
		return TransferLogic.TRANSFERRED;
	}

	/** 把槽内物品规约为纯逻辑视图。 */
	private static TransferLogic.SlotView view(ItemStack stack) {
		if (stack.isEmpty()) {
			return new TransferLogic.SlotView(null, 0);
		}
		return new TransferLogic.SlotView(
			BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
				+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
			stack.getCount());
	}
}
