package com.hexagram2021.embodimentlib.tool.meta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SayLogic} 的单测（PLAN WP-6 #23）。
 * <p>
 * 覆盖：合法文本 → said；null/空白 → 错误文本；数字等非字符串值被字符串化。
 */
class SayLogicTest {
	@Test
	@DisplayName("非空白文本返回 said")
	void validText() {
		assertEquals(SayLogic.SAID, SayLogic.validate("hello world"));
		assertEquals(SayLogic.SAID, SayLogic.validate(" 你好，世界 "));
	}

	@Test
	@DisplayName("null / 空白 / 全空白返回错误文本")
	void blankText() {
		assertEquals(SayLogic.INVALID_TEXT, SayLogic.validate(null));
		assertEquals(SayLogic.INVALID_TEXT, SayLogic.validate(""));
		assertEquals(SayLogic.INVALID_TEXT, SayLogic.validate("   \t "));
	}

	@Test
	@DisplayName("非字符串值（数字）被字符串化后判定")
	void numericTextCoerced() {
		assertEquals(SayLogic.SAID, SayLogic.validate(42));
	}
}