package com.hexagram2021.embodimentlib.tool.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StopFollowLogic} 的单测（PLAN WP-7 #19）。
 * <p>
 * 本工具没有输入参数与裁决分支，唯一的契约是「结果文本恒为 stopped」——
 * 幂等的状态断言（见 {@link StopFollowLogic} 的类 Javadoc）。
 */
class StopFollowLogicTest {
	@Test
	@DisplayName("契约常量：结果文本恒为 stopped，与 PRD #19 一致")
	void stoppedConstant() {
		assertEquals("stopped", StopFollowLogic.STOPPED);
	}
}
