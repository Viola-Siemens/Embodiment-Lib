package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code action.place_block}（PLAN WP-7 #12，PRD §4.5 #12）。
 * <p>
 * 把手上的方块放到被点击面的相邻格：{@code hit_pos} 是被点击的方块，
 * {@code face} 是点击侧的朝向，落点为 {@code inside_pos = hit_pos.adjacent(face)}。
 * 破坏性动作（会改变世界、可能覆盖原方块），执行前必过 {@link Griefing}。
 * 纯逻辑（裁决顺序与文本）见 {@link PlaceBlockLogic}，参数解析见 {@link BlockAimLogic}。
 *
 * <h2>判定顺序</h2>
 * <ol>
 *   <li>{@link Griefing} 拒绝 → {@code "griefing denied"}（CLIENT 侧一概拒绝）；</li>
 *   <li>参数非法 → {@code "invalid input: ..."}；</li>
 *   <li>目标或落点在世界外/未加载 → {@code "target out of world"}；</li>
 *   <li>无方块可放 / 落点被占 / 超范围 → 对应文本；</li>
 *   <li>否则落块并消耗 1 个物品 → {@code "placed"}。</li>
 * </ol>
 * 注意「超范围」在 {@link Griefing} <b>之后</b>判定：Griefing 是对世界的授权问题，
 * 无论距离远近都应先问；距离只是「这次操作是否合理」。
 *
 * @author liudongyu
 */
public final class PlaceBlockTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public PlaceBlockTool() {
		super(
			"action.place_block",
			"Place the held block against a clicked face. hit_pos is the block being clicked, "
				+ "face is the side normal; the block appears at hit_pos.adjacent(face). Reports "
				+ "\"placed\", \"no block in hand\", \"target occupied\", \"out of reach\", "
				+ "\"target out of world\", or \"griefing denied\".",
			ToolResults.objectSchema(Map.of(
				"hit_pos", ToolResults.prop("array", "Block coordinate [x, y, z] being clicked"),
				"face", ToolResults.enumProp("Which side of hit_pos to place against (default \"up\")",
					BlockAimLogic.FACES),
				"hand", ToolResults.enumProp("Which hand holds the block (default \"main\")",
					List.of(UseItemLogic.DEFAULT_HAND, UseItemLogic.OFF_HAND))),
				List.of("hit_pos")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
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
			// 纯逻辑层已校验过朝向名；走到这里说明两张清单失配（内部不一致）。
			return BlockAimLogic.INVALID_FACE;
		}

		InteractionHand hand = UseItemLogic.OFF_HAND.equals(aim.hand())
			? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack stack = ctx.entity().getItemInHand(hand);
		boolean hasBlockInHand = stack.getItem() instanceof BlockItem;

		Level level = ctx.level();
		BlockPos hitPos = new BlockPos(aim.x(), aim.y(), aim.z());
		BlockPos insidePos = hitPos.relative(face);
		if (!Blocks.isWritable(level, hitPos) || !Blocks.isWritable(level, insidePos)) {
			return BlockAccess.OUT_OF_WORLD;
		}

		boolean inReach = BlockAccess.within(ctx.entity().blockPosition().distSqr(hitPos), BlockAccess.INTERACT_REACH);
		boolean targetReplaceable = level.getBlockState(insidePos).canBeReplaced();
		String decision = PlaceBlockLogic.decide(hasBlockInHand, targetReplaceable, inReach);
		if (!PlaceBlockLogic.PLACED.equals(decision)) {
			return decision;
		}

		Block block = ((BlockItem) stack.getItem()).getBlock();
		if (!level.setBlock(insidePos, block.defaultBlockState(), Block.UPDATE_ALL)) {
			return PlaceBlockLogic.PLACE_FAILED;
		}
		// 放置必须消耗物品，否则模型可以无限刷方块——这是经济性的底线。
		stack.shrink(1);
		return PlaceBlockLogic.PLACED;
	}
}
