package com.hexagram2021.embodimentlib.tool.container;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code container.inspect}（PLAN WP-7 #20，PRD §4.5 #20）。
 * <p>
 * 读取某个容器方块（箱子、木桶、潜影盒、熔炉、漏斗…）的逐槽内容。
 * <b>不开</b>玩家式 GUI——mob 没有 UI 状态（PRD 明确要求），因此直接读
 * {@code Container} 的数据，也不触发「谁打开了容器」的追踪。
 * 纯逻辑（前置检查裁决与文本）见 {@link InspectContainerLogic}。
 *
 * <h2>前置检查</h2>
 * 顺序：可读写坐标 → 是否容器 → 距离（6 格，PLAN 指定）→ 视线（从眼睛到方块中心
 * 射线，被别的方块挡住即失败）→ {@link Griefing}（PRD 与 PLAN 都把容器读写列入
 * 需要 griefing 检查的动作）。
 *
 * <h2>双箱只读半边</h2>
 * 大箱子的合并发生在玩家式访问路径上（{@code ChestBlock#getContainer}），
 * 本工具按坐标取方块实体，因此只会读到坐标所在的<b>那一半</b>。
 * 该简化已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class InspectContainerTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public InspectContainerTool() {
		super(
				"container.inspect",
				"Read the contents of a container block at pos (chest, barrel, shulker, furnace, "
						+ "hopper, ...) after range and line-of-sight checks. Does not open a "
						+ "player-style GUI. Reports per-slot contents, \"out of reach\", "
						+ "\"line of sight blocked\", or \"not a container\".",
				ToolResults.objectSchema(Map.of(
								"pos", ToolResults.prop("array", "Block coordinate [x, y, z] of the container")),
						List.of("pos")),
				true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		// Griefing 前置：被拒即收工（不读世界内容）。
		if (Griefing.denied(ctx)) {
			return Griefing.DENIED;
		}
		BlockCoordinates coordinates = BlockCoordinates.parse(input, BlockCoordinates.POS);
		if (coordinates.error() != null) {
			return coordinates.error();
		}

		Level level = ctx.level();
		BlockPos pos = new BlockPos(coordinates.x(), coordinates.y(), coordinates.z());
		if (!Blocks.isWritable(level, pos)) {
			return BlockAccess.OUT_OF_WORLD;
		}
		Container container = Containers.blockContainerAt(level, pos);
		boolean inReach = BlockAccess.within(ctx.entity().blockPosition().distSqr(pos), BlockAccess.CONTAINER_REACH);
		String rejection = InspectContainerLogic.rejectReason(
				container != null, inReach, hasLineOfSight(ctx, pos)
		);
		if (rejection != null) {
			return rejection;
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
		return Slots.join(lines, Slots.CONTAINER_EMPTY);
	}

	/**
	 * 从实体眼睛到目标方块中心做一次射线检测。
	 * <p>
	 * 命中目标方块本身（或什么都没命中，说明方块是空气/非实心形状）都算通视；
	 * 命中别的坐标即视为被遮挡。
	 */
	private static boolean hasLineOfSight(ToolContext ctx, BlockPos pos) {
		Level level = ctx.level();
		Vec3 from = ctx.entity().getEyePosition();
		Vec3 to = Vec3.atCenterOf(pos);
		BlockHitResult hit = level.clip(new ClipContext(
				from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ctx.entity()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}
}
