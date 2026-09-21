package com.hexagram2021.embodimentlib.tool.loco;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置工具 {@code loco.move_to_entity}（PLAN WP-7 #8，PRD §4.5 #8）。
 * <p>
 * 用原版寻路走向一个<b>会移动的</b>目标实体（UUID 指定），可指定停止距离。
 * 仅对 {@link Mob} 可用；其它实体返回 {@code "pathfinding not supported"}。
 * 纯逻辑（裁决与文本）见 {@link MoveToEntityLogic}。
 *
 * <h2>一次调用 = 启动寻路 + 报告快照</h2>
 * 与 {@code loco.move_to} 相同：不阻塞等待到达。目标是移动的，因此每次调用都会
 * 用<b>当前</b>距离重新裁决——模型可以靠反复调用观察距离是否在收敛。
 *
 * <h2>与 {@code action.follow_entity} 的分工</h2>
 * 本工具是「朝它走一段」的一次性指令；{@code action.follow_entity} 是「持续跟着它」
 * 的状态式指令（由实体 tick 驱动，直到 {@code action.stop_follow}）。模型需要
 * 「跟着我」这种长期行为时应选后者，而不是反复调用本工具。
 *
 * @author liudongyu
 */
public final class MoveToEntityTool extends EmbodiedToolBase {
	private static final double SPEED = 1.0;

	/**
	 * 构造工具。
	 */
	public MoveToEntityTool() {
		super(
			"loco.move_to_entity",
			"Path toward a moving entity (by uuid). The entity starts walking; call again to "
				+ "check convergence. Reports \"in range\", \"lost target\", \"path blocked\", "
				+ "\"distance D remaining\", or \"pathfinding not supported\".",
			ToolResults.objectSchema(Map.of(
				"entity_id", ToolResults.prop("string", "UUID of the entity to walk toward"),
				"min_distance", ToolResults.prop("number", "Stop distance in blocks (default 3)")),
				List.of("entity_id")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String rawId = ToolResults.requiredParam(input, "entity_id");
		if (rawId == null) {
			return MoveToEntityLogic.INVALID_ID;
		}
		UUID targetId;
		try {
			targetId = UUID.fromString(rawId);
		} catch (IllegalArgumentException _) {
			return MoveToEntityLogic.INVALID_ID;
		}
		double minDistance = MoveToEntityLogic.sanitizeMinDistance(
			ToolResults.doubleParam(input, "min_distance", MoveToEntityLogic.DEFAULT_MIN_DISTANCE));

		Mob mob = ctx.asMob();
		Entity target = ctx.level().getEntity(targetId);
		boolean targetPresent = target != null && target.isAlive();
		if (mob == null) {
			return MoveToEntityLogic.describe(false, targetPresent, 0.0, minDistance, false);
		}
		if (!targetPresent) {
			return MoveToEntityLogic.describe(true, false, 0.0, minDistance, false);
		}
		double distance = mob.distanceTo(target);
		if (distance <= minDistance) {
			return MoveToEntityLogic.describe(true, true, distance, minDistance, false);
		}
		boolean pathFound = mob.getNavigation().moveTo(target, SPEED);
		return MoveToEntityLogic.describe(true, true, distance, minDistance, pathFound);
	}
}
