package com.hexagram2021.embodimentlib.tool.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MineBlockLogic} 的单测（PLAN WP-6 #11）。
 * <p>
 * 覆盖裁决矩阵：不可破坏 → unbreakable；需工具且空手 → no tool；
 * 其余 → mined。Griefing 判定属于世界侧（真实 ServerLevel），不在此测。
 */
class MineBlockLogicTest {
	@Test
	@DisplayName("不可破坏优先于一切，返回 block unbreakable")
	void unbreakableWins() {
		assertEquals(MineBlockLogic.UNBREAKABLE, MineBlockLogic.describe(true, true, true));
		assertEquals(MineBlockLogic.UNBREAKABLE, MineBlockLogic.describe(true, false, false));
	}

	@Test
	@DisplayName("需工具且空手返回 no tool")
	void noToolWhenToolRequiredAndHandEmpty() {
		assertEquals(MineBlockLogic.NO_TOOL, MineBlockLogic.describe(false, true, true));
	}

	@Test
	@DisplayName("需工具但手持有东西 → 允许挖掘（工具不符只影响掉落，0.1 不细分）")
	void toolRequiredButHandNotEmptyMines() {
		assertEquals(MineBlockLogic.MINED, MineBlockLogic.describe(false, true, false));
	}

	@Test
	@DisplayName("普通方块空手可挖 → mined")
	void plainBlockMinesBareHand() {
		assertEquals(MineBlockLogic.MINED, MineBlockLogic.describe(false, false, true));
	}
}