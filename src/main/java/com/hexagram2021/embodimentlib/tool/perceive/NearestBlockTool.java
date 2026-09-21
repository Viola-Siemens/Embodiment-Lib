package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 内置工具 {@code perceive.nearest_block}（PLAN WP-6 #1，PRD §4.5 #1）。
 * <p>
 * 在绑定实体周围 {radius} 格内扫描指定类型方块，返回最近的一个。
 * 纯逻辑（id 校验、距离计算、文本规约）见 {@link NearestBlockLogic}，本类只做
 * <b>世界侧适配</b>：方块 id → 注册表查询、范围扫描、状态识别。
 *
 * <h2>线程</h2>
 * {@code run} 由基类保证在游戏线程执行，可直接读 {@link Level#getBlockState}。
 *
 * <h2>性能</h2>
 * 扫描范围受 {@link NearestBlockLogic#MAX_RADIUS} 封顶（32 格），
 * 单次最坏 {@code (2*32+1)^3 = 274625} 次读块，全部在游戏线程完成——
 * 对一次工具调用而言可接受，但 LLM 不应频繁请求大半径扫描。
 *
 * @author liudongyu
 */
public final class NearestBlockTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public NearestBlockTool() {
		super(
			"perceive.nearest_block",
			"Find the nearest block of a given type within a radius around the entity. "
				+ "Input block is a resource location such as \"minecraft:iron_ore\"; the observation "
				+ "reports the position and distance of the nearest match, or \"not found\".",
			ToolResults.objectSchema(Map.of(
				"block", ToolResults.prop("string", "Block resource location to search for, e.g. \"minecraft:iron_ore\""),
				"radius", ToolResults.rangedProp("integer", "Search radius in blocks", NearestBlockLogic.MIN_RADIUS,
					NearestBlockLogic.MAX_RADIUS)),
				List.of("block")),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String blockId = ToolResults.stringParam(input, "block", "");
		if (!NearestBlockLogic.isValidBlockId(blockId)) {
			return NearestBlockLogic.INVALID_BLOCK_ID;
		}
		int radius = ToolResults.intParam(input, "radius", NearestBlockLogic.DEFAULT_RADIUS);
		if (!NearestBlockLogic.isValidRadius(radius)) {
			return NearestBlockLogic.INVALID_RADIUS;
		}

		// 不带命名空间时 Identifier.tryParse 按 minecraft 缺省，与纯逻辑层规则一致。
		Identifier wanted = Identifier.tryParse(blockId);
		if (wanted == null) {
			return NearestBlockLogic.INVALID_BLOCK_ID;
		}
		String wantedFull = wanted.getNamespace() + ":" + wanted.getPath();
		Block wantedBlock = BuiltInRegistries.BLOCK.getOptional(wanted).orElse(null);
		if (wantedBlock == null) {
			// 注册表里没有这个方块（mod 未加载/拼错）：按「无匹配」收场。
			return NearestBlockLogic.NOT_FOUND;
		}

		Level level = ctx.level();
		BlockPos center = ctx.entity().blockPosition();
		List<NearestBlockLogic.BlockHit> hits = new ArrayList<>();
		BlockPos.betweenClosed(
			center.getX() - radius, center.getY() - radius, center.getZ() - radius,
			center.getX() + radius, center.getY() + radius, center.getZ() + radius)
			.forEach(pos -> {
				if (level.getBlockState(pos).getBlock() == wantedBlock) {
					// 注册表键的路径部分（如 iron_ore），与纯逻辑层的 fullId 约定一致。
					hits.add(new NearestBlockLogic.BlockHit(
						wantedFull, pos.getX(), pos.getY(), pos.getZ()));
				}
			});

		double ex = ctx.entity().getX();
		double ey = ctx.entity().getEyeY();
		double ez = ctx.entity().getZ();
		return NearestBlockLogic.describe(wantedFull, ex, ey, ez, hits);
	}
}