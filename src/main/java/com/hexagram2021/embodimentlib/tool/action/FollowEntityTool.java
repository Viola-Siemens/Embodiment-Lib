package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置工具 {@code action.follow_entity}（PLAN WP-7 #18，PRD §4.5 #18）。
 * <p>
 * 让绑定实体<b>持续</b>跟随某个目标实体，直到 {@code action.stop_follow}。
 * 仅对 {@link Mob} 可用（需要 {@code GoalSelector} 与 {@code PathNavigation}）。
 * 纯逻辑（起始裁决）见 {@link FollowEntityLogic}，状态与生命周期见 {@link FollowService}。
 *
 * <h2>唯一有状态的工具</h2>
 * 本工具返回 {@code "following"} 后，实体会自行维持跟随——模型不必、也不应轮询它。
 * 状态清理有三条路径（正常停止、实体消失、服务器停止），详见 {@link FollowService}。
 * 重复调用本工具是安全的：它会替换旧目标，而不是叠加两个 goal。
 *
 * @author liudongyu
 */
public final class FollowEntityTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public FollowEntityTool() {
		super(
			"action.follow_entity",
			"Begin persistently following an entity (by uuid) at a given distance until "
				+ "action.stop_follow. Reports \"following\", \"lost target\", or "
				+ "\"pathfinding not supported\".",
			ToolResults.objectSchema(Map.of(
				"entity_id", ToolResults.prop("string", "UUID of the entity to follow"),
				"distance", ToolResults.prop("number", "Distance to keep in blocks (default 4)")),
				List.of("entity_id")),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String rawId = ToolResults.requiredParam(input, "entity_id");
		if (rawId == null) {
			return FollowEntityLogic.INVALID_ID;
		}
		UUID targetId;
		try {
			targetId = UUID.fromString(rawId);
		} catch (IllegalArgumentException _) {
			return FollowEntityLogic.INVALID_ID;
		}
		double distance = FollowEntityLogic.sanitizeDistance(
			ToolResults.doubleParam(input, "distance", FollowEntityLogic.DEFAULT_DISTANCE));

		Mob mob = ctx.asMob();
		Entity target = ctx.level().getEntity(targetId);
		boolean targetPresent = target instanceof LivingEntity living && living.isAlive();
		if (mob == null) {
			return FollowEntityLogic.describe(false, targetPresent);
		}
		if (!targetPresent) {
			return FollowEntityLogic.describe(true, false);
		}
		FollowService.start(mob, targetId, distance);
		return FollowEntityLogic.describe(true, true);
	}
}
