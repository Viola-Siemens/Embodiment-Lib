package com.hexagram2021.embodimentlib.tool.meta;

import org.jspecify.annotations.Nullable;

/**
 * {@code meta.say} 的纯逻辑层（PLAN WP-6 #23，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（post {@code AgentSayEvent}）在 {@link SayTool} 中完成；本类只做
 * <b>文本校验</b>——空白的话没有内容可渲染，直接给错误文本而不是 post 空事件。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@code text} 非空白：返回 {@link #SAID}；</li>
 *   <li>{@code text} 空白/缺失：返回 {@code "invalid input: text must not be blank"}。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class SayLogic {
	/** 说话成功的 observation。 */
	public static final String SAID = "said";
	/** text 空白时的 observation。 */
	public static final String INVALID_TEXT = "invalid input: text must not be blank";
	/** text 参数名（供 schema 与解析共用）。 */
	public static final String TEXT_KEY = "text";

	private SayLogic() {
	}

	/**
	 * 校验说话文本并返回 observation。
	 *
	 * @param text 说的内容；可为 null（模型漏传）
	 * @return {@link #SAID}；空白/缺失时返回 {@link #INVALID_TEXT}
	 */
	public static String validate(@Nullable Object text) {
		if (text == null) {
			return INVALID_TEXT;
		}
		String s = String.valueOf(text);
		return s.isBlank() ? INVALID_TEXT : SAID;
	}
}