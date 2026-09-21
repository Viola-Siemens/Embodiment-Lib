package com.hexagram2021.embodimentlib.tool.perceive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BlockStateLogic} 的单测（PLAN WP-6 #2）。
 * <p>
 * 覆盖：序列化（无属性/有属性/多属性）、世界界内判定、air/void 标记。
 */
class BlockStateLogicTest {
	@Test
	@DisplayName("serialize：无属性时仅返回 id")
	void serializeWithoutProperties() {
		assertEquals("minecraft:stone", BlockStateLogic.serialize("minecraft:stone", Map.of()));
	}

	@Test
	@DisplayName("serialize：单属性输出 id[key=value]")
	void serializeSingleProperty() {
		Map<String, String> props = new TreeMap<>();
		props.put("half", "lower");
		assertEquals("minecraft:oak_door[half=lower]", BlockStateLogic.serialize("minecraft:oak_door", props));
	}

	@Test
	@DisplayName("serialize：多属性按 key 升序输出，逗号分隔")
	void serializeMultiplePropertiesSorted() {
		Map<String, String> props = new TreeMap<>();
		props.put("facing", "east");
		props.put("half", "lower");
		props.put("waterlogged", "false");
		assertEquals(
				"minecraft:oak_door[facing=east,half=lower,waterlogged=false]",
				BlockStateLogic.serialize("minecraft:oak_door", props));
	}

	@Test
	@DisplayName("isInBuildHeight：含边界判定")
	void buildHeightBounds() {
		assertTrue(BlockStateLogic.isInBuildHeight(0, -64, 320));
		assertTrue(BlockStateLogic.isInBuildHeight(-64, -64, 320));
		assertTrue(BlockStateLogic.isInBuildHeight(320, -64, 320));
		assertFalse(BlockStateLogic.isInBuildHeight(-65, -64, 320));
		assertFalse(BlockStateLogic.isInBuildHeight(321, -64, 320));
	}

	@Test
	@DisplayName("describe：世界外返回 void")
	void describeOutOfWorld() {
		assertEquals(BlockStateLogic.VOID, BlockStateLogic.describe(false, false, "minecraft:stone", Map.of()));
	}

	@Test
	@DisplayName("describe：空气返回 air")
	void describeAir() {
		assertEquals(BlockStateLogic.AIR, BlockStateLogic.describe(true, true, "minecraft:stone", Map.of()));
	}

	@Test
	@DisplayName("describe：正常方块走序列化")
	void describeSerializesBlock() {
		Map<String, String> props = new TreeMap<>();
		props.put("facing", "north");
		assertEquals("minecraft:furnace[facing=north]",
				BlockStateLogic.describe(true, false, "minecraft:furnace", props));
	}
}