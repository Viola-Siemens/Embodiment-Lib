package com.hexagram2021.embodimentlib.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BlockCoordinates} 的单测（PLAN WP-7 #12/#14/#15/#20/#21 共用的坐标解析）。
 * <p>
 * 覆盖：数字与数字字符串、数组长度、小数与文本的拒绝、按参数名生成的失败文本。
 */
class BlockCoordinatesTest {
	@Test
	@DisplayName("parse：长度 3 的数字数组被接受")
	void parsesNumericTriple() {
		BlockCoordinates coordinates = BlockCoordinates.parse(
				Map.of("pos", List.of(10, 64, -3)), BlockCoordinates.POS);
		assertNull(coordinates.error());
		assertEquals(10, coordinates.x());
		assertEquals(64, coordinates.y());
		assertEquals(-3, coordinates.z());
	}

	@Test
	@DisplayName("parse：数字字符串被接受（模型常把 12 写成 \"12\"）")
	void acceptsNumericStrings() {
		BlockCoordinates coordinates = BlockCoordinates.parse(
				Map.of("hit_pos", List.of("10", " 64 ", "-3")), BlockCoordinates.HIT_POS);
		assertNull(coordinates.error());
		assertEquals(10, coordinates.x());
		assertEquals(64, coordinates.y());
		assertEquals(-3, coordinates.z());
	}

	@Test
	@DisplayName("parse：拒绝长度不为 3 的数组与缺失参数")
	void rejectsWrongShape() {
		assertNotNull(BlockCoordinates.parse(Map.of(), BlockCoordinates.POS).error());
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of(1, 2)), BlockCoordinates.POS).error());
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of(1, 2, 3, 4)), BlockCoordinates.POS).error());
		assertNotNull(BlockCoordinates.parse(Map.of("pos", "1,2,3"), BlockCoordinates.POS).error());
	}

	@Test
	@DisplayName("parse：拒绝小数坐标（不静默截断，避免模型以为命中了别的位置）")
	void rejectsFractionalCoordinates() {
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of(1.5, 2, 3)), BlockCoordinates.POS).error());
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of("1.5", "2", "3")), BlockCoordinates.POS).error());
		// 整数值的浮点数仍然接受（模型可能给 10.0）。
		assertNull(BlockCoordinates.parse(Map.of("pos", List.of(10.0, 2, 3)), BlockCoordinates.POS).error());
	}

	@Test
	@DisplayName("parse：非数字元素被拒绝")
	void rejectsNonNumericElements() {
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of("north", 2, 3)), BlockCoordinates.POS).error());
		assertNotNull(BlockCoordinates.parse(Map.of("pos", List.of(true, 2, 3)), BlockCoordinates.POS).error());
	}

	@Test
	@DisplayName("失败文本按参数名生成，模型能分辨是哪个参数写错了")
	void failureTextNamesTheParameter() {
		assertEquals("invalid input: pos must be [x, y, z] of integers",
				BlockCoordinates.parse(Map.of(), BlockCoordinates.POS).error());
		assertEquals("invalid input: hit_pos must be [x, y, z] of integers",
				BlockCoordinates.parse(Map.of(), BlockCoordinates.HIT_POS).error());
	}

	@Test
	@DisplayName("契约常量：两个参数名与 PRD schema 一致")
	void contractConstants() {
		assertEquals("pos", BlockCoordinates.POS);
		assertEquals("hit_pos", BlockCoordinates.HIT_POS);
	}
}
