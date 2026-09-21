package com.hexagram2021.embodimentlib.tool.loco;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link JumpLogic} 的单测（PLAN WP-6 #9）。
 * <p>
 * 工具无参数、无裁决，唯一契约是输出常量 {@code "jumped"}（PRD #9）。
 */
class JumpLogicTest {
	@Test
	@DisplayName("JUMPED 常量即契约文本")
	void jumpedConstant() {
		assertEquals("jumped", JumpLogic.JUMPED);
	}
}