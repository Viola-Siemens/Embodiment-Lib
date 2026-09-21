package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.Locale;

/**
 * {@code perceive.self_status} 的纯逻辑层（PLAN WP-6 #6，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（取位置/血量/手持/维度/时间/威胁数）在 {@link SelfStatusTool} 中完成；
 * 本类只做<b>快照行的规约</b>，把各字段拼成一行 observation（PRD 契约示例）：
 * <pre>
 * pos=(x,y,z) dim=minecraft:overworld health=18.0 held=minecraft:iron_pickaxe time=6000 threats=2
 * </pre>
 *
 * @author liudongyu
 */
public final class SelfStatusLogic {
	private SelfStatusLogic() {
	}

	/**
	 * 规约一行状态快照。
	 *
	 * @param x 实体 X
	 * @param y 实体 Y
	 * @param z 实体 Z
	 * @param dim 维度完整标识符（如 {@code "minecraft:overworld"}）
	 * @param health 当前血量（保留 1 位小数）
	 * @param held 手持物品 id；空手传 {@code "empty"}
	 * @param time 游戏时间（白天钟）
	 * @param threats 附近敌对实体数
	 * @return 一行 observation
	 */
	public static String format(double x, double y, double z, String dim, double health, String held,
			long time, int threats) {
		return "pos=(" + fmt(x) + "," + fmt(y) + "," + fmt(z) + ") dim=" + dim
			+ " health=" + String.format(Locale.ROOT, "%.1f", health)
			+ " held=" + held
			+ " time=" + time
			+ " threats=" + threats;
	}

	private static String fmt(double value) {
		// 坐标保留整数（实体坐标对齐），避免浮点噪声。
		return String.format(Locale.ROOT, "%.1f", value);
	}
}