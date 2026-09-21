package com.hexagram2021.embodimentlib.tool.action;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * {@code action.use_item} 的纯逻辑层（PLAN WP-6 #13，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（取手持物品、启动使用）在 {@link UseItemTool} 中完成；本类负责
 * <b>手别解析与 observation 规约</b>。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>手别：{@code "main"} / {@code "off"}，缺省 {@code "main"}；非法值 → 错误文本。</li>
 *   <li>手持为空：{@value #NO_ITEM_IN_HAND}；</li>
 *   <li>有物品：{@code "used <id>"}；可消耗物品追加 {@code " (consumable)"}；</li>
 *   <li>使用的真实动作（吃/拉弓/掷药水）是 tick 驱动、不能在一次调用内完成——
 *       本工具只负责「开始使用」，结果文本如实描述。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class UseItemLogic {
	/** 手持为空时的 observation。 */
	public static final String NO_ITEM_IN_HAND = "no item in hand";
	/** 默认手别：主手。 */
	public static final String DEFAULT_HAND = "main";
	/** 允许的手别。 */
	public static final String OFF_HAND = "off";

	private UseItemLogic() {
	}

	/**
	 * 解析手别参数。
	 *
	 * @param input 模型参数
	 * @return {@code "main"} / {@code "off"}；非法时为 null
	 */
	public static @Nullable String parseHand(Map<String, Object> input) {
		Object raw = input.get("hand");
		if (raw == null) {
			return DEFAULT_HAND;
		}
		String hand = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
		if (DEFAULT_HAND.equals(hand) || OFF_HAND.equals(hand)) {
			return hand;
		}
		return null;
	}

	/**
	 * 规约使用结果文本。
	 *
	 * @param itemId 物品完整资源标识符
	 * @param consumable 是否可消耗（吃/喝）
	 * @return observation 文本
	 */
	public static String describe(String itemId, boolean consumable) {
		return "used " + itemId + (consumable ? " (consumable)" : "");
	}
}