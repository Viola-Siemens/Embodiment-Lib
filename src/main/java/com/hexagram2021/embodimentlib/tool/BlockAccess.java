package com.hexagram2021.embodimentlib.tool;

/**
 * 方块「够不够得着」的判定与失败文本（纯逻辑，无 Minecraft 依赖）。
 * <p>
 * 有六个内置工具要先回答同一个问题：{@code action.place_block}、{@code action.use_item_on}、
 * {@code action.interact_with_block}、{@code container.inspect}、{@code container.transfer}
 * （以及 {@code action.mine_block} 的越界判定）。它们分属三个子包，若各自声明
 * {@code "out of reach"}，迟早出现「同一件事在两个工具里叫不同名字」——
 * 而 observation 的用词稳定本身就是模型可靠推理的前提。
 *
 * <h2>两种距离，各有出处</h2>
 * <ul>
 *   <li>{@link #INTERACT_REACH}（4.5）：与原版<b>生存模式玩家</b>的方块交互距离一致，
 *       用于放置/使用/交互三类动作；</li>
 *   <li>{@link #CONTAINER_REACH}（6.0）：容器读写用，取自 PLAN §4.5 #20 的
 *       {@code entity.blockPosition().closerThan(pos, 6.0)}。翻箱子比点方块宽松一点
 *       是刻意的：容器操作是需要「站定」的多步行为，卡在 4.5 会让模型频繁失败重试。</li>
 * </ul>
 *
 * <h2>为什么用整格距离而不是射线</h2>
 * 模型给的是方块坐标，不是视线方向；用「实体所在方块到目标方块的直线距离」与它的
 * 直觉一致，也让判定完全可测。精确的射线遮挡检查只在 {@code container.inspect}
 * 需要（且有独立实现）。
 *
 * @author liudongyu
 */
public final class BlockAccess {
	/** 方块交互（放置/使用/空手交互）的距离上限（格）。 */
	public static final double INTERACT_REACH = 4.5;
	/** 容器读写的距离上限（格，PLAN §4.5 #20 明确值）。 */
	public static final double CONTAINER_REACH = 6.0;
	/** 目标超出手臂可及范围时的 observation。 */
	public static final String OUT_OF_REACH = "out of reach";
	/** 目标坐标在世界外或区块未加载时的 observation。 */
	public static final String OUT_OF_WORLD = "target out of world";

	private BlockAccess() {
	}

	/**
	 * 判定目标是否在给定距离内。
	 *
	 * @param distanceSq 平方距离
	 * @param reach 距离上限
	 * @return 在范围内返回 true
	 */
	public static boolean within(double distanceSq, double reach) {
		return distanceSq < reach * reach;
	}
}
