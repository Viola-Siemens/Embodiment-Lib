package com.hexagram2021.embodimentlib.tool.action;

/**
 * {@code action.stop_follow} 的纯逻辑层（PLAN WP-7 #19，无 Minecraft 依赖）。
 * <p>
 * 本工具没有任何输入参数，也不做任何裁决——它只是「撤销一条持续指令」。
 * 因此这里只有一个文本常量，与 {@code loco.jump} 的 {@code JumpLogic} 同构：
 * 保持「每个工具都有自己的 {@code *Logic}」这一约定，让
 * {@code action.follow_entity} 与 {@code action.stop_follow} 的契约在代码结构上对称。
 *
 * <h2>「没在跟随」也算成功</h2>
 * 结果文本恒为 {@value #STOPPED}，即使实体本来就没在跟随。理由：本工具的语义是
 * 「确保它不在跟随」，这是一个<b>幂等的状态断言</b>。让模型去区分
 * 「我刚停掉了它」与「它本来就没在跟」没有决策价值，反而会诱使它再调一次来确认。
 *
 * @author liudongyu
 */
public final class StopFollowLogic {
	/** 停止跟随的结果（恒为此文本，幂等）。 */
	public static final String STOPPED = "stopped";

	private StopFollowLogic() {
	}
}
