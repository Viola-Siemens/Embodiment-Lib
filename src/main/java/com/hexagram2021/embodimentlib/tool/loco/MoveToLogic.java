package com.hexagram2021.embodimentlib.tool.loco;

import java.util.Locale;

/**
 * {@code loco.move_to} 的纯逻辑层（PLAN WP-6 #7，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（寻路、距离计算）在 {@link MoveToTool} 中完成；本类负责
 * <b>结果裁决与文本规约</b>——把「是否支持寻路 / 路径是否找到 / 距离 / 到达阈值」
 * 缩成一个 observation 文本。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>非 {@code Mob}（无寻路）：{@value #NOT_SUPPORTED}；</li>
 *   <li>距离 ≤ reach：{@value #ARRIVED}；</li>
 *   <li>路径未找到：{@value #PATH_BLOCKED}；</li>
 *   <li>其余：{@code "distance D remaining"}（D 保留 1 位小数）。</li>
 * </ul>
 * 超时文本（PLAN 里的 {@code "(timeout)"} 形态）由 WP-3 的
 * {@code ThreadBridge} 超时兜底产生（{@code "tool timeout after Xms"}），
 * 本工具只报告当前距离。
 *
 * @author liudongyu
 */
public final class MoveToLogic {
	/** 非 {@code Mob} 实体的 observation。 */
	public static final String NOT_SUPPORTED = "pathfinding not supported";
	/** 已到达（距离 ≤ reach）。 */
	public static final String ARRIVED = "arrived";
	/** 目标位置不可达（寻路失败）。 */
	public static final String PATH_BLOCKED = "path blocked";

	private MoveToLogic() {
	}

	/**
	 * 裁决移动结果。
	 *
	 * @param mob 实体是否支持寻路（{@code Mob}）
	 * @param distance 实体到目标的当前距离
	 * @param reach 到达阈值
	 * @param pathFound 本次寻路是否成功启动
	 * @return observation 文本
	 */
	public static String describe(boolean mob, double distance, double reach, boolean pathFound) {
		if (!mob) {
			return NOT_SUPPORTED;
		}
		if (distance <= reach) {
			return ARRIVED;
		}
		if (!pathFound) {
			return PATH_BLOCKED;
		}
		return "distance " + String.format(Locale.ROOT, "%.1f", distance) + " remaining";
	}
}