package com.hexagram2021.embodimentlib.tool.loco;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置工具 {@code loco.look_at}（PLAN WP-6 #10，PRD §4.5 #10）。
 * <p>
 * 让绑定实体把头/身体转向一个位置或一个实体。目标规格解析见 {@link LookAtLogic}。
 *
 * <h2>目标实体</h2>
 * {@code entity_id} 是目标实体的 UUID（LLM 通常从 {@code perceive.nearby_entities}
 * 之类感知工具拿到）。实体已卸载/不存在时返回 {@code "target not found"}
 * （这是可预期失败，走文本而非异常）。
 *
 * @author liudongyu
 */
public final class LookAtTool extends EmbodiedToolBase {
	private static final String TARGET_NOT_FOUND = "target not found";

	/**
	 * 构造工具。
	 */
	public LookAtTool() {
		super(
			"loco.look_at",
			"Turn the entity's head/body toward a position ([x,y,z]) or an entity (uuid). "
				+ "Reports \"looking\".",
			ToolResults.objectSchema(Map.of(
				"pos", ToolResults.prop("array", "World coordinate [x, y, z] to look at"),
				"entity_id", ToolResults.prop("string", "UUID of the entity to look at")),
				List.of()),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		LookAtLogic.Target target = LookAtLogic.parse(input);
		if (target.error() != null) {
			return target.error();
		}
		if (target.pos() != null) {
			ctx.entity().lookAt(EntityAnchorArgument.Anchor.EYES, target.pos());
			return LookAtLogic.LOOKING;
		}
		Entity targetEntity = resolveEntity(ctx, target.entityId());
		if (targetEntity == null) {
			return TARGET_NOT_FOUND;
		}
		ctx.entity().lookAt(EntityAnchorArgument.Anchor.EYES, targetEntity.getEyePosition());
		return LookAtLogic.LOOKING;
	}

	@Nullable
	private static Entity resolveEntity(ToolContext ctx, @Nullable String entityId) {
		if(entityId == null) {
			return null;
		}
		try {
			UUID uuid = UUID.fromString(entityId);
			return ctx.level().getEntity(uuid);
		} catch (IllegalArgumentException _) {
			return null;
		}
	}
}