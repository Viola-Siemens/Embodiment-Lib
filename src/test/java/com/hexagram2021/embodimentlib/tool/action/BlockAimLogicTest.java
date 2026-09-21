package com.hexagram2021.embodimentlib.tool.action;

import com.google.common.collect.Maps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BlockAimLogic} 的单测（PLAN WP-7 #12/#14 共用的瞄准参数解析）。
 * <p>
 * 覆盖：缺省朝向与手别、朝向名大小写与非法值、手别非法值、坐标错误透传。
 */
class BlockAimLogicTest {
	@Test
	@DisplayName("parseAim：完整参数被解析")
	void parsesFullAim() {
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(Map.of(
				"hit_pos", List.of(1, 2, 3), "face", "north", "hand", "off"));
		assertNull(aim.error());
		assertEquals(1, aim.x());
		assertEquals(2, aim.y());
		assertEquals(3, aim.z());
		assertEquals("north", aim.face());
		assertEquals("off", aim.hand());
	}

	@Test
	@DisplayName("parseAim：face 与 hand 缺省为 up / main")
	void defaultsFaceAndHand() {
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(Map.of("hit_pos", List.of(1, 2, 3)));
		assertNull(aim.error());
		assertEquals(BlockAimLogic.DEFAULT_FACE, aim.face());
		assertEquals(UseItemLogic.DEFAULT_HAND, aim.hand());
	}

	@Test
	@DisplayName("parseFace：大小写不敏感；六个朝向之外返回 null")
	void parseFaceRules() {
		assertEquals("up", BlockAimLogic.parseFace("UP"));
		assertEquals("down", BlockAimLogic.parseFace(" Down "));
		assertEquals("up", BlockAimLogic.parseFace(null));
		assertNull(BlockAimLogic.parseFace("sideways"));
		assertNull(BlockAimLogic.parseFace(""));
	}

	@Test
	@DisplayName("parseAim：非法 face 返回朝向错误文本")
	void invalidFace() {
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(Map.of("hit_pos", List.of(1, 2, 3), "face", "upwards"));
		assertEquals(BlockAimLogic.INVALID_FACE, aim.error());
	}

	@Test
	@DisplayName("parseAim：非法 hand 返回手别错误文本")
	void invalidHand() {
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(Map.of("hit_pos", List.of(1, 2, 3), "hand", "left"));
		assertEquals(BlockAimLogic.INVALID_HAND, aim.error());
	}

	@Test
	@DisplayName("parseAim：坐标错误原样透传，且优先于朝向/手别")
	void coordinateErrorTakesPrecedence() {
		Map<String, Object> input = Maps.newHashMap();
		input.put("face", "upwards");
		input.put("hand", "left");
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(input);
		assertEquals("invalid input: hit_pos must be [x, y, z] of integers", aim.error());
	}

	@Test
	@DisplayName("parseAim：坐标非法但朝向手别合法时仍报坐标错误")
	void missingCoordinates() {
		BlockAimLogic.Aim aim = BlockAimLogic.parseAim(Map.of("face", "up"));
		assertNotNull(aim.error());
	}

	@Test
	@DisplayName("契约常量：六个朝向与 Direction 序列化名一致，缺省为 up")
	void contractConstants() {
		assertEquals(List.of("down", "up", "north", "south", "east", "west"), BlockAimLogic.FACES);
		assertEquals("up", BlockAimLogic.DEFAULT_FACE);
	}
}
