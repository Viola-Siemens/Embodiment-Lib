package com.hexagram2021.embodimentlib.tool.action;

import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.Griefing;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * 内置工具 {@code action.mine_block}（PLAN WP-6 #11，PRD §4.5 #11）。
 * <p>
 * 破坏指定坐标的方块（掉落物）。破坏性动作，执行前必过 {@link Griefing}
 * （PRD §4.9：破坏性工具统一走 mobGriefing 规则）。
 * 纯逻辑（裁决）见 {@link MineBlockLogic}。
 *
 * <h2>判定顺序</h2>
 * <ol>
 *   <li>{@link Griefing} 拒绝 → {@code "griefing denied"}（CLIENT 侧一概拒绝）；</li>
 *   <li>坐标不可用（世界外/未加载）→ {@code "target out of world"}；</li>
 *   <li>不可破坏 / 需要工具但空手 → 对应文本；</li>
 *   <li>否则 {@code level.destroyBlock(pos, true)} → {@code "mined"}。</li>
 * </ol>
 *
 * @author liudongyu
 */
public final class MineBlockTool extends EmbodiedToolBase {
	private static final String INVALID_POS = "invalid input: pos must be [x, y, z] of integers";
	private static final String OUT_OF_WORLD = "target out of world";
	private static final String MINED_FAILED = "mine failed";

	/**
	 * 构造工具。
	 */
	public MineBlockTool() {
		super(
			"action.mine_block",
			"Break the block at a position, dropping its items. Uses the held tool if applicable. "
				+ "Reports \"mined\", \"block unbreakable\", \"no tool\", \"target out of world\", "
				+ "or \"griefing denied\".",
			ToolResults.objectSchema(Map.of(
				"pos", ToolResults.prop("array", "Block coordinate [x, y, z] to mine")), List.of("pos")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		// Griefing 前置：被拒即收工（不读世界、不改世界）。
		if (Griefing.denied(ctx)) {
			return Griefing.DENIED;
		}
		Object rawPos = input.get("pos");
		Integer x = rawPos instanceof List<?> list && list.size() == 3 ? toInt(list.get(0)) : null;
		Integer y = rawPos instanceof List<?> list && list.size() == 3 ? toInt(list.get(1)) : null;
		Integer z = rawPos instanceof List<?> list && list.size() == 3 ? toInt(list.get(2)) : null;
		if (x == null || y == null || z == null) {
			return INVALID_POS;
		}

		Level level = ctx.level();
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.isLoaded(pos) || y < level.getMinY() || y > level.getMaxY()) {
			return OUT_OF_WORLD;
		}

		BlockState state = level.getBlockState(pos);
		boolean unbreakable = state.getDestroySpeed(level, pos) < 0.0f;
		boolean requiresTool = state.requiresCorrectToolForDrops();
		boolean handEmpty = ctx.entity().getMainHandItem().isEmpty();

		String decision = MineBlockLogic.describe(unbreakable, requiresTool, handEmpty);
		if (!MineBlockLogic.MINED.equals(decision)) {
			return decision;
		}
		boolean destroyed = level.destroyBlock(pos, true, ctx.entity(), Block.UPDATE_ALL);
		return destroyed ? MineBlockLogic.MINED : MINED_FAILED;
	}

	@Nullable
	private static Integer toInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}