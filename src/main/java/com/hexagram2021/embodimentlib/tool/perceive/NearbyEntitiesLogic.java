package com.hexagram2021.embodimentlib.tool.perceive;

import com.google.common.collect.Lists;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code perceive.nearby_entities} 的纯逻辑层（PLAN WP-7 #5，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（实体查询、注册表键、血量读取）在 {@link NearbyEntitiesTool} 中完成；
 * 本类负责<b>半径校验、排序与列表文本规约</b>。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>radius 越界：{@code "invalid radius"}；</li>
 *   <li>type_filter 不是合法资源标识符：{@code "invalid type filter"}；</li>
 *   <li>无匹配：{@code "no entities found"}；</li>
 *   <li>单行：{@code "zombie (uuid=..., dist=3.2, hp=20)"}——类型只写路径部分
 *       （与 {@link NearestBlockLogic} 同一约定：命名空间是查找细节，不必占用上下文）；</li>
 *   <li>多行用 {@code "; "} 连接。</li>
 * </ul>
 *
 * <h2>排序为什么必须稳定</h2>
 * 世界侧的实体列表顺序由引擎的存储结构决定（哈希/区块遍历序），同一场景两次调用
 * 可能给出不同顺序。模型会把 observation 当作事实来比对，顺序抖动会让它误以为
 * 世界发生了变化。故本类按「距离升序，距离相同则 UUID 升序」排序，
 * 使输出对同一世界状态逐字可复现。
 *
 * @author liudongyu
 */
public final class NearbyEntitiesLogic {
	/** 无匹配实体时的 observation。 */
	public static final String NONE = "no entities found";
	/** radius 越界时的 observation。 */
	public static final String INVALID_RADIUS = "invalid radius";
	/** type_filter 不是合法资源标识符时的 observation。 */
	public static final String INVALID_TYPE_FILTER = "invalid type filter";
	/** radius 合法下界（1 格，含）。 */
	public static final int MIN_RADIUS = 1;
	/** radius 合法上界（32 格，含）。过大半径会让游戏线程一次 tick 遍历海量实体。 */
	public static final int MAX_RADIUS = 32;
	/** 默认扫描半径（与 PRD #5 示例一致）。 */
	public static final int DEFAULT_RADIUS = 16;

	/** 一次查询命中的实体：类型完整标识符、UUID、距离、血量。 */
	public record EntityHit(String fullId, String uuid, double distance, float health) {
	}

	private NearbyEntitiesLogic() {
	}

	/**
	 * 校验半径是否在 {@code [MIN_RADIUS, MAX_RADIUS]} 内。
	 *
	 * @param radius 扫描半径
	 * @return 合法返回 true
	 */
	public static boolean isValidRadius(int radius) {
		return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
	}

	/**
	 * 按「距离升序，同距离按 UUID 升序」排序。
	 *
	 * @param hits 候选命中
	 * @return 排序后的新列表
	 */
	public static List<EntityHit> sorted(List<EntityHit> hits) {
		List<EntityHit> copy = Lists.newArrayList(hits);
		copy.sort(Comparator.comparingDouble(EntityHit::distance).thenComparing(EntityHit::uuid));
		return copy;
	}

	/**
	 * 规约单行文本：{@code "<path> (uuid=..., dist=3.2, hp=20)"}。
	 *
	 * @param hit 命中
	 * @return 单行文本
	 */
	public static String line(EntityHit hit) {
		return ResourceId.pathOf(hit.fullId()) + " (uuid=" + hit.uuid()
			+ ", dist=" + String.format(Locale.ROOT, "%.1f", hit.distance())
			+ ", hp=" + formatHealth(hit.health()) + ")";
	}

	/**
	 * 规约整个 observation：排序 + 逐行拼接，无命中时返回 {@link #NONE}。
	 *
	 * @param hits 候选命中
	 * @return observation 文本
	 */
	public static String describe(List<EntityHit> hits) {
		if (hits.isEmpty()) {
			return NONE;
		}
		return String.join("; ", sorted(hits).stream().map(NearbyEntitiesLogic::line).toList());
	}

	/**
	 * 规约血量文本。
	 * <p>
	 * 整数值不写小数（{@code 20} 而非 {@code 20.0}）：观察里的每个字符都要占用
	 * 模型上下文，而血量在绝大多数场合是整数。
	 *
	 * @param health 血量
	 * @return 文本形态
	 */
	public static String formatHealth(float health) {
		float rounded = Math.round(health);
		if (Math.abs(health - rounded) < 1.0e-3f) {
			return Integer.toString((int) rounded);
		}
		return String.format(Locale.ROOT, "%.1f", health);
	}
}
