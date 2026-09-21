package com.hexagram2021.embodimentlib.tool.perceive;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code perceive.nearby_entities}（PLAN WP-7 #5，PRD §4.5 #5）。
 * <p>
 * 列出绑定实体周围 {@code radius} 格内的活体：类型、UUID、距离、血量，可选按类型过滤。
 * 纯逻辑（半径校验、排序、文本规约）见 {@link NearbyEntitiesLogic}。
 *
 * <h2>半径是「球形」而非「立方体」</h2>
 * 原版 {@code getEntitiesOfClass} 只接受 {@code AABB}（立方体），直接用它会把角落上
 * 距离 {@code radius * sqrt(3)} 的实体也算进来——模型会看到「dist=27.7」却被告知
 * radius=16。故先按放大后的 AABB 粗筛，再用真实距离精筛，保证输出与半径语义自洽。
 *
 * <h2>不包含自己</h2>
 * 模型问「附近有什么」时不需要被告知它自己；把自己列进去只会浪费一行上下文，
 * 还可能诱使它去攻击/跟随自己。
 *
 * @author liudongyu
 */
public final class NearbyEntitiesTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public NearbyEntitiesTool() {
		super(
			"perceive.nearby_entities",
			"List living entities within a radius: type, uuid, distance, health. Optional "
				+ "type_filter is a resource id such as \"minecraft:zombie\". Sorted by distance.",
			ToolResults.objectSchema(Map.of(
				"radius", ToolResults.rangedProp("integer", "Search radius in blocks (default 16)",
					NearbyEntitiesLogic.MIN_RADIUS, NearbyEntitiesLogic.MAX_RADIUS),
				"type_filter", ToolResults.prop("string",
					"Optional entity type to filter by, e.g. \"minecraft:zombie\"")),
				List.of()),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		int radius = ToolResults.intParam(input, "radius", NearbyEntitiesLogic.DEFAULT_RADIUS);
		if (!NearbyEntitiesLogic.isValidRadius(radius)) {
			return NearbyEntitiesLogic.INVALID_RADIUS;
		}
		// type_filter 是可选的；给了但不合法要明确报错，而不是静默不过滤——
		// 静默不过滤会让模型以为「周围只有僵尸」，实际是过滤条件被吞了。
		String rawFilter = ToolResults.requiredParam(input, "type_filter");
		String wantedType = null;
		if (rawFilter != null) {
			wantedType = ResourceId.canonicalize(rawFilter);
			if (wantedType == null) {
				return NearbyEntitiesLogic.INVALID_TYPE_FILTER;
			}
		}

		LivingEntity self = ctx.entity();
		Level level = ctx.level();
		List<LivingEntity> candidates = level.getEntitiesOfClass(
			LivingEntity.class,
			self.getBoundingBox().inflate(radius),
			candidate -> candidate != self && candidate.isAlive() && self.distanceTo(candidate) <= radius);

		List<NearbyEntitiesLogic.EntityHit> hits = Lists.newArrayList();
		for (LivingEntity candidate : candidates) {
			String fullId = typeId(candidate);
			if (wantedType != null && !wantedType.equals(fullId)) {
				continue;
			}
			hits.add(new NearbyEntitiesLogic.EntityHit(
				fullId, candidate.getUUID().toString(), self.distanceTo(candidate), candidate.getHealth()));
		}
		return NearbyEntitiesLogic.describe(hits);
	}

	/** 取实体类型的完整资源标识符（{@code "minecraft:zombie"}）。 */
	private static String typeId(LivingEntity entity) {
		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return key.getNamespace() + ":" + key.getPath();
	}
}
