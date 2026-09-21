package com.hexagram2021.embodimentlib.tool;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工具调用的纯逻辑：参数解析与 observation 规约（PLAN WP-5 ①/②的可测部分）。
 * <p>
 * <b>为什么把这些从 {@link EmbodiedToolBase} 里拆出来</b>：基类的方法要么需要真实
 * {@code LivingEntity}（{@code ctx()}），要么是 AgentScope 的 {@code Mono} 包装，
 * 两者都无法在纯 JUnit 下驱动——实测 26.1.2 中连
 * {@code net.minecraft.world.entity.animal.Pig} 都不在测试编译类路径上，
 * 构造一个 {@code LivingEntity} 需要 {@code EntityType} 与世界对象。
 * <p>
 * 而工具契约里<b>真正会出错、真正值得测</b>的部分——「模型少传参数怎么办」
 * 「工具返回空白怎么办」「observation 该长什么样」——全都是纯函数。
 * 把它们集中到本类，就能在没有游戏进程的情况下获得完整覆盖，
 * 基类退化为一层不含判断的转发。
 * <p>
 * 本类无状态、不可实例化、线程安全。
 *
 * @author liudongyu
 */
public final class ToolResults {
	/** 工具执行成功但没有内容时回喂给模型的文本。 */
	public static final String EMPTY_OBSERVATION = "tool returned no result";
	/** 绑定实体已死亡/卸载时的文本（PRD §4.5 约定的失败形态之一）。 */
	public static final String ENTITY_UNAVAILABLE_OBSERVATION = "entity unavailable";
	/** 无参数工具（如 {@code perceive.self_status}）的规范空参数集。 */
	private static final Map<String, Object> EMPTY_INPUT = Map.of();

	private ToolResults() {
	}

	/**
	 * 把可能为 null 的模型参数规约为可安全查询的 Map。
	 *
	 * @param input 模型参数；可为 null
	 * @return 非 null 的参数集（null 时为空集）
	 */
	public static Map<String, Object> normalizeInput(@Nullable Map<String, Object> input) {
		return input == null ? EMPTY_INPUT : input;
	}

	/**
	 * 把工具返回值规约为回喂模型的 observation 文本。
	 * <p>
	 * 契约（PRD §4.5）：<b>永不返回 null，永不为空白</b>。
	 * 空白观测会让模型以为「工具成功了但什么也没发生」，从而反复重试同一个调用，
	 * 因此统一替换为 {@link #EMPTY_OBSERVATION}。
	 *
	 * @param raw 工具原始返回值；可为 null
	 * @return 非空白的 observation 文本
	 */
	public static String normalizeObservation(@Nullable String raw) {
		if (raw == null) {
			return EMPTY_OBSERVATION;
		}
		String trimmed = raw.strip();
		return trimmed.isEmpty() ? EMPTY_OBSERVATION : raw;
	}

	/**
	 * 读取字符串参数，缺失/空白时返回默认值。
	 * <p>
	 * 模型经常漏传可选参数或给出空串，此时应走默认值而非报错——
	 * 一次参数缺失不该毁掉整轮推理。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 默认值
	 * @return 解析结果
	 */
	public static String stringParam(Map<String, Object> input, String key, String fallback) {
		Object value = input.get(key);
		if (value == null) {
			return fallback;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? fallback : text;
	}

	/**
	 * 读取整数参数，缺失/不可解析时返回默认值。
	 * <p>
	 * 兼容模型把数字写成字符串（{@code "5"}）的常见情形。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 默认值
	 * @return 解析结果
	 */
	public static int intParam(Map<String, Object> input, String key, int fallback) {
		Object value = input.get(key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.strip());
			} catch (NumberFormatException _) {
				// 模型给了非数字：退化为默认值，而不是抛异常中断整轮推理。
				return fallback;
			}
		}
		return fallback;
	}

	/**
	 * 读取浮点参数，缺失/不可解析时返回默认值。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 默认值
	 * @return 解析结果
	 */
	public static double doubleParam(Map<String, Object> input, String key, double fallback) {
		Object value = input.get(key);
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.strip());
			} catch (NumberFormatException _) {
				return fallback;
			}
		}
		return fallback;
	}

	/**
	 * 读取布尔参数，缺失时返回默认值。
	 * <p>
	 * 只有真正的布尔值与 {@code "true"}/{@code "false"} 被接受；
	 * 其它文本一律回落默认值，避免 {@code Boolean.parseBoolean("yes") == false}
	 * 这种「看似解析成功实则吞掉意图」的行为。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @param fallback 默认值
	 * @return 解析结果
	 */
	public static boolean booleanParam(Map<String, Object> input, String key, boolean fallback) {
		Object value = input.get(key);
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String text) {
			String lowered = text.strip().toLowerCase(Locale.ROOT);
			if ("true".equals(lowered)) {
				return true;
			}
			if ("false".equals(lowered)) {
				return false;
			}
		}
		return fallback;
	}

	/**
	 * 读取必填字符串参数。
	 *
	 * @param input 模型参数
	 * @param key 参数名
	 * @return 解析结果；缺失或空白时为 {@code null}
	 */
	public static @Nullable String requiredParam(Map<String, Object> input, String key) {
		Object value = input.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? null : text;
	}

	/**
	 * 构造「对象」类型的 JSON Schema。
	 * <p>
	 * 所有内置工具的参数根节点都是 object，集中在此避免 23 处重复写错键名。
	 *
	 * @param properties 属性定义
	 * @param required 必填属性名
	 * @return JSON Schema
	 */
	public static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
		return Map.of(
			"type", "object",
			"properties", properties,
			"required", List.copyOf(required));
	}

	/**
	 * 构造单个属性的 JSON Schema。
	 *
	 * @param type JSON 类型
	 * @param description 给 LLM 看的说明
	 * @return 属性 schema
	 */
	public static Map<String, Object> prop(String type, String description) {
		return Map.of("type", type, "description", description);
	}

	/**
	 * 构造带取值范围约束的数值属性 schema。
	 * <p>
	 * 给 LLM 明确上下界能显著降低它编造离谱参数（如半径 10000）的概率。
	 *
	 * @param type JSON 类型
	 * @param description 说明
	 * @param minimum 下界
	 * @param maximum 上界
	 * @return 属性 schema
	 */
	public static Map<String, Object> rangedProp(String type, String description, int minimum, int maximum) {
		return Map.of(
			"type", type,
			"description", description,
			"minimum", minimum,
			"maximum", maximum);
	}

	/**
	 * 构造带枚举约束的字符串属性 schema。
	 *
	 * @param description 说明
	 * @param allowed 允许取值
	 * @return 属性 schema
	 */
	public static Map<String, Object> enumProp(String description, List<String> allowed) {
		return Map.of(
			"type", "string",
			"description", description,
			"enum", List.copyOf(allowed));
	}
}