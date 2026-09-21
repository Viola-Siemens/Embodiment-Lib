package com.hexagram2021.embodimentlib.tool.loco;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LookAtLogic} 的单测（PLAN WP-6 #10）。
 * <p>
 * 覆盖：pos / entity_id 二选一解析、冲突时 pos 优先、缺失与类型错误文本。
 */
class LookAtLogicTest {
	@Test
	@DisplayName("pos 合法时解析为目标点")
	void parsesPos() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of("pos", List.of(10.5, 64.0, -3.25)));
		assertNull(target.error());
		assertEquals(new Vec3(10.5, 64.0, -3.25), target.pos());
		assertNull(target.entityId());
	}

	@Test
	@DisplayName("pos 内的数字可写成字符串")
	void parsesPosAsStrings() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of("pos", List.of("10", "64", "-3")));
		assertNull(target.error());
		assertEquals(new Vec3(10.0, 64.0, -3.0), target.pos());
	}

	@Test
	@DisplayName("entity_id 合法时解析为实体引用")
	void parsesEntityId() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of("entity_id", "8f14e45f-ceea-4d2a-8f0a-2c5e9f0a1b3c"));
		assertNull(target.error());
		assertEquals("8f14e45f-ceea-4d2a-8f0a-2c5e9f0a1b3c", target.entityId());
		assertNull(target.pos());
	}

	@Test
	@DisplayName("两参都给时 pos 优先（位置比 UUID 解析更直接）")
	void posWinsOnConflict() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of(
				"pos", List.of(1.0, 2.0, 3.0),
				"entity_id", "8f14e45f-ceea-4d2a-8f0a-2c5e9f0a1b3c"));
		assertNull(target.error());
		assertNotNull(target.pos());
	}

	@Test
	@DisplayName("都缺返回 invalid need pos or entity_id")
	void missingBoth() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of());
		assertTrue(target.error().startsWith("invalid input"));
		assertTrue(target.error().contains("pos"));
		assertTrue(target.error().contains("entity_id"));
	}

	@Test
	@DisplayName("pos 长度/类型错误返回 invalid pos 文本")
	void malformedPos() {
		assertTrue(LookAtLogic.parse(Map.of("pos", List.of(1.0, 2.0))).error().contains("pos"));
		assertTrue(LookAtLogic.parse(Map.of("pos", "1,2,3")).error().contains("pos"));
		assertTrue(LookAtLogic.parse(Map.of("pos", List.of(1.0, "x", 3.0))).error().contains("pos"));
	}

	@Test
	@DisplayName("entity_id 空白返回 invalid")
	void blankEntityId() {
		LookAtLogic.Target target = LookAtLogic.parse(Map.of("entity_id", "  "));
		assertTrue(target.error().contains("entity_id"));
	}
}