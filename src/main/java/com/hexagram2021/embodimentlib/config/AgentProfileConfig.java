package com.hexagram2021.embodimentlib.config;

import com.hexagram2021.embodimentlib.api.AgentProfile;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 一段 profile 的四个配置字段（ModConfigSpec 小节，对应 TOML 中的一个表，如 {@code [default]}）。
 * <p>
 * 写法参考 GirlfriendsCommonConfig：push / comment / define / pop。
 * 非法值由 ModConfigSpec 修正为默认值并在日志中给出警告。
 */
public final class AgentProfileConfig {
	/** 默认协议：OpenAI 兼容。 */
	public static final String DEFAULT_PROTOCOL = AgentProfile.PROTOCOL_OPENAI;
	/** 默认 Base URL：OpenAI 官方端点。 */
	public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
	/** 默认 API key：空串（本地端点如 Ollama 可无 key）。 */
	public static final String DEFAULT_API_KEY = "";
	/** 默认模型名。 */
	public static final String DEFAULT_MODEL_NAME = "gpt-4o-mini";

	private final ModConfigSpec.ConfigValue<String> protocol;
	private final ModConfigSpec.ConfigValue<String> baseUrl;
	private final ModConfigSpec.ConfigValue<String> apiKey;
	private final ModConfigSpec.ConfigValue<String> modelName;

	/**
	 * 在给定 builder 上声明一段 profile 的四个字段（push/comment/define/pop，Girlfriends 风格）。
	 * 非法值由 ModConfigSpec 修正为默认值并在日志中给出警告。
	 *
	 * @param builder 所在配置的 ModConfigSpec builder（本构造器会 push 并 pop 一个 {@code [section]} 小节）
	 * @param section 小节名（如 "default"；命名 profile 复用本类时传 profile 名）
	 */
	public AgentProfileConfig(ModConfigSpec.Builder builder, String section) {
		builder.push(section);
		this.protocol = builder
				.comment("Model API protocol: \"openai\" (OpenAI-compatible, also Ollama / LM Studio) or \"anthropic\".",
						"Invalid values are corrected to the default and a warning is logged.")
				.define(
						"protocol", DEFAULT_PROTOCOL,
						value -> value instanceof String s && (AgentProfile.PROTOCOL_OPENAI.equals(s) || AgentProfile.PROTOCOL_ANTHROPIC.equals(s))
				);
		this.baseUrl = builder
				.comment("Base URL of the provider endpoint (including version prefix if required).")
				.define("base_url", DEFAULT_BASE_URL, value -> value instanceof String s && !s.isBlank());
		this.apiKey = builder
				.comment("API key. Read only on the side that performs inference; never written to chat or packets.",
						"May be empty for local endpoints (e.g. Ollama).")
				.define("api_key", DEFAULT_API_KEY);
		this.modelName = builder
				.comment("Model name, e.g. \"gpt-4o-mini\" (OpenAI) or \"claude-sonnet-4-5\" (Anthropic).")
				.define("model_name", DEFAULT_MODEL_NAME, value -> value instanceof String s && !s.isBlank());
		builder.pop();
	}

	/**
	 * 读取当前配置值并构建不可变 profile（含字段校验）。
	 *
	 * @return 校验后的 profile；字段非法时由 {@link AgentProfile} 构造器抛 {@link IllegalArgumentException}
	 */
	public AgentProfile profile() {
		return new AgentProfile(this.protocol.get(), this.baseUrl.get(), this.apiKey.get(), this.modelName.get());
	}
}
