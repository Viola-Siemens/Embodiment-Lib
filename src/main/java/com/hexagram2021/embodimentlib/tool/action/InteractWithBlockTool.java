package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code action.interact_with_block}（PLAN WP-7 #15，PRD §4.5 #15）。
 * <p>
 * 不带特殊物品地右键一个方块：开门、拉杆、按按钮、给村民交易……
 * 纯逻辑（结果规约与全部文本）见 {@link BlockInteractionLogic}。
 *
 * <h2>与 {@code action.use_item_on} 的分工</h2>
 * 本工具<b>只看方块</b>（{@code pos} 一个参数），不传手别、不涉及手持物品；
 * 且不像 {@code use_item_on} 那样带「用物品失败就退回空手」的回退链——
 * 模型明确要求的是「空手交互」这一件事。想要「能成就成、不成就算了」的语义，
 * 应该用 {@code action.use_item_on}。
 *
 * <h2>命中面的合成</h2>
 * 原版 {@code useWithoutItem} 需要 {@code BlockHitResult}（含点击点与面），
 * 而模型只给方块坐标。本工具合成一个「方块中心、朝上」的命中结果：
 * 对门、活板门、拉杆、按钮这些关心「有没有人碰我」而非「从哪面碰」的方块，
 * 这个合成是充分的；对少数依赖点击面决定行为的方块（如某些 mod 方块），
 * 语义会退化为「从上方交互」。该简化已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class InteractWithBlockTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public InteractWithBlockTool() {
		super(
			"action.interact_with_block",
			"Right-click a block without using a special item (open a door, flip a lever, "
				+ "press a button). Reports \"interacted\", \"not interactable\", "
				+ "\"out of reach\", or \"interaction requires a player body\".",
			ToolResults.objectSchema(Map.of(
				"pos", ToolResults.prop("array", "Block coordinate [x, y, z] to interact with")),
				List.of("pos")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		if (!(ctx.entity() instanceof Player player)) {
			return BlockInteractionLogic.REQUIRES_PLAYER;
		}
		// Griefing 前置：拉杆/按钮/门都会改变世界，必须像原版生物一样受 mobGriefing 约束。
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
		if (!BlockAccess.within(player.blockPosition().distSqr(pos), BlockAccess.INTERACT_REACH)) {
			return BlockAccess.OUT_OF_REACH;
		}

		BlockState state = level.getBlockState(pos);
		BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return BlockInteractionLogic.describeBlockUse(
			InteractionResults.of(state.useWithoutItem(level, player, hitResult)));
	}
}
