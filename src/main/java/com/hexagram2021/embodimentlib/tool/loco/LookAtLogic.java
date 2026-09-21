package com.hexagram2021.embodimentlib.tool.loco;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * {@code loco.look_at} 的纯逻辑层（PLAN WP-6 #10，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（转动头/身体朝向）在 {@link LookAtTool} 中完成；本类负责
 * <b>目标规格的解析</b>：{@code pos} 与 {@code entity_id} 二选一，冲突/缺失/非法给出
 * {@code "invalid input: ..."} 文本。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@code {"pos":[x,y,z]}} → 看向该点；</li>
 *   <li>{@code {"entity_id":"<uuid>"}} → 看向该实体；</li>
 *   <li>两参都给：以 {@code pos} 优先（实体的 UUID 解析成本更高，位置更直接）；</li>
 *   <li>都缺 / 类型错：{@code "invalid input: need pos ([x,y,z]) or entity_id (uuid)"}。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class LookAtLogic {
	/** 看向成功的 observation（PRD #10）。 */
	public static final String LOOKING = "looking";

	/** 解析结果：要么是目标点，要么是目标实体 id，要么是错误文本。 */
	public record Target(@Nullable Vec3 pos, @Nullable String entityId, @Nullable String error) {
		public static Target point(double x, double y, double z) {
			return new Target(new Vec3(x, y, z), null, null);
		}

		public static Target entity(String entityId) {
			return new Target(null, entityId, null);
		}

		public static Target invalid(String error) {
			return new Target(null, null, error);
		}
	}

	private LookAtLogic() {
	}

	/**
	 * 解析模型参数为目标规格。
	 *
	 * @param input 模型参数
	 * @return 解析结果；{@code error() != null} 时该文本即为 observation
	 */
	public static Target parse(Map<String, Object> input) {
		Object rawPos = input.get("pos");
		Object rawEntity = input.get("entity_id");
		if (rawPos == null && rawEntity == null) {
			return Target.invalid("invalid input: need pos ([x,y,z]) or entity_id (uuid)");
		}
		if (rawPos != null) {
			if (!(rawPos instanceof List<?> list) || list.size() != 3) {
				return Target.invalid("invalid input: pos must be [x,y,z]");
			}
			Double x = toDouble(list.get(0));
			Double y = toDouble(list.get(1));
			Double z = toDouble(list.get(2));
			if (x == null || y == null || z == null) {
				return Target.invalid("invalid input: pos must be [x,y,z] of numbers");
			}
			return Target.point(x, y, z);
		}
		if (!(rawEntity instanceof String s) || s.isBlank()) {
			return Target.invalid("invalid input: entity_id must be a non-blank uuid");
		}
		return Target.entity(s);
	}

	@Nullable
	private static Double toDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}