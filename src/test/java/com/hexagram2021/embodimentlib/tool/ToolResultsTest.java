package com.hexagram2021.embodimentlib.tool;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolResults} 的单测：工具契约中与实体无关的全部纯逻辑。
 * <p>
 * 覆盖 v0.1.0 PRD §4.5 的两条硬约束（观测永不为空、参数缺失不抛异常）与 schema 构造。
 */
class ToolResultsTest {
	@Nested
	@DisplayName("输入规约")
	class InputNormalization {
		@Test
		@DisplayName("null 输入规约为空集，供无参数工具使用")
		void nullInputBecomesEmptyMap() {
			assertTrue(ToolResults.normalizeInput(null).isEmpty());
		}

		@Test
		@DisplayName("非 null 输入原样返回，不做拷贝")
		void nonNullInputPassedThrough() {
			Map<String, Object> input = Map.of("radius", 8);
			assertSame(input, ToolResults.normalizeInput(input));
		}
	}

	@Nested
	@DisplayName("observation 规约（永不为空白）")
	class ObservationNormalization {
		@Test
		@DisplayName("null 转固定文本，而不是让模型看到空回复")
		void nullBecomesEmptyObservation() {
			assertEquals(ToolResults.EMPTY_OBSERVATION, ToolResults.normalizeObservation(null));
		}

		@Test
		@DisplayName("纯空白（空格/制表/换行）同样转固定文本")
		void blankBecomesEmptyObservation() {
			assertEquals(ToolResults.EMPTY_OBSERVATION, ToolResults.normalizeObservation("   "));
			assertEquals(ToolResults.EMPTY_OBSERVATION, ToolResults.normalizeObservation("\t\n"));
			assertEquals(ToolResults.EMPTY_OBSERVATION, ToolResults.normalizeObservation(""));
		}

		@Test
		@DisplayName("正常文本原样保留，且不 trim 掉有意义的空白")
		void normalTextPreservedVerbatim() {
			// 保留原文很重要：observation 是回喂给模型的，格式（缩进/换行）可能承载结构信息。
			assertEquals("mined", ToolResults.normalizeObservation("mined"));
			assertEquals("line1\nline2", ToolResults.normalizeObservation("line1\nline2"));
			assertEquals(" padded ", ToolResults.normalizeObservation(" padded "));
		}
	}

	@Nested
	@DisplayName("参数解析：缺失/畸形一律回落默认值，绝不抛异常")
	class ParameterParsing {
		@Test
		@DisplayName("字符串参数：缺失或空白走默认值")
		void stringParamFallsBack() {
			Map<String, Object> input = Map.of("present", "value", "blank", "   ");
			assertEquals("value", ToolResults.stringParam(input, "present", "fallback"));
			assertEquals("fallback", ToolResults.stringParam(input, "missing", "fallback"));
			assertEquals("fallback", ToolResults.stringParam(input, "blank", "fallback"));
		}

		@Test
		@DisplayName("字符串参数：非字符串值转成字符串而非报错")
		void stringParamCoercesNonString() {
			Map<String, Object> input = Map.of("number", 42);
			assertEquals("42", ToolResults.stringParam(input, "number", "fallback"));
		}

		@Test
		@DisplayName("整数参数：接受数字与数字字符串两种形态")
		void intParamAcceptsBothForms() {
			Map<String, Object> input = Map.of("num", 8, "str", "12", "padded", " 5 ");
			assertEquals(8, ToolResults.intParam(input, "num", -1));
			assertEquals(12, ToolResults.intParam(input, "str", -1));
			assertEquals(5, ToolResults.intParam(input, "padded", -1));
		}

		@Test
		@DisplayName("整数参数：非数字文本回落默认值而不是抛 NumberFormatException")
		void intParamFallsBackOnGarbage() {
			Map<String, Object> input = Map.of("bad", "eight", "missing", "x");
			assertEquals(-1, ToolResults.intParam(input, "bad", -1));
			assertEquals(-1, ToolResults.intParam(input, "missing", -1));
		}

		@Test
		@DisplayName("浮点参数：接受数字与数字字符串，畸形回落默认值")
		void doubleParamBehavior() {
			Map<String, Object> input = Map.of("num", 1.5, "str", "2.25", "bad", "abc");
			assertEquals(1.5, ToolResults.doubleParam(input, "num", -1.0), 1e-9);
			assertEquals(2.25, ToolResults.doubleParam(input, "str", -1.0), 1e-9);
			assertEquals(-1.0, ToolResults.doubleParam(input, "bad", -1.0), 1e-9);
		}

		@Test
		@DisplayName("布尔参数：只认 true/false（含字符串形态），其余回落默认值")
		void booleanParamIsStrict() {
			Map<String, Object> input = Map.of(
				"t", true, "f", false, "ts", "TRUE", "fs", " false ", "yes", "yes");
			assertTrue(ToolResults.booleanParam(input, "t", false));
			assertFalse(ToolResults.booleanParam(input, "f", true));
			assertTrue(ToolResults.booleanParam(input, "ts", false));
			assertFalse(ToolResults.booleanParam(input, "fs", true));
			// "yes" 必须回落默认值：若用 Boolean.parseBoolean 会静默得到 false，
			// 把模型明确的肯定意图翻转成否定。
			assertTrue(ToolResults.booleanParam(input, "yes", true));
		}

		@Test
		@DisplayName("必填参数：缺失或空白返回 null（由工具自行产出可读失败文本）")
		void requiredParamReturnsNullWhenAbsent() {
			Map<String, Object> input = Map.of("ok", "v", "blank", "  ");
			assertEquals("v", ToolResults.requiredParam(input, "ok"));
			assertNull(ToolResults.requiredParam(input, "blank"));
			assertNull(ToolResults.requiredParam(input, "missing"));
		}
	}

	@Nested
	@DisplayName("JSON Schema 构造")
	class SchemaBuilding {
		@Test
		@DisplayName("object schema 带 type/properties/required 三键")
		void objectSchemaShape() {
			Map<String, Object> schema = ToolResults.objectSchema(
				Map.of("radius", ToolResults.prop("integer", "search radius")),
				List.of("radius"));
			assertEquals("object", schema.get("type"));
			assertEquals(List.of("radius"), schema.get("required"));
			assertTrue(schema.containsKey("properties"));
		}

		@Test
		@DisplayName("required 列表被防御性拷贝：改动入参不影响已建 schema")
		void objectSchemaCopiesRequired() {
			List<String> required = Lists.newArrayList(List.of("a"));
			Map<String, Object> schema = ToolResults.objectSchema(Map.of(), required);
			required.add("b");
			assertEquals(List.of("a"), schema.get("required"));
		}

		@Test
		@DisplayName("rangedProp 带上下界，用于约束模型别编造离谱数值")
		void rangedPropIncludesBounds() {
			Map<String, Object> prop = ToolResults.rangedProp("integer", "radius", 1, 32);
			assertEquals(1, prop.get("minimum"));
			assertEquals(32, prop.get("maximum"));
			assertEquals("integer", prop.get("type"));
		}

		@Test
		@DisplayName("enumProp 带允许取值，且做拷贝")
		void enumPropIncludesAllowedValues() {
			List<String> allowed = Lists.newArrayList(List.of("main_hand"));
			Map<String, Object> prop = ToolResults.enumProp("which hand", allowed);
			allowed.add("off_hand");
			assertEquals(List.of("main_hand"), prop.get("enum"));
			assertEquals("string", prop.get("type"));
		}

		@Test
		@DisplayName("prop 同时给出 type 与 description")
		void propShape() {
			Map<String, Object> prop = ToolResults.prop("string", "block id");
			assertEquals("string", prop.get("type"));
			assertEquals("block id", prop.get("description"));
		}
	}
}
