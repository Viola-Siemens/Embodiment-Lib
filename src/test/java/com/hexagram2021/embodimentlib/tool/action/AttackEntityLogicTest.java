package com.hexagram2021.embodimentlib.tool.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AttackEntityLogic} 的单测（PLAN WP-6 #16）。
 * <p>
 * 覆盖裁决矩阵：目标缺失/死亡 → target invalid；超出近战范围 → out of reach；
 * 范围内 → attacked。近战范围为 3.0 格。
 */
class AttackEntityLogicTest {
	@Test
	@DisplayName("目标缺失 → target invalid")
	void targetMissing() {
		assertEquals(AttackEntityLogic.TARGET_INVALID, AttackEntityLogic.describe(false, false, 0.0));
	}

	@Test
	@DisplayName("目标已死亡 → target invalid")
	void targetDead() {
		assertEquals(AttackEntityLogic.TARGET_INVALID, AttackEntityLogic.describe(true, false, 1.0));
	}

	@Test
	@DisplayName("超出近战范围 → out of reach")
	void outOfMeleeReach() {
		assertEquals(AttackEntityLogic.OUT_OF_REACH, AttackEntityLogic.describe(true, true, 3.1));
		assertEquals(AttackEntityLogic.OUT_OF_REACH, AttackEntityLogic.describe(true, true, 100.0));
	}

	@Test
	@DisplayName("范围内（含等于 3.0）→ attacked")
	void inReachAttacks() {
		assertEquals(AttackEntityLogic.ATTACKED, AttackEntityLogic.describe(true, true, 0.5));
		assertEquals(AttackEntityLogic.ATTACKED, AttackEntityLogic.describe(true, true, 3.0));
	}
}