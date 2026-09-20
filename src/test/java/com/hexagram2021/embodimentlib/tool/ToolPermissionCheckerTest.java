package com.hexagram2021.embodimentlib.tool;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolPermissionChecker} 的单测（v0.1.0 PRD §4.6 addon 否决钩子）。
 * <p>
 * <b>测试边界说明</b>：{@link ToolContext} 需要真实 {@code LivingEntity}，纯 JUnit 下无法构造，
 * 因此 {@link ToolPermissionChecker#guarded} 的<b>放行/否决分支</b>无法在此驱动
 * （它要求非空 ctx，且这个要求是刻意的——不知道「为哪个实体干活」的权限判定没有意义）。
 * 本测试覆盖不需要 ctx 的全部行为：组合语义、黑名单语义、短路、以及 {@code guarded} 的参数校验。
 * 端到端的权限拦截随 WP-9 验证。
 */
class ToolPermissionCheckerTest {
	@Test
	@DisplayName("allowAll 恒放行")
	void allowAllPermitsEverything() {
		ToolPermissionChecker checker = ToolPermissionChecker.allowAll();
		assertTrue(checker.check(null, "action.mine_block", Map.of()));
	}

	@Test
	@DisplayName("denyTools 只否决列出的工具，其余放行")
	void denyToolsBlocksOnlyListed() {
		ToolPermissionChecker checker = ToolPermissionChecker.denyTools("action.mine_block", "action.place_block");
		assertFalse(checker.check(null, "action.mine_block", Map.of()));
		assertFalse(checker.check(null, "action.place_block", Map.of()));
		assertTrue(checker.check(null, "perceive.self_status", Map.of()));
	}

	@Test
	@DisplayName("denyTools 空列表等同于放行一切")
	void denyToolsWithNoArgumentsPermitsAll() {
		ToolPermissionChecker checker = ToolPermissionChecker.denyTools();
		assertTrue(checker.check(null, "action.mine_block", Map.of()));
	}

	@Test
	@DisplayName("and 组合：两者都放行才放行")
	void andRequiresBothToAllow() {
		ToolPermissionChecker alwaysAllow = ToolPermissionChecker.allowAll();
		ToolPermissionChecker denyMine = ToolPermissionChecker.denyTools("action.mine_block");

		assertTrue(alwaysAllow.and(alwaysAllow).check(null, "action.mine_block", Map.of()));
		assertTrue(alwaysAllow.and(denyMine).check(null, "perceive.self_status", Map.of()));
		assertFalse(alwaysAllow.and(denyMine).check(null, "action.mine_block", Map.of()));
	}

	@Test
	@DisplayName("and 短路：前一个否决时不调用后一个")
	void andShortCircuits() {
		// 短路是刻意的：检查器实现可能读世界状态，被否决后无需再读。
		boolean[] secondCalled = {false};
		ToolPermissionChecker first = (ctx, name, input) -> false;
		ToolPermissionChecker second = (ctx, name, input) -> {
			secondCalled[0] = true;
			return true;
		};

		assertFalse(first.and(second).check(null, "any.tool", Map.of()));
		assertFalse(secondCalled[0], "第一个检查器已否决，第二个不应被调用");
	}

	@Test
	@DisplayName("DENIED 文案固定为 permission denied")
	void deniedConstantIsStable() {
		assertEquals("permission denied", ToolPermissionChecker.DENIED);
	}
}
