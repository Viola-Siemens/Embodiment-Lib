package com.hexagram2021.embodimentlib.tool.perceive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResourceId} 的单测（PLAN WP-7，WP-6 #1 / WP-7 #5 共用的 id 校验规则）。
 * <p>
 * 覆盖：与原版 {@code Identifier} 一致的合法/非法字符集、缺省命名空间补全、
 * 以及本类<b>刻意比原版更严</b>的空路径判定（差异已在类 Javadoc 与 PLAN 偏差记录中写明）。
 */
class ResourceIdTest {
	@Test
	@DisplayName("合法形态：带命名空间、裸路径、下划线与斜杠路径都被接受")
	void acceptsWellFormedIds() {
		assertTrue(ResourceId.isValid("minecraft:iron_ore"));
		assertTrue(ResourceId.isValid("iron_ore"));
		assertTrue(ResourceId.isValid("tech:deep_ore.block-2"));
		assertTrue(ResourceId.isValid("minecraft:oak_door"));
		assertTrue(ResourceId.isValid("minecraft:block/stone_slab"));
		assertTrue(ResourceId.isValid("  minecraft:stone  "));
	}

	@Test
	@DisplayName("非法形态：空白/非法字符/多冒号/空路径/命名空间 .. 都被拒绝")
	void rejectsMalformedIds() {
		assertFalse(ResourceId.isValid(null));
		assertFalse(ResourceId.isValid(""));
		assertFalse(ResourceId.isValid("   "));
		assertFalse(ResourceId.isValid("minecraft:iron ore"));
		assertFalse(ResourceId.isValid("a:b:c"));
		assertFalse(ResourceId.isValid("..:stone"));
		// 刻意严于原版：原版 Identifier.isValidPath("") 为 true，会把 "minecraft:" 当作合法。
		assertFalse(ResourceId.isValid("minecraft:"));
		assertFalse(ResourceId.isValid(":"));
	}

	@Test
	@DisplayName("canonicalize：补齐缺省命名空间并统一小写，非法时返回 null")
	void canonicalizesNamespaceAndCase() {
		assertEquals("minecraft:iron_ore", ResourceId.canonicalize("iron_ore"));
		assertEquals("minecraft:iron_ore", ResourceId.canonicalize("IRON_ORE"));
		assertEquals("minecraft:stone", ResourceId.canonicalize(":stone"));
		assertEquals("tech:ore", ResourceId.canonicalize("tech:ore"));
		assertNull(ResourceId.canonicalize("a:b:c"));
		assertNull(ResourceId.canonicalize("  "));
	}

	@Test
	@DisplayName("pathOf / namespaceOf：裸路径按 minecraft 缺省")
	void splitsNamespaceAndPath() {
		assertEquals("iron_ore", ResourceId.pathOf("minecraft:iron_ore"));
		assertEquals("iron_ore", ResourceId.pathOf("iron_ore"));
		assertEquals("deep_ore", ResourceId.pathOf("tech:deep_ore"));
		assertEquals("minecraft", ResourceId.namespaceOf("iron_ore"));
		assertEquals("tech", ResourceId.namespaceOf("tech:deep_ore"));
	}

	@Test
	@DisplayName("canonicalize 与 isValid 判定一致（放行的必然可被规约）")
	void isValidAgreesWithCanonicalize() {
		for (String candidate : new String[] {
				"minecraft:stone", "stone", ":stone", "minecraft:", "a:b:c", "", "..:stone", "UPPER"}) {
			assertEquals(ResourceId.isValid(candidate), ResourceId.canonicalize(candidate) != null,
					() -> "isValid 与 canonicalize 判定不一致: " + candidate);
		}
	}
}
