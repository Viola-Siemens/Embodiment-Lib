package com.hexagram2021.embodimentlib.tool.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TransferLogic} 的单测（PLAN WP-7 #21）。
 * <p>
 * 覆盖：方向解析、搬运量算术（整叠/部分/目标容量不足截断）、各类拒绝分支
 * （来源不足、目标物品不同、目标已满）与全部契约文本。
 */
class TransferLogicTest {
	private static final TransferLogic.SlotView EMPTY = new TransferLogic.SlotView(null, 0);

	@Test
	@DisplayName("parseDirection：缺省为 deposit，大小写不敏感，非法值返回 null")
	void parseDirectionRules() {
		assertEquals(TransferLogic.DEPOSIT, TransferLogic.parseDirection(null));
		assertEquals(TransferLogic.DEPOSIT, TransferLogic.parseDirection("deposit"));
		assertEquals(TransferLogic.WITHDRAW, TransferLogic.parseDirection("WITHDRAW"));
		assertEquals(TransferLogic.WITHDRAW, TransferLogic.parseDirection(" Withdraw "));
		assertNull(TransferLogic.parseDirection("sideways"));
		assertNull(TransferLogic.parseDirection(""));
	}

	@Test
	@DisplayName("plan：来源为空时返回 not enough items")
	void rejectsEmptySource() {
		TransferLogic.Decision decision = TransferLogic.plan(EMPTY, EMPTY, null, 64);
		assertEquals(TransferLogic.NOT_ENOUGH_ITEMS, decision.error());
	}

	@Test
	@DisplayName("plan：请求量超过来源数量时返回 not enough items（不静默少搬）")
	void rejectsInsufficientSource() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 3), EMPTY, 16, 64);
		assertEquals(TransferLogic.NOT_ENOUGH_ITEMS, decision.error());
	}

	@Test
	@DisplayName("plan：目标槽有不同物品时返回 target slot occupied")
	void rejectsDifferentItemInTarget() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 10),
				new TransferLogic.SlotView("minecraft:dirt", 10), null, 64);
		assertEquals(TransferLogic.TARGET_SLOT_OCCUPIED, decision.error());
	}

	@Test
	@DisplayName("plan：目标槽已满时返回 target slot occupied")
	void rejectsFullTarget() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 10),
				new TransferLogic.SlotView("minecraft:stone", 64), 1, 64);
		assertEquals(TransferLogic.TARGET_SLOT_OCCUPIED, decision.error());
	}

	@Test
	@DisplayName("plan：未给数量时搬运整叠")
	void movesWholeStack() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 12), EMPTY, null, 64);
		assertNull(decision.error());
		assertEquals(12, decision.count());
	}

	@Test
	@DisplayName("plan：目标槽有同种物品时按剩余容量合并")
	void mergesIntoPartiallyFilledTarget() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 64),
				new TransferLogic.SlotView("minecraft:stone", 60), 16, 64);
		assertNull(decision.error());
		// 目标只剩 4 个位置：部分搬运是原版行为（shift 点击也是能塞多少塞多少）。
		assertEquals(4, decision.count());
	}

	@Test
	@DisplayName("plan：空目标槽按容量上限计算")
	void emptyTargetUsesLimit() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 64), EMPTY, 16, 64);
		assertNull(decision.error());
		assertEquals(16, decision.count());
	}

	@Test
	@DisplayName("plan：请求量小于来源且目标容量充足时按请求量搬运")
	void honoursRequestedCount() {
		TransferLogic.Decision decision = TransferLogic.plan(
				new TransferLogic.SlotView("minecraft:stone", 64), EMPTY, 5, 64);
		assertNull(decision.error());
		assertEquals(5, decision.count());
	}

	@Test
	@DisplayName("SlotView：id 为 null 或数量非正都视为空槽")
	void slotViewEmptiness() {
		assertEquals(true, EMPTY.isEmpty());
		assertEquals(true, new TransferLogic.SlotView("minecraft:stone", 0).isEmpty());
		assertEquals(false, new TransferLogic.SlotView("minecraft:stone", 1).isEmpty());
	}

	@Test
	@DisplayName("契约常量：四个结果文本与 PLAN #21 一致，方向参数名固定")
	void contractConstants() {
		assertEquals("transferred", TransferLogic.TRANSFERRED);
		assertEquals("not enough items", TransferLogic.NOT_ENOUGH_ITEMS);
		assertEquals("target slot occupied", TransferLogic.TARGET_SLOT_OCCUPIED);
		assertEquals("direction", TransferLogic.DIRECTION_KEY);
		assertEquals("deposit", TransferLogic.DEPOSIT);
		assertEquals("withdraw", TransferLogic.WITHDRAW);
		assertEquals("invalid input: direction must be \"deposit\" or \"withdraw\"",
				TransferLogic.INVALID_DIRECTION);
	}
}
