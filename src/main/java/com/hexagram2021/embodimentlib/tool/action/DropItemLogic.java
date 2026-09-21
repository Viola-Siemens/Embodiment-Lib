package com.hexagram2021.embodimentlib.tool.action;

import org.jspecify.annotations.Nullable;

/**
 * {@code action.drop_item} 的纯逻辑层（PLAN WP-7 #17，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（取库存、生成掉落物、写回槽位）在 {@link DropItemTool} 中完成；
 * 本类负责<b>掉落数量的规约</b>。槽号解析与各类文本复用
 * {@link com.hexagram2021.embodimentlib.tool.Slots}。
 *
 * <h2>「数量超过现有」不是错误</h2>
 * 模型经常给一个保守偏大的数量（{@code count: 64} 而槽里只有 3 个）。
 * 这种情况按「最多掉这么多」处理并如实执行，而不是回 {@code "not enough items"}：
 * 模型的意图明确是「把这叠丢掉」，为参数精度打断它没有收益
 * （PRD §4.5 #17 的输出也只有 {@code "dropped"} / {@code "slot empty"}，
 * 没有「数量不足」这一态）。
 *
 * @author liudongyu
 */
public final class DropItemLogic {
	/** 掉落成功。 */
	public static final String DROPPED = "dropped";

	private DropItemLogic() {
	}

	/**
	 * 规约实际掉落的数量。
	 *
	 * @param requested 模型给的数量；{@code null} 表示「整叠」
	 * @param available 槽内实际数量
	 * @return 实际掉落数量：{@code available <= 0} 时为 0，否则落在 {@code [1, available]}
	 */
	public static int resolveCount(@Nullable Integer requested, int available) {
		if (available <= 0) {
			return 0;
		}
		return requested == null ? available : Math.min(requested, available);
	}
}
