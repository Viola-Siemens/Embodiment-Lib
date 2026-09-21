package com.hexagram2021.embodimentlib.tool.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BlockInteractionLogic} 的单测（PLAN WP-7 #14/#15 共用的交互结果规约）。
 * <p>
 * 覆盖：物品交互的三态、空手回退链（原版 {@code TryEmptyHandInteraction} 的行为复刻）、
 * 空手交互的三态，以及全部契约常量。
 */
class BlockInteractionLogicTest {
	@Test
	@DisplayName("describeItemUse：物品交互成功时点名物品与方块")
	void itemUseSuccess() {
		assertEquals("used minecraft:wheat_seeds on minecraft:farmland",
				BlockInteractionLogic.describeItemUse("minecraft:wheat_seeds", "minecraft:farmland",
						BlockInteractionLogic.Outcome.SUCCESS, BlockInteractionLogic.Outcome.NO_EFFECT));
	}

	@Test
	@DisplayName("describeItemUse：明确失败时返回 interaction failed")
	void itemUseFailure() {
		assertEquals(BlockInteractionLogic.INTERACTION_FAILED,
				BlockInteractionLogic.describeItemUse("minecraft:flint_and_steel", "minecraft:stone",
						BlockInteractionLogic.Outcome.FAILURE, BlockInteractionLogic.Outcome.NO_EFFECT));
	}

	@Test
	@DisplayName("describeItemUse：物品不接管时返回 no effect")
	void itemUseNoEffect() {
		assertEquals(BlockInteractionLogic.NO_EFFECT,
				BlockInteractionLogic.describeItemUse("minecraft:stick", "minecraft:stone",
						BlockInteractionLogic.Outcome.NO_EFFECT, BlockInteractionLogic.Outcome.NO_EFFECT));
	}

	@Test
	@DisplayName("describeItemUse：TRY_WITH_EMPTY_HAND 时回退到空手交互结果（成功 → interacted）")
	void emptyHandFallbackSucceeds() {
		assertEquals(BlockInteractionLogic.INTERACTED,
				BlockInteractionLogic.describeItemUse("minecraft:stick", "minecraft:oak_door",
						BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND,
						BlockInteractionLogic.Outcome.SUCCESS));
	}

	@Test
	@DisplayName("describeItemUse：TRY_WITH_EMPTY_HAND 且空手也无效果时返回 not interactable")
	void emptyHandFallbackDoesNothing() {
		assertEquals(BlockInteractionLogic.NOT_INTERACTABLE,
				BlockInteractionLogic.describeItemUse("minecraft:stick", "minecraft:stone",
						BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND,
						BlockInteractionLogic.Outcome.NO_EFFECT));
	}

	@Test
	@DisplayName("describeItemUse：TRY_WITH_EMPTY_HAND 且空手被明确拒绝时返回 interaction failed")
	void emptyHandFallbackFails() {
		assertEquals(BlockInteractionLogic.INTERACTION_FAILED,
				BlockInteractionLogic.describeItemUse("minecraft:stick", "minecraft:stone",
						BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND,
						BlockInteractionLogic.Outcome.FAILURE));
	}

	@Test
	@DisplayName("describeBlockUse：成功 → interacted，失败 → interaction failed，无效果 → not interactable")
	void blockUseOutcomes() {
		assertEquals(BlockInteractionLogic.INTERACTED,
				BlockInteractionLogic.describeBlockUse(BlockInteractionLogic.Outcome.SUCCESS));
		assertEquals(BlockInteractionLogic.INTERACTION_FAILED,
				BlockInteractionLogic.describeBlockUse(BlockInteractionLogic.Outcome.FAILURE));
		assertEquals(BlockInteractionLogic.NOT_INTERACTABLE,
				BlockInteractionLogic.describeBlockUse(BlockInteractionLogic.Outcome.NO_EFFECT));
		assertEquals(BlockInteractionLogic.NOT_INTERACTABLE,
				BlockInteractionLogic.describeBlockUse(BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND));
	}

	@Test
	@DisplayName("契约常量：需玩家身体的文本与 PRD #15 的 not interactable")
	void contractConstants() {
		assertEquals("interaction requires a player body", BlockInteractionLogic.REQUIRES_PLAYER);
		assertEquals("not interactable", BlockInteractionLogic.NOT_INTERACTABLE);
		assertEquals("no effect", BlockInteractionLogic.NO_EFFECT);
		assertEquals("interaction failed", BlockInteractionLogic.INTERACTION_FAILED);
		assertEquals("interacted", BlockInteractionLogic.INTERACTED);
	}
}
