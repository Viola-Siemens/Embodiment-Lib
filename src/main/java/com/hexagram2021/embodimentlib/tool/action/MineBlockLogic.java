package com.hexagram2021.embodimentlib.tool.action;

/**
 * {@code action.mine_block} 的纯逻辑层（PLAN WP-6 #11，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（Griefing 判定、方块状态读取、实际破坏）在 {@link MineBlockTool} 中完成；
 * 本类负责<b>裁决</b>——把「是否不可破坏 / 是否需要工具 / 手是否空」缩成一个 observation。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>不可破坏（挖掘速度 &lt; 0，如基岩）：{@value #UNBREAKABLE}；</li>
 *   <li>需要正确工具采集且手持为空：{@value #NO_TOOL}；</li>
 *   <li>其余：{@value #MINED}。</li>
 * </ul>
 * Griefing 拒绝（{@code Griefing.DENIED}）由世界侧先于本裁决检查——它需要真实
 * {@code ServerLevel} 与事件总线，不属于纯逻辑层（PLAN §8 决策 17/18）。
 *
 * @author liudongyu
 */
public final class MineBlockLogic {
	/** 成功挖掉。 */
	public static final String MINED = "mined";
	/** 方块不可破坏（如基岩）。 */
	public static final String UNBREAKABLE = "block unbreakable";
	/** 方块需要正确工具才能掉落，但手持为空。 */
	public static final String NO_TOOL = "no tool";

	private MineBlockLogic() {
	}

	/**
	 * 裁决挖掘结果。
	 *
	 * @param unbreakable 方块是否不可破坏（挖掘速度 &lt; 0）
	 * @param requiresTool 方块是否需要正确工具采集（{@code requiresCorrectToolForDrops}）
	 * @param handEmpty 手持是否为空（主手）
	 * @return observation 文本
	 */
	public static String describe(boolean unbreakable, boolean requiresTool, boolean handEmpty) {
		if (unbreakable) {
			return UNBREAKABLE;
		}
		if (requiresTool && handEmpty) {
			return NO_TOOL;
		}
		return MINED;
	}
}