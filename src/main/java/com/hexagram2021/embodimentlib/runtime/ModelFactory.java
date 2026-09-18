package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.api.AgentProfile;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import java.util.Objects;

/**
 * 按 {@link AgentProfile} 构造 AgentScope 模型客户端（PLAN WP-3 ①）。
 * <p>
 * <b>为什么需要这个端口</b>：{@code OpenAIChatModel} / {@code AnthropicChatModel} 的构造会创建
 * OkHttp 客户端与 HTTP 传输层（真实网络设施），单测中不可实例化也不应实例化。
 * 把「profile → Model」这一步抽成函数式端口后，循环测试可以注入桩模型，
 * 从而满足 PLAN §3.8「测试中不得发起真实 LLM 调用」。
 * <p>
 * <b>API 核对记录（agentscope-extensions-model 2.0.1 实源码）</b>：
 * <ul>
 *   <li>{@code OpenAIChatModel.builder().apiKey(...).baseUrl(...).modelName(...).stream(...).build()}；
 *       {@code build()} 仅强制要求 {@code modelName}；</li>
 *   <li>{@code AnthropicChatModel.builder().baseUrl(...).apiKey(...).modelName(...).stream(...).build()}；
 *       其紧凑构造器对 null {@code baseUrl}/{@code apiKey} 容忍，缺省由 SDK 自行解析环境变量。</li>
 * </ul>
 * 两者都<b>不做</b>「apiKey 非空」校验，因此本库必须在配置层就保证 key 的存在性检查
 * （PRD §4.3 的配置校验职责），否则会退化成一个语义含糊的 401。
 *
 * @author liudongyu
 */
@FunctionalInterface
public interface ModelFactory {
	/**
	 * 为指定 profile 构造模型客户端。
	 *
	 * @param profile 模型提供方 profile（protocol / baseUrl / apiKey / modelName）
	 * @return AgentScope 模型实例
	 */
	Model create(AgentProfile profile);

	/**
	 * 默认实现：按 protocol 分派到对应扩展模块的模型客户端。
	 * <p>
	 * <b>stream 选项</b>：统一关闭流式（{@code stream(false)}）。0.1 的 {@code reply()} 只消费
	 * 最终答案文本，流式增量对本库没有价值，反而会让 AgentScope 内部走不同的响应聚合路径。
	 * P1 若要做「边说边播」的体验，在此处改为 {@code true} 并配合流式事件处理。
	 *
	 * @return 生产用模型工厂
	 */
	static ModelFactory defaultFactory() {
		return profile -> {
			Objects.requireNonNull(profile, "profile");
			if (profile.isOpenAI()) {
				// OpenAI 兼容端点（含 Ollama / LM Studio / DeepSeek 等）统一走此路径。
				return OpenAIChatModel.builder()
					.apiKey(profile.apiKey())
					.baseUrl(profile.baseUrl())
					.modelName(profile.modelName())
					.stream(false)
					.build();
			}
			if (profile.isAnthropic()) {
				return AnthropicChatModel.builder()
					.apiKey(profile.apiKey())
					.baseUrl(profile.baseUrl())
					.modelName(profile.modelName())
					.stream(false)
					.build();
			}
			// AgentProfile 紧凑构造器已拒绝未知 protocol，走到这里说明二者失配。
			throw new IllegalArgumentException("unsupported protocol: '" + profile.protocol() + "'");
		};
	}
}
