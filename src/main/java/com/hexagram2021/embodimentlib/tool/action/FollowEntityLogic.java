package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.loco.MoveToLogic;

/**
 * {@code action.follow_entity} 的纯逻辑层（PLAN WP-7 #18，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（查目标、把跟随 goal 挂到实体身上）在 {@link FollowEntityTool} 与
 * {@link FollowService} 中完成；本类负责<b>参数规约与起始裁决</b>。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@code entity_id} 非法 → {@value #INVALID_ID}；</li>
 *   <li>绑定实体不支持寻路 → {@code "pathfinding not supported"}（复用
 *       {@link MoveToLogic#NOT_SUPPORTED}）；</li>
 *   <li>目标不存在/已死亡 → {@value #LOST_TARGET}；</li>
 *   <li>否则开始跟随 → {@value #FOLLOWING}。</li>
 * </ul>
 *
 * <h2>「开始跟随」只报一次</h2>
 * 与 {@code loco.move_to_entity} 不同，本工具返回 {@code "following"} 而不是距离：
 * 它是一次<b>状态指令</b>，后续是否靠近由实体 tick 自行维持，模型不需要（也不应该）
 * 靠轮询本工具来推进。要停止请调用 {@code action.stop_follow}。
 *
 * @author liudongyu
 */
public final class FollowEntityLogic {
	/** 开始跟随成功。 */
	public static final String FOLLOWING = "following";
	/** {@code entity_id} 不是合法 UUID 时的 observation。 */
	public static final String INVALID_ID = "invalid input: entity_id must be a uuid";
	/** 目标实体不存在或已死亡时的 observation。 */
	public static final String LOST_TARGET = "lost target";
	/** 非 {@code Mob} 实体的 observation（与移动类工具逐字相同）。 */
	public static final String NOT_SUPPORTED = MoveToLogic.NOT_SUPPORTED;
	/** 默认跟随距离（与 PRD #18 示例一致）。 */
	public static final double DEFAULT_DISTANCE = 4.0;
	/** 跟随距离合法下界：低于 1 格会持续推挤目标。 */
	public static final double MIN_DISTANCE = 1.0;
	/** 跟随距离合法上界：超过 32 格已不构成「跟着」。 */
	public static final double MAX_DISTANCE = 32.0;

	private FollowEntityLogic() {
	}

	/**
	 * 规约跟随距离：越界或非有限值一律回落默认值。
	 * <p>
	 * 与 {@code min_distance} 同理——距离是优化参数，为它中断一次指令没有收益。
	 *
	 * @param raw 原始距离
	 * @return 合法的跟随距离
	 */
	public static double sanitizeDistance(double raw) {
		if (Double.isNaN(raw) || raw < MIN_DISTANCE || raw > MAX_DISTANCE) {
			return DEFAULT_DISTANCE;
		}
		return raw;
	}

	/**
	 * 裁决跟随指令的起始结果。
	 *
	 * @param pathfindingSupported 绑定实体是否为 {@code Mob}
	 * @param targetPresent 目标是否存在且存活
	 * @return observation 文本
	 */
	public static String describe(boolean pathfindingSupported, boolean targetPresent) {
		if (!pathfindingSupported) {
			return NOT_SUPPORTED;
		}
		return targetPresent ? FOLLOWING : LOST_TARGET;
	}
}
