package com.hexagram2021.embodimentlib.tool.container;

import com.hexagram2021.embodimentlib.tool.BlockAccess;
import org.jspecify.annotations.Nullable;

/**
 * {@code container.inspect} 的纯逻辑层（PLAN WP-7 #20，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（取方块实体、射线遮挡、遍历槽位）在 {@link InspectContainerTool} 中完成；
 * 本类负责<b>三项前置检查的裁决顺序与全部失败文本</b>。
 *
 * <h2>裁决顺序：先问「是不是容器」，再问「够不够得着」</h2>
 * <ol>
 *   <li>坐标不可读写 → {@code "target out of world"}（由适配器先行判定）；</li>
 *   <li>不是容器 → {@value #NOT_A_CONTAINER}；</li>
 *   <li>超出距离 → {@code "out of reach"}；</li>
 *   <li>视线被遮挡 → {@value #LINE_OF_SIGHT_BLOCKED}。</li>
 * </ol>
 * 「不是容器」排在距离之前，是刻意的：它不是容器这件事与远近无关，先说出来能
 * <b>省掉一次无用的往返</b>——否则模型会听话地走过去，然后再被告知「这儿没箱子」。
 *
 * <h2>与 PLAN 的一处刻意偏差：视线失败有独立文本</h2>
 * PLAN WP-7 #20 把视线检查的失败并入 {@code "out of reach"}（「含范围/视线检查」）。
 * 本实现给它单独一句 {@value #LINE_OF_SIGHT_BLOCKED}，理由是行为上的：
 * 若隔着墙也回 {@code "out of reach"}，模型会认为「再靠近一点就行」而<b>反复走近</b>，
 * 但距离根本不是问题——它会陷入一个永远出不来的重试循环。
 * 说出「视线被挡」才能让它改去绕路或换个位置。距离本身超出时仍返回
 * {@code "out of reach"}，PLAN 的验收项因此不受影响；该偏差已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class InspectContainerLogic {
	/** 目标方块不是容器（没有 {@code Container} 型方块实体）时的 observation。 */
	public static final String NOT_A_CONTAINER = "not a container";
	/** 视线被其它方块遮挡时的 observation。 */
	public static final String LINE_OF_SIGHT_BLOCKED = "line of sight blocked";

	private InspectContainerLogic() {
	}

	/**
	 * 裁决能否读取容器内容。
	 *
	 * @param containerPresent 目标是否是容器
	 * @param inReach 目标是否在容器读写距离内
	 * @param lineOfSightClear 视线是否未被遮挡
	 * @return 可以继续读取时返回 null；否则返回应回喂模型的错误文本
	 */
	public static @Nullable String rejectReason(boolean containerPresent, boolean inReach,
												boolean lineOfSightClear) {
		if (!containerPresent) {
			return NOT_A_CONTAINER;
		}
		if (!inReach) {
			return BlockAccess.OUT_OF_REACH;
		}
		return lineOfSightClear ? null : LINE_OF_SIGHT_BLOCKED;
	}
}
