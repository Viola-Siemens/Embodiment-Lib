package com.hexagram2021.embodimentlib.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AgentLoop} 双环策略测试（PLAN WP-3 ② 第 5–6 点、验收：P1 批量环处理多个调用）。
 */
class AgentLoopTest {
	@Test
	@DisplayName("默认策略为 ReAct 逐步环且 maxIterations == 12（PLAN WP-3 要求）")
	void defaultsAreReactStepWithTwelveIterations() {
		AgentLoop loop = AgentLoop.defaults();

		assertEquals(AgentLoop.Mode.REACT_STEP, loop.mode());
		assertEquals(12, loop.maxIterations());
		assertEquals(AgentLoop.DEFAULT_MAX_ITERATIONS, loop.maxIterations());
		assertFalse(loop.allowsParallelToolCalls());
	}

	@Test
	@DisplayName("逐步环每轮只执行第一个工具调用（不允许同轮并行）")
	void reactStepExecutesSingleInvocation() {
		AgentLoop loop = AgentLoop.of(AgentLoop.Mode.REACT_STEP, 5);

		assertEquals(List.of("mine_block"),
			loop.selectInvocations(List.of("mine_block", "place_block", "move_to")));
		assertEquals(List.of(), loop.selectInvocations(List.of()));
		assertEquals(List.of("only"), loop.selectInvocations(List.of("only")));
	}

	@Test
	@DisplayName("批量环保留模型同轮的全部工具调用（P1 验收）")
	void batchModeExecutesAllInvocations() {
		AgentLoop loop = AgentLoop.of(AgentLoop.Mode.BATCH_TOOLS, 5);

		assertTrue(loop.allowsParallelToolCalls());
		assertEquals(List.of("mine_block", "place_block", "move_to"),
			loop.selectInvocations(List.of("mine_block", "place_block", "move_to")));
	}

	@Test
	@DisplayName("selectInvocations 不接受 null")
	void selectRejectsNull() {
		assertThrows(NullPointerException.class,
			() -> AgentLoop.defaults().selectInvocations(null));
	}

	@Test
	@DisplayName("逐步环 maxIters 直接透传；批量环留一倍余量避免中途截断")
	void delegateMaxItersDiffersByMode() {
		assertEquals(12, AgentLoop.of(AgentLoop.Mode.REACT_STEP, 12).delegateMaxIters());
		assertEquals(24, AgentLoop.of(AgentLoop.Mode.BATCH_TOOLS, 12).delegateMaxIters());
	}

	@Test
	@DisplayName("maxIterations 越界被拒绝（防止误配成实际无限的烧钱循环）")
	void rejectsOutOfRangeIterations() {
		assertThrows(IllegalArgumentException.class, () -> AgentLoop.of(AgentLoop.Mode.REACT_STEP, 0));
		assertThrows(IllegalArgumentException.class, () -> AgentLoop.of(AgentLoop.Mode.REACT_STEP, -1));
		assertThrows(IllegalArgumentException.class,
			() -> AgentLoop.of(AgentLoop.Mode.REACT_STEP, AgentLoop.MAX_ITERATIONS_CEILING + 1));

		// 边界值应当被接受。
		assertEquals(1, AgentLoop.of(AgentLoop.Mode.REACT_STEP, 1).maxIterations());
		assertEquals(AgentLoop.MAX_ITERATIONS_CEILING,
			AgentLoop.of(AgentLoop.Mode.REACT_STEP, AgentLoop.MAX_ITERATIONS_CEILING).maxIterations());
	}

	@Test
	@DisplayName("of 拒绝 null 模式")
	void rejectsNullMode() {
		assertThrows(NullPointerException.class, () -> AgentLoop.of(null, 12));
	}

	@Test
	@DisplayName("normalizeObservation 把 null/空白替换为空结果文本")
	void normalizeHandlesEmpty() {
		assertEquals(ToolBridge.EMPTY_OBSERVATION, AgentLoop.normalizeObservation(null));
		assertEquals(ToolBridge.EMPTY_OBSERVATION, AgentLoop.normalizeObservation(""));
		assertEquals(ToolBridge.EMPTY_OBSERVATION, AgentLoop.normalizeObservation("  \n "));
		assertEquals("ok", AgentLoop.normalizeObservation("ok"));
	}

	@Test
	@DisplayName("truncateObservation 超长才截断，并追加省略号")
	void truncatesOnlyWhenTooLong() {
		assertEquals("abc", AgentLoop.truncateObservation("abc", 3));
		assertEquals("abc", AgentLoop.truncateObservation("abc", 10));
		assertEquals("ab...", AgentLoop.truncateObservation("abcdef", 2));
	}

	@Test
	@DisplayName("truncateObservation 拒绝非法上限")
	void truncateRejectsBadLimit() {
		assertThrows(IllegalArgumentException.class, () -> AgentLoop.truncateObservation("abc", 0));
		assertThrows(NullPointerException.class, () -> AgentLoop.truncateObservation(null, 5));
	}

	@Test
	@DisplayName("toString 描述模式与预算，便于日志辨认")
	void describesItself() {
		assertEquals("REACT_STEP(max=12)", AgentLoop.defaults().toString());
		assertEquals("BATCH_TOOLS(max=3)", AgentLoop.of(AgentLoop.Mode.BATCH_TOOLS, 3).toString());
	}
}
