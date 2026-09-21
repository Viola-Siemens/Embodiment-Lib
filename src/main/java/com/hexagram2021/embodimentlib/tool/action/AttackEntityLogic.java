package com.hexagram2021.embodimentlib.tool.action;

/**
 * {@code action.attack_entity} 的纯逻辑层（PLAN WP-6 #16，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（解析 UUID、查目标、实际攻击）在 {@link AttackEntityTool} 中完成；
 * 本类负责<b>攻击裁决</b>：目标是否存在/存活/在范围内。
 *
 * <h2>契约（PRD #16 / PLAN #16）</h2>
 * <ul>
 *   <li>目标缺失或已死亡：{@value #TARGET_INVALID}；</li>
 *   <li>距离 &gt; 近战范围：{@value #OUT_OF_REACH}；</li>
 *   <li>在范围内：{@value #ATTACKED}。</li>
 * </ul>
 * 近战范围常量 {@value #MELEE_REACH} 格（与玩家近战一致）。Griefing 判定发生在世界侧
 * （需要真实 {@code ServerLevel} 与事件总线），不在纯逻辑层。
 *
 * @author liudongyu
 */
public final class AttackEntityLogic {
	/** 攻击成功。 */
	public static final String ATTACKED = "attacked";
	/** 目标超出近战范围。 */
	public static final String OUT_OF_REACH = "out of reach";
	/** 目标缺失 / 已死亡 / id 非法。 */
	public static final String TARGET_INVALID = "target invalid";
	/** 近战范围（格）。 */
	public static final double MELEE_REACH = 3.0;

	private AttackEntityLogic() {
	}

	/**
	 * 裁决攻击。
	 *
	 * @param targetPresent 目标是否存在（按 UUID 查到存活实体）
	 * @param targetAlive 目标是否存活
	 * @param distance 实体到目标的距离
	 * @return observation 文本
	 */
	public static String describe(boolean targetPresent, boolean targetAlive, double distance) {
		if (!targetPresent || !targetAlive) {
			return TARGET_INVALID;
		}
		if (distance > MELEE_REACH) {
			return OUT_OF_REACH;
		}
		return ATTACKED;
	}
}