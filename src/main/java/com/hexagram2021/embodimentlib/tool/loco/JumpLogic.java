package com.hexagram2021.embodimentlib.tool.loco;

/**
 * {@code loco.jump} 的纯逻辑层（PLAN WP-6 #9，无 Minecraft 依赖）。
 * <p>
 * 本工具没有参数、没有裁决——跳跃动作本身在 {@link JumpTool} 的世界侧完成，
 * 纯逻辑只剩输出常量的规约，留在这里以便单测锁定契约文本（PRD #9：Always "jumped"）。
 *
 * @author liudongyu
 */
public final class JumpLogic {
	/** 跳跃成功的 observation。 */
	public static final String JUMPED = "jumped";

	private JumpLogic() {
	}
}