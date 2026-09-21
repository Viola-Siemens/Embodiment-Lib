package com.hexagram2021.embodimentlib.tool.loco;

/**
 * {@code loco.move_to_entity} 的纯逻辑层（PLAN WP-7 #8，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（UUID → 实体查询、寻路启动、距离计算）在 {@link MoveToEntityTool} 中完成；
 * 本类负责<b>结果裁决</b>：把「是否支持寻路 / 目标是否还在 / 距离 / 停止距离 / 路径是否找到」
 * 缩成一个 observation 文本。
 *
 * <h2>与 {@link MoveToLogic} 的关系</h2>
 * 「非 Mob」与「路径不通」两种文本与 {@code loco.move_to} <b>逐字相同</b>
 * （复用 {@link MoveToLogic#NOT_SUPPORTED} / {@link MoveToLogic#PATH_BLOCKED}），
 * 距离表述也复用 {@link MoveToLogic#distanceRemaining(double)}：
 * 模型会把这两个工具的结果混着读，不一致的措辞会让它以为遇到了不同的状况。
 *
 * <h2>判定顺序</h2>
 * <ol>
 *   <li>实体不支持寻路 → {@code "pathfinding not supported"}（能力问题，与目标无关）；</li>
 *   <li>目标不存在或已死亡 → {@code "lost target"}（PRD #8 的约定文本）；</li>
 *   <li>距离 ≤ {@code min_distance} → {@code "in range"}；</li>
 *   <li>寻路未启动 → {@code "path blocked"}；</li>
 *   <li>其余 → {@code "distance D remaining"}。</li>
 * </ol>
 *
 * @author liudongyu
 */
public final class MoveToEntityLogic {
	/** {@code entity_id} 不是合法 UUID 时的 observation。 */
	public static final String INVALID_ID = "invalid input: entity_id must be a uuid";
	/** 目标实体已消失或已死亡时的 observation。 */
	public static final String LOST_TARGET = "lost target";
	/** 已进入停止距离时的 observation。 */
	public static final String IN_RANGE = "in range";
	/** 默认停止距离（与 PRD #8 示例一致）。 */
	public static final double DEFAULT_MIN_DISTANCE = 3.0;
	/** 停止距离合法上界：超过 32 格就没有「走到目标旁边」的语义了。 */
	public static final double MAX_MIN_DISTANCE = 32.0;

	private MoveToEntityLogic() {
	}

	/**
	 * 规约 {@code min_distance} 参数。
	 * <p>
	 * 非法值（缺失/非正/超上界）一律回落默认值而不是报错：{@code min_distance}
	 * 是纯优化参数，为它中断一次移动指令得不偿失（与 {@code loco.move_to} 的
	 * {@code reach} 处理一致）。
	 *
	 * @param raw 原始值
	 * @return 合法的停止距离
	 */
	public static double sanitizeMinDistance(double raw) {
		return raw > 0.0 && raw <= MAX_MIN_DISTANCE ? raw : DEFAULT_MIN_DISTANCE;
	}

	/**
	 * 裁决移动结果。
	 *
	 * @param pathfindingSupported 绑定实体是否为 {@code Mob}（具备寻路能力）
	 * @param targetPresent 目标实体是否仍在世界中且存活
	 * @param distance 实体到目标的当前距离（目标不存在时无意义）
	 * @param minDistance 停止距离
	 * @param pathFound 本次寻路是否成功启动
	 * @return observation 文本
	 */
	public static String describe(boolean pathfindingSupported, boolean targetPresent,
			double distance, double minDistance, boolean pathFound) {
		if (!pathfindingSupported) {
			return MoveToLogic.NOT_SUPPORTED;
		}
		if (!targetPresent) {
			return LOST_TARGET;
		}
		if (distance <= minDistance) {
			return IN_RANGE;
		}
		if (!pathFound) {
			return MoveToLogic.PATH_BLOCKED;
		}
		return MoveToLogic.distanceRemaining(distance);
	}
}
