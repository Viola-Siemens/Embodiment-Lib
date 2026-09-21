package com.hexagram2021.embodimentlib.tool.container;

import com.hexagram2021.embodimentlib.tool.BlockAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link InspectContainerLogic} 的单测（PLAN WP-7 #20）。
 * <p>
 * 覆盖：三项前置检查的判定与优先级（非容器优先于超范围）、视线失败的独立文本。
 */
class InspectContainerLogicTest {
	@Test
	@DisplayName("rejectReason：全部满足时返回 null（可以读取）")
	void acceptsWhenAllChecksPass() {
		assertNull(InspectContainerLogic.rejectReason(true, true, true));
	}

	@Test
	@DisplayName("rejectReason：不是容器时返回 not a container")
	void rejectsNonContainer() {
		assertEquals(InspectContainerLogic.NOT_A_CONTAINER,
				InspectContainerLogic.rejectReason(false, true, true));
	}

	@Test
	@DisplayName("rejectReason：超出距离时返回 out of reach")
	void rejectsOutOfReach() {
		assertEquals(BlockAccess.OUT_OF_REACH,
				InspectContainerLogic.rejectReason(true, false, true));
	}

	@Test
	@DisplayName("rejectReason：视线被遮挡时返回独立的 line of sight blocked（避免模型反复走近）")
	void rejectsBlockedLineOfSight() {
		assertEquals(InspectContainerLogic.LINE_OF_SIGHT_BLOCKED,
				InspectContainerLogic.rejectReason(true, true, false));
	}

	@Test
	@DisplayName("rejectReason：优先级为「非容器 &gt; 超范围 &gt; 视线」，省掉无用的往返")
	void priorityOrder() {
		assertEquals(InspectContainerLogic.NOT_A_CONTAINER,
				InspectContainerLogic.rejectReason(false, false, false));
		assertEquals(BlockAccess.OUT_OF_REACH,
				InspectContainerLogic.rejectReason(true, false, false));
	}

	@Test
	@DisplayName("契约常量：文本与 PLAN/PRD 一致，且距离取容器专用的 6 格")
	void contractConstants() {
		assertEquals("not a container", InspectContainerLogic.NOT_A_CONTAINER);
		assertEquals("line of sight blocked", InspectContainerLogic.LINE_OF_SIGHT_BLOCKED);
		assertEquals(6.0, BlockAccess.CONTAINER_REACH);
	}
}
