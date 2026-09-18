package com.hexagram2021.embodimentlib.runtime;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 脚本化的 {@link Model} 测试桩（PLAN WP-3 明确要求的 {@code FakeModel}）。
 * <p>
 * 它按预设脚本逐轮返回响应，使 ReAct 循环的测试完全脱离网络：
 * <ul>
 *   <li>{@link #replyWith(String)} 入队一条最终文本回答（循环应当结束）；</li>
 *   <li>{@link #callTool(String, Map)} 入队一次工具调用（循环应当继续）；</li>
 *   <li>{@link #failWith(Throwable)} 入队一次模型错误（用于断言错误传播路径）。</li>
 * </ul>
 * 同时记录每次被调用时收到的消息数，便于断言「观测确实被回喂给了模型」。
 */
final class FakeModel implements Model {
	private final Deque<Object> script = new ArrayDeque<>();
	private final List<Integer> observedMessageCounts = new CopyOnWriteArrayList<>();
	private final List<List<Msg>> receivedMessages = new CopyOnWriteArrayList<>();
	private final AtomicInteger streamCalls = new AtomicInteger();
	private final String modelName;

	FakeModel(String modelName) {
		this.modelName = Objects.requireNonNull(modelName, "modelName");
	}

	/** 入队一条最终文本回答。 */
	FakeModel replyWith(String text) {
		this.script.addLast(TextBlock.builder().text(text).build());
		return this;
	}

	/** 入队一次工具调用。 */
	FakeModel callTool(String toolName, Map<String, Object> args) {
		this.script.addLast(new ToolUseBlock("call-" + (this.script.size() + 1), toolName, args));
		return this;
	}

	/** 入队一次模型失败。 */
	FakeModel failWith(Throwable error) {
		this.script.addLast(error);
		return this;
	}

	@Override
	public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
		this.streamCalls.incrementAndGet();
		// 记录收到的消息数：断言「工具观测被回喂」的关键证据。
		this.observedMessageCounts.add(messages == null ? 0 : messages.size());
		this.receivedMessages.add(messages == null ? List.of() : List.copyOf(messages));

		if (this.script.isEmpty()) {
			return Flux.error(new IllegalStateException(
				"FakeModel script exhausted after " + this.streamCalls.get() + " call(s)"));
		}
		Object next = this.script.removeFirst();
		if (next instanceof Throwable error) {
			return Flux.error(error);
		}
		ContentBlock block = (ContentBlock) next;
		return Flux.just(ChatResponse.builder()
			.id("fake-" + this.streamCalls.get())
			.content(List.of(block))
			.finishReason("stop")
			.build());
	}

	@Override
	public String getModelName() {
		return this.modelName;
	}

	/** @return {@code stream} 被调用的次数（= 模型往返轮数） */
	int streamCallCount() {
		return this.streamCalls.get();
	}

	/** @return 每轮收到的消息条数，按调用顺序。 */
	List<Integer> observedMessageCounts() {
		return List.copyOf(this.observedMessageCounts);
	}

	/** @return 第 index 轮收到的消息列表。 */
	List<Msg> messagesAt(int index) {
		return this.receivedMessages.get(index);
	}

	/** @return 脚本中尚未消费的条目数。 */
	int remainingScriptSize() {
		return this.script.size();
	}
}
