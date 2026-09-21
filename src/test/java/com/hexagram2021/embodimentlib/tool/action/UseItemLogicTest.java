package com.hexagram2021.embodimentlib.tool.action;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UseItemLogic} 的单测（PLAN WP-6 #13）。
 * <p>
 * 覆盖：手别解析（缺省 main / off / 非法值）、observation 规约（含 consumable 标记）。
 */
class UseItemLogicTest {
	@Test
	@DisplayName("手别缺省为 main")
	void handDefaultsToMain() {
		assertEquals("main", UseItemLogic.parseHand(Map.of()));
	}

	@Test
	@DisplayName("手别 main/off 被接受（大小写不敏感）")
	void handAcceptedValues() {
		assertEquals("main", UseItemLogic.parseHand(Map.of("hand", "main")));
		assertEquals("off", UseItemLogic.parseHand(Map.of("hand", "off")));
		assertEquals("off", UseItemLogic.parseHand(Map.of("hand", "OFF")));
	}

	@Test
	@DisplayName("手别非法值返回 null")
	void handRejectsInvalid() {
		assertNull(UseItemLogic.parseHand(Map.of("hand", "left")));
		assertNull(UseItemLogic.parseHand(Map.of("hand", "both")));
	}

	@Test
	@DisplayName("describe：普通物品 used <id>")
	void describePlain() {
		assertEquals("used minecraft:stone", UseItemLogic.describe("minecraft:stone", false));
	}

	@Test
	@DisplayName("describe：可消耗物品标记 (consumable)")
	void describeConsumable() {
		assertEquals("used minecraft:bread (consumable)", UseItemLogic.describe("minecraft:bread", true));
	}
}