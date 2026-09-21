package com.hexagram2021.embodimentlib.tool.meta;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code meta.wait}（PLAN WP-6 #22，PRD §4.5 #22）。
 * <p>
 * 让智能体「等 N tick 再继续」。契约硬约束：<b>非阻塞</b>调度，绝不 park 游戏线程。
 * 纯逻辑（ticks 校验）见 {@link WaitLogic}。
 *
 * <h2>0.1 语义与 WP-9 接线</h2>
 * 真正的「让推理循环暂停 N tick 再恢复」需要循环驱动侧配合：工具的观测返回
 * {@code "waited"} 后，循环应把「下一个动作安排在 N tick 后」。当前 runtime
 * （WP-3）尚未提供「循环暂停/恢复」原语——它属于 WP-9 端到端接线的范围
 * （循环驱动者读取 wait 信号、在游戏线程登记延迟恢复）。
 * <p>
 * <b>因此本工具 0.1 只做两件事</b>：校验 ticks 并在合法时返回 {@code "waited"}。
 * 它<b>不会 park 任何线程</b>（零等待），也不会假装已经休眠了 N tick——
 * 「等待语义真正生效」的接线在 WP-9，与本 WP 的已知验收缺口一致
 * （PLAN §8 决策 18 同类：需要真实循环环境才能闭合）。
 *
 * @author liudongyu
 */
public final class WaitTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public WaitTool() {
		super(
			"meta.wait",
			"Yield the agent loop for a fixed number of ticks, then resume. Non-blocking: the "
				+ "game thread is never parked. Reports \"waited\" or invalid-input text.",
			ToolResults.objectSchema(Map.of(
				"ticks", ToolResults.rangedProp("integer", "Ticks to wait",
					WaitLogic.MIN_TICKS, WaitLogic.MAX_TICKS)),
				List.of()),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		int ticks = ToolResults.intParam(input, "ticks", WaitLogic.DEFAULT_TICKS);
		return WaitLogic.validate(ticks);
	}
}