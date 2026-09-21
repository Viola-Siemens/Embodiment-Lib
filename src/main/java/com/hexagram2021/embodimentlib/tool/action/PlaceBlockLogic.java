package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.BlockAccess;

/**
 * {@code action.place_block} 的纯逻辑层（PLAN WP-7 #12，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（手持物品判定、落点可替换性、区块可写、真正落块）在
 * {@link PlaceBlockTool} 中完成；本类负责<b>裁决顺序与失败文本</b>。
 *
 * <h2>裁决顺序（与 PLAN WP-7 #12 的实现要点一致）</h2>
 * <ol>
 *   <li>手上没有可放置的方块 → {@value #NO_BLOCK_IN_HAND}；</li>
 *   <li>落点被占用 → {@value #TARGET_OCCUPIED}；</li>
 *   <li>目标超出手臂可及范围 → {@value BlockAccess#OUT_OF_REACH}；</li>
 *   <li>否则 {@value #PLACED}。</li>
 * </ol>
 * 顺序不是随意的：先报「你自己手上的问题」，再报「目标的问题」，最后报「你与目标的关系问题」——
 * 由近及远，模型据此知道第一步该改什么（换物品 / 换位置 / 走过去）。
 *
 * <h2>0.1 的放置语义是简化版（PLAN 明确要求）</h2>
 * 本工具不复刻 {@code BlockItem#place} 的完整流程（朝向推断、多格结构、水logged、
 * 点击面决定落点），而是：手上是 {@code BlockItem} → 落点可替换 → 放它的
 * {@code defaultBlockState()} 并消耗 1 个物品。这样做的理由是模型可预期：
 * 它给什么坐标就落在什么坐标，不会因为「点击面推断」而落到相邻格。
 * 代价是楼梯/告示牌之类<b>有朝向</b>的方块会被放成默认朝向——已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class PlaceBlockLogic {
	/** 放置成功。 */
	public static final String PLACED = "placed";
	/** 手上没有（可放置为方块的）物品。 */
	public static final String NO_BLOCK_IN_HAND = "no block in hand";
	/** 落点已被不可替换的方块占用。 */
	public static final String TARGET_OCCUPIED = "target occupied";
	/** 落块被原版拒绝（区块状态不允许写、世界边界等）。 */
	public static final String PLACE_FAILED = "place failed";

	private PlaceBlockLogic() {
	}

	/**
	 * 裁决放置请求。
	 *
	 * @param hasBlockInHand 手上是否持有 {@code BlockItem}
	 * @param targetReplaceable 落点是否可被替换（原版 {@code BlockState#canBeReplaced()}）
	 * @param inReach 目标是否在手臂可及范围内
	 * @return observation 文本；等于 {@link #PLACED} 时调用方才真正落块
	 */
	public static String decide(boolean hasBlockInHand, boolean targetReplaceable, boolean inReach) {
		if (!hasBlockInHand) {
			return NO_BLOCK_IN_HAND;
		}
		if (!targetReplaceable) {
			return TARGET_OCCUPIED;
		}
		if (!inReach) {
			return BlockAccess.OUT_OF_REACH;
		}
		return PLACED;
	}
}
