package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code action.use_item_on}（PLAN WP-7 #14，PRD §4.5 #14）。
 * <p>
 * 用手持物品右键某个方块的某一面（种作物、点火、开门、给牛喂食…）。
 * {@code hit_pos}/{@code face} 语义与 {@code action.place_block} 完全一致。
 * 纯逻辑（结果规约与全部文本）见 {@link BlockInteractionLogic}，
 * 参数解析见 {@link BlockAimLogic}。
 *
 * <h2>需要玩家身体</h2>
 * 原版交互 API 强制要求非空 {@code Player}；非玩家身体返回
 * {@code "interaction requires a player body"}。完整理由（以及为什么不伪造
 * {@code FakePlayer}）写在 {@link BlockInteractionLogic} 的类 Javadoc 里。
 *
 * <h2>走的不是游戏模式路径</h2>
 * 本工具直接调用 {@code BlockState#useItemOn}（它内部会 post NeoForge 的
 * {@code UseItemOnBlockEvent}），而<b>不</b>经过 {@code ServerPlayerGameMode#useItemOn}：
 * 后者额外要求 {@code ServerPlayer}、会写统计/成就、还会 post
 * {@code PlayerInteractEvent.RightClickBlock}。对一个 mob 驱动的交互而言，
 * 那些副作用既不真实也不必要。需要拦截本工具调用的 addon 应监听
 * {@code UseItemOnBlockEvent}。
 *
 * <h2>空手回退</h2>
 * 若方块对「用物品」的回答是 {@code TryEmptyHandInteraction}（例如拿种子点耕地的
 * 反面：拿错物品点门），原版会再试一次空手交互；本工具完整复刻这条回退链，
 * 否则模型会看到「no effect」而误以为方块不可交互。
 *
 * @author liudongyu
 */
public final class UseItemOnTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public UseItemOnTool() {
		super(
			"action.use_item_on",
			"Right-click/use the held item on a specific block face (plant a crop, light a fire, "
				+ "open a door). Same hit_pos/face semantics as action.place_block. Reports the "
				+ "interaction result, or \"no item in hand\" / \"out of reach\" / "
				+ "\"interaction requires a player body\".",
			ToolResults.objectSchema(Map.of(
				"hit_pos", ToolResults.prop("array", "Block coordinate [x, y, z] being clicked"),
				"face", ToolResults.enumProp("Which side of hit_pos to click (default \"up\")",
					BlockAimLogic.FACES),
				"hand", ToolResults.enumProp("Which hand holds the item (default \"main\")",
					List.of(UseItemLogic.DEFAULT_HAND, UseItemLogic.OFF_HAND))),
				List.of("hit_pos")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		if (!(ctx.entity() instanceof Player player)) {
			return BlockInteractionLogic.REQUIRES_PLAYER;
		}
		// Griefing 前置：被拒即收工（不读世界、不改世界）。
		if (Griefing.denied(ctx)) {
			return Griefing.DENIED;
		}
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(input);
		if (aim.error() != null) {
			return aim.error();
		}
		Direction face = Direction.byName(aim.face());
		if (face == null) {
			return BlockAimLogic.INVALID_FACE;
		}
		InteractionHand hand = UseItemLogic.OFF_HAND.equals(aim.hand())
			? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty()) {
			return UseItemLogic.NO_ITEM_IN_HAND;
		}

		Level level = ctx.level();
		BlockPos hitPos = new BlockPos(aim.x(), aim.y(), aim.z());
		if (!Blocks.isWritable(level, hitPos)) {
			return BlockAccess.OUT_OF_WORLD;
		}
		if (!BlockAccess.within(player.blockPosition().distSqr(hitPos), BlockAccess.INTERACT_REACH)) {
			return BlockAccess.OUT_OF_REACH;
		}

		BlockState state = level.getBlockState(hitPos);
		BlockHitResult hitResult = clickAt(hitPos, face);
		BlockInteractionLogic.Outcome itemOutcome =
			InteractionResults.of(state.useItemOn(stack, level, player, hand, hitResult));
		BlockInteractionLogic.Outcome emptyHandOutcome = BlockInteractionLogic.Outcome.NO_EFFECT;
		if (itemOutcome == BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND
				&& hand == InteractionHand.MAIN_HAND) {
			emptyHandOutcome = InteractionResults.of(state.useWithoutItem(level, player, hitResult));
		}
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
			+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return BlockInteractionLogic.describeItemUse(itemId, Blocks.idAt(level, hitPos),
			itemOutcome, emptyHandOutcome);
	}

	/** 构造点在该面中心的命中结果（模型只给方块坐标与面，给不出精确视线交点）。 */
	private static BlockHitResult clickAt(BlockPos pos, Direction face) {
		Vec3 location = Vec3.atCenterOf(pos)
			.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		return new BlockHitResult(location, face, pos, false);
	}
}
