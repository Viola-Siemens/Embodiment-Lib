package com.hexagram2021.embodimentlib.tool.meta;

/**
 * {@code meta.wait} 的纯逻辑层（PLAN WP-6 #22，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（调度 tick 延迟恢复）在 {@link WaitTool} 中完成；本类只做
 * <b>ticks 参数校验</b>与常量规约。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@code ticks} 必须在 {@code [MIN_TICKS, MAX_TICKS]} 内：过短无意义（0 就是没等），
 *       过长会让工具桥超时（默认 10s = 200 tick），模型会收到超时 observation；</li>
 *   <li>合法：返回 {@link #WAITED}；非法：返回错误文本（非异常）。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class WaitLogic {
	/** 等待成功的 observation。 */
	public static final String WAITED = "waited";
	/** ticks 合法下界。 */
	public static final int MIN_TICKS = 1;
	/** ticks 合法上界（10 秒，与工具桥默认超时对齐）。 */
	public static final int MAX_TICKS = 200;
	/** 默认 ticks（1 秒）。 */
	public static final int DEFAULT_TICKS = 20;

	private WaitLogic() {
	}

	/**
	 * 校验 ticks 并返回 observation。
	 *
	 * @param ticks 等待 ticks
	 * @return {@link #WAITED}；非法时返回错误文本
	 */
	public static String validate(int ticks) {
		if (ticks < MIN_TICKS || ticks > MAX_TICKS) {
			return "invalid input: ticks must be between " + MIN_TICKS + " and " + MAX_TICKS + ", got " + ticks;
		}
		return WAITED;
	}
}