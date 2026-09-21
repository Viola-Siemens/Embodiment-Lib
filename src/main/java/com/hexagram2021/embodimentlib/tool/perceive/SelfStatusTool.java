package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 内置工具 {@code perceive.self_status}（PLAN WP-6 #6，PRD §4.5 #6）。
 * <p>
 * 报告绑定实体自身状态（PRD）：位置、维度、血量、手持物品、时间、附近威胁数。
 * 纯逻辑（快照行规约）见 {@link SelfStatusLogic}。
 *
 * <h2>威胁计数语义</h2>
 * 「附近威胁」0.1 简化为：实体周围 16 格内、且与实体同维度的
 * {@link Monster} 数量（攻击型生物）。不区分目标仇恨——那是 WP-9 端到端
 * 优化项，0.1 只给出「这块区域危不危险」的粗粒度信号。
 *
 * @author liudongyu
 */
public final class SelfStatusTool extends EmbodiedToolBase {
	/** 威胁扫描半径。 */
	private static final double THREAT_RADIUS = 16.0;

	/**
	 * 构造工具。
	 */
	public SelfStatusTool() {
		super(
			"perceive.self_status",
			"Report the executing entity's own state: position, dimension, health, held item, "
				+ "time of day, and nearby threat count.",
			ToolResults.objectSchema(Map.of(), List.of()),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Vec3 pos = ctx.entity().position();
		Level level = ctx.level();
		String dim = level.dimension().identifier().getNamespace() + ":" + level.dimension().identifier().getPath();
		double health = ctx.entity().getHealth();
		ItemStack held = ctx.entity().getMainHandItem();
		String heldId = held.isEmpty() ? "empty"
			: BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace() + ":"
				+ BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
		long time = level.getOverworldClockTime();
		int threats = countThreats(level, pos);
		return SelfStatusLogic.format(pos.x, pos.y, pos.z, dim, health, heldId, time, threats);
	}

	private static int countThreats(Level level, Vec3 center) {
		AABB box = new AABB(
			center.x - THREAT_RADIUS, center.y - THREAT_RADIUS, center.z - THREAT_RADIUS,
			center.x + THREAT_RADIUS, center.y + THREAT_RADIUS, center.z + THREAT_RADIUS);
		return level.getEntitiesOfClass(Monster.class, box).size();
	}
}