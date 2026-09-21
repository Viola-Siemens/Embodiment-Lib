package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.Griefing;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置工具 {@code action.attack_entity}（PLAN WP-6 #16，PRD §4.5 #16）。
 * <p>
 * 攻击<b>由 LLM 明确指定</b>的目标（{@code entity_id} = 目标 UUID）。
 * 工具<b>不自动选目标</b>——PRD 明确：「The LLM names the victim; the tool does not
 * pick a nearest target on its own」。
 * 纯逻辑（裁决）见 {@link AttackEntityLogic}。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>解析 UUID：非法 → {@code "target invalid"}（客户端拒绝由 Griefing 兜底）；</li>
 *   <li>查询目标：不存在/未存活 → {@code "target invalid"}；</li>
 *   <li>距离 &gt; 近战范围 → {@code "out of reach"}；</li>
 *   <li>{@link Griefing} 拒绝 → {@code "griefing denied"}；</li>
 *   <li>挥动手臂 + {@code hurtServer} 造成 1 点基础伤害 → {@code "attacked"}。</li>
 * </ol>
 *
 * @author liudongyu
 */
public final class AttackEntityTool extends EmbodiedToolBase {
	private static final String INVALID_ID = "invalid input: entity_id must be a uuid";

	/**
	 * 构造工具。
	 */
	public AttackEntityTool() {
		super(
				"action.attack_entity",
				"Swing at the specified target entity (by uuid). The tool never picks "
						+ "a target on its own. Reports \"attacked\", \"out of reach\", "
						+ "\"target invalid\", or \"griefing denied\".",
				ToolResults.objectSchema(
						Map.of(
								"entity_id", ToolResults.prop("string", "UUID of the target entity to attack")
						),
						List.of("entity_id")
				),
				false
		);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String entityId = ToolResults.requiredParam(input, "entity_id");
		if (entityId == null) {
			return INVALID_ID;
		}
		UUID uuid;
		try {
			uuid = UUID.fromString(entityId);
		} catch (IllegalArgumentException _) {
			return INVALID_ID;
		}
		Entity targetRaw = ctx.level().getEntity(uuid);
		boolean present = targetRaw instanceof LivingEntity;
		boolean alive = present && targetRaw.isAlive();
		double distance = present ? ctx.entity().distanceTo(targetRaw) : 0.0;
		String decision = AttackEntityLogic.describe(present, alive, distance);
		if (!AttackEntityLogic.ATTACKED.equals(decision)) {
			return decision;
		}

		// 攻击是破坏性动作，过 Griefing（需要 ServerLevel，客户端一律拒绝）。
		if (!(ctx.level() instanceof ServerLevel serverLevel)) {
			return Griefing.DENIED;
		}
		if (Griefing.denied(ctx)) {
			return Griefing.DENIED;
		}

		LivingEntity attacker = ctx.entity();
		LivingEntity target = (LivingEntity) targetRaw;
		attacker.swing(InteractionHand.MAIN_HAND);
		target.hurtServer(serverLevel, serverLevel.damageSources().mobAttack(attacker), 1.0f);
		return AttackEntityLogic.ATTACKED;
	}
}