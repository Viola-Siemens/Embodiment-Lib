package com.hexagram2021.embodimentlib.config;

import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个宿主侧（SERVER 或 CLIENT）的配置视图：
 * {@code [default]} profile + 按 agent-type 的路由表 + 命名 profile 表。
 * <p>
 * 每个宿主侧持有独立实例，互不共享任何值（PRD §4.1.1 / §6.4）。
 * <p>
 * <b>schema 说明（26.1 实测约束）</b>：NeoForge 26.1 的 ModConfigSpec 对「嵌套表
 * （Config 类型值）」在加载修正时会清空用户数据（FML 加载路径把嵌套表包装为
 * night-config 的 {@code SynchronizedConfig}，其内容被视为未声明键而被删除/替换）。
 * 因此 routing / profiles 不使用嵌套表，而采用可被 ModConfigSpec 无损往返的类型：
 * <ul>
 *   <li>{@code routing}：{@code List<String>}，每个元素为 {@code "agentType=profileName"}；</li>
 *   <li>{@code profiles}：{@code List<String>}，每个元素为一个 JSON 对象字符串，含
 *       {@code name / protocol / base_url / api_key / model_name} 字段（{@code name} 为路由引用键）。</li>
 * </ul>
 * 详见 PLAN §4 WP-1 与 §8 决策记录。
 */
@SuppressWarnings("java:S4968")
public final class HostConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.config");

	/** routing 中指向默认 profile 的约定值。 */
	public static final String DEFAULT_PROFILE_NAME = "default";
	/** routing 条目分隔符：{@code agentType=profileName}。 */
	public static final String ROUTING_SEPARATOR = "=";
	/** profiles JSON 中的 name 字段名（路由引用键）。 */
	public static final String PROFILE_NAME_FIELD = "name";

	/**
	 * 绝对兜底 profile：{@code [default]} 段字段非法时使用。
	 * <p>
	 * 与 {@link AgentProfileConfig} 的 spec 默认值保持一致，保证任何情况下都有可用 profile。
	 */
	private static final AgentProfile FALLBACK_PROFILE = new AgentProfile(
		AgentProfileConfig.DEFAULT_PROTOCOL, AgentProfileConfig.DEFAULT_BASE_URL,
		AgentProfileConfig.DEFAULT_API_KEY, AgentProfileConfig.DEFAULT_MODEL_NAME);

	private final AgentProfileConfig defaultProfile;
	private final ModConfigSpec.ConfigValue<List<? extends String>> routing;
	private final ModConfigSpec.ConfigValue<List<? extends String>> profiles;

	private HostConfig(ModConfigSpec.Builder builder) {
		this.defaultProfile = new AgentProfileConfig(builder, "default");
		this.routing = builder
			.comment("Routing entries: each element is \"agentType=profileName\".",
				"Absent entries or the special value \"default\" fall back to the [default] profile.")
			.defineListAllowEmpty("routing", List.of(), () -> "agent_type=profile_name", String.class::isInstance);
		this.profiles = builder
			.comment("Named profiles: each element is a JSON object string with fields",
				"name, protocol, base_url, api_key, model_name. \"name\" is the key referenced by routing.",
				"Example: {\"name\":\"quest_giver\",\"protocol\":\"anthropic\",\"base_url\":\"https://api.anthropic.com\",\"api_key\":\"\",\"model_name\":\"claude-sonnet-4-5\"}")
			.defineListAllowEmpty("profiles", List.of(), () -> "{\"name\":\"example\",\"protocol\":\"openai\",\"base_url\":\"https://api.openai.com/v1\",\"api_key\":\"\",\"model_name\":\"gpt-5.5\"}", String.class::isInstance);
	}

	static HostConfig create(ModConfigSpec.Builder builder) {
		return new HostConfig(builder);
	}

	/**
	 * 默认 profile（未命中路由时的兜底）。
	 * <p>
	 * {@code [default]} 段的字段合法性由 ModConfigSpec 校验器保证，正常路径下不会抛异常；
	 * 但为使「配置层永不因非法值中断」这一契约在代码层面显式成立（未来改动校验器、
	 * 或 spec 未生效的调用路径），此处仍捕获 {@link IllegalArgumentException} 并回退到
	 * {@link #FALLBACK_PROFILE}，同时记录 WARN。
	 *
	 * @return {@code [default]} 段校验后的 profile（永不返回 null）
	 */
	public AgentProfile defaultProfile() {
		try {
			return this.defaultProfile.profile();
		} catch (IllegalArgumentException ex) {
			LOGGER.warn("Invalid [default] profile ({}); falling back to built-in defaults", ex.getMessage());
			return FALLBACK_PROFILE;
		}
	}

	/**
	 * 解析 agent-type 应使用的 profile（PRD §4.3）。
	 * <p>
	 * 命中 {@code routing} 且指向已定义命名 profile 时返回该 profile；
	 * 否则（未配置、指向 "default"、指向不存在的 profile、或 profile 字段非法）回退默认 profile，
	 * 并记录 WARN 日志。永不返回 null。
	 *
	 * @param agentType 智能体类型（如 "village_npc"）
	 * @return 该 agent-type 应使用的 profile；任何异常路径均回退默认 profile
	 */
	public AgentProfile resolveProfile(String agentType) {
		String profileName = routingMap().get(agentType);
		if (profileName == null || profileName.isBlank() || DEFAULT_PROFILE_NAME.equals(profileName)) {
			return this.defaultProfile();
		}
		AgentProfile profile = profilesMap().get(profileName);
		if (profile == null) {
			LOGGER.warn("routing[{}] references undefined profile '{}'; falling back to [default]", agentType, profileName);
			return this.defaultProfile();
		}
		return profile;
	}

	/** 配置加载完成后校验路由引用与 JSON 可解析性；仅记录日志，不抛异常（解析期已安全回退）。 */
	public void validateRouting() {
		Map<String, String> routes = routingMap();
		Map<String, AgentProfile> defined = profilesMap();
		for (Map.Entry<String, String> entry : routes.entrySet()) {
			String profileName = entry.getValue();
			if (profileName != null && !profileName.isBlank()
				&& !DEFAULT_PROFILE_NAME.equals(profileName) && !defined.containsKey(profileName)) {
				LOGGER.warn("routing[{}] -> undefined profile '{}'", entry.getKey(), profileName);
			}
		}
	}

	/** 归一化 routing 为 {@code Map<agentType, profileName>}；畸形条目 WARN 并跳过。 */
	private Map<String, String> routingMap() {
		return parseRouting(this.routing.get());
	}

	/** 归一化 profiles 为 {@code Map<profileName, AgentProfile>}；解析失败条目 WARN 并跳过。 */
	private Map<String, AgentProfile> profilesMap() {
		Map<String, AgentProfile> result = new LinkedHashMap<>();
		for (String json : this.profiles.get()) {
			try {
				NamedProfile named = parseProfile(json);
				result.put(named.name(), named.profile());
			} catch (IllegalArgumentException ex) {
				LOGGER.warn("Invalid profile entry ignored: {}", ex.getMessage());
			}
		}
		return result;
	}

	/**
	 * 解析 routing 条目列表（{@code "agentType=profileName"}）为映射。
	 * 纯静态、无 IO，便于单元测试。
	 *
	 * @param entries routing 配置值
	 * @return 归一化后的 {@code agentType -> profileName} 映射（保持顺序，不变量：无空白键/值）
	 */
	static Map<String, String> parseRouting(List<? extends String> entries) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String entry : entries) {
			if (entry == null || entry.isBlank()) {
				LOGGER.warn("Blank routing entry ignored");
				continue;
			}
			int separator = entry.indexOf(ROUTING_SEPARATOR);
			if (separator <= 0 || separator == entry.length() - 1) {
				LOGGER.warn("Malformed routing entry '{}' (expected \"agentType=profileName\"); ignored", entry);
				continue;
			}
			String agentType = entry.substring(0, separator);
			String profileName = entry.substring(separator + 1);
			if (agentType.isBlank() || profileName.isBlank()) {
				LOGGER.warn("Malformed routing entry '{}' (blank agentType or profileName); ignored", entry);
				continue;
			}
			result.put(agentType, profileName);
		}
		return result;
	}

	/**
	 * 解析一个 profile JSON 对象字符串。
	 * <p>
	 * 字段：{@code name}（路由引用键，必填）、{@code protocol}、{@code base_url}、
	 * {@code api_key}、{@code model_name}（后四者与 {@code [default]} 同构，构造时校验）。
	 * 缺失的字段按空串处理，由 {@link AgentProfile} 构造校验兜底。
	 *
	 * @param json profile JSON 对象字符串
	 * @return 命名 profile
	 * @throws IllegalArgumentException JSON 非法、缺 name 或 profile 字段非法（消息带原因）
	 */
	static NamedProfile parseProfile(String json) {
		if (json == null || json.isBlank()) {
			throw new IllegalArgumentException("blank profile JSON");
		}
		final JsonObject object;
		try {
			object = JsonParser.parseString(json).getAsJsonObject();
		} catch (JsonSyntaxException | IllegalStateException ex) {
			throw new IllegalArgumentException("profile is not a JSON object: " + ex.getMessage());
		}
		String name = stringField(object, PROFILE_NAME_FIELD);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("profile JSON missing required string field '" + PROFILE_NAME_FIELD + "': " + json);
		}
		try {
			AgentProfile profile = new AgentProfile(
				stringField(object, "protocol"), stringField(object, "base_url"),
				stringField(object, "api_key"), stringField(object, "model_name"));
			return new NamedProfile(name, profile);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("profile '" + name + "' is invalid: " + ex.getMessage());
		}
	}

	private static String stringField(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
	}

	/** 命名 profile：{@code name} 为路由引用键，{@code profile} 为校验后的值对象。 */
	public record NamedProfile(String name, AgentProfile profile) {
	}
}
