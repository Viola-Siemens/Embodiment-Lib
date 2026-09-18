package com.hexagram2021.embodimentlib.api;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * 模型提供方 Profile（PRD §4.3）：protocol + base_url + api_key + model_name。
 * <p>
 * 不可变值对象；构造时校验，非法字段抛 {@link IllegalArgumentException}
 * （由配置层 {@code HostConfig#resolveProfile} 捕获并回退默认 profile）。
 *
 * @author liudongyu
 */
public record AgentProfile(String protocol, String baseUrl, @Nullable String apiKey, String modelName) {
	/** OpenAI 兼容协议标识（含 Ollama / LM Studio）。 */
	public static final String PROTOCOL_OPENAI = "openai";
	/** Anthropic 协议标识。 */
	public static final String PROTOCOL_ANTHROPIC = "anthropic";

	/**
	 * 紧凑构造器：校验字段合法性。
	 *
	 * @param protocol 协议名
	 * @param baseUrl 请求地址
	 * @param apiKey api key，可以为空
	 * @param modelName 模型名称
	 *
	 * @throws IllegalArgumentException protocol 为空/未知、base_url 为空或 model_name 为空
	 */
	@SuppressWarnings("java:S6207")
	@Contract("null, _, _, _ -> fail; _, null, _, _ -> fail; _, _, _, null -> fail")
	public AgentProfile(@Nullable String protocol, @Nullable String baseUrl, @Nullable String apiKey, @Nullable String modelName) {
		if (protocol == null || protocol.isBlank()) {
			throw new IllegalArgumentException("protocol must not be blank");
		}
		if (!PROTOCOL_OPENAI.equals(protocol) && !PROTOCOL_ANTHROPIC.equals(protocol)) {
			throw new IllegalArgumentException("unknown protocol: '" + protocol + "' (expected 'openai' or 'anthropic')");
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("base_url must not be blank");
		}
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalArgumentException("model_name must not be blank");
		}
		this.protocol = protocol;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.modelName = modelName;
	}

	/**
	 * 是否为 OpenAI 兼容协议。
	 *
	 * @return {@code protocol == "openai"}
	 */
	public boolean isOpenAI() {
		return PROTOCOL_OPENAI.equals(this.protocol);
	}

	/**
	 * 是否为 Anthropic 协议。
	 *
	 * @return {@code protocol == "anthropic"}
	 */
	public boolean isAnthropic() {
		return PROTOCOL_ANTHROPIC.equals(this.protocol);
	}
}
