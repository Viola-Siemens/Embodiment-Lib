package com.hexagram2021.embodimentlib.attach;

/**
 * 智能体运行状态（PLAN WP-2 ②：WP-2 定义最小形态，WP-3 负责维护）。
 * <p>
 * {@link #IDLE} 是新建条目与 {@code reply()} 结束后的状态；
 * {@link #REASONING} 覆盖「模型推理中」与「等待工具结果」两种外部不可区分的忙碌态以外的
 * 明确工具等待窗口。{@code /embodimentlib inspect}（WP-8）直接展示该枚举名。
 */
public enum AgentState {
	/** 空闲：无进行中的推理或工具调用。 */
	IDLE,
	/** 推理中：请求已发给模型，等待其返回工具调用或最终答案。 */
	REASONING,
	/** 等待工具：模型已请求工具，工具执行尚未回填 observation。 */
	WAITING_TOOL
}
