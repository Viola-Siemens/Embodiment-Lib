package com.hexagram2021.embodimentlib.attach;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.jspecify.annotations.Nullable;

/**
 * 注册表条目：一个 {@code (host-side, session-id)} 下的智能体运行对象集合
 * （PLAN WP-2 ② 的字段清单）。
 * <p>
 * 字段职责：
 * <ul>
 *   <li>{@code agentType} / {@code sessionId} / {@code side}：身份三元组，注册后不可变；</li>
 *   <li>{@code agent}：WP-3 的智能体句柄，注册表负责在替换/注销时关闭它；</li>
 *   <li>{@code toolkit}：工具集引用。WP-2 以 {@code Object} 占位，待 WP-5 的
 *       {@code Toolkit} 合并后收窄为具体类型（PLAN WP-5 §4 桩契约）；</li>
 *   <li>{@code state}：WP-3 维护的运行状态；</li>
 *   <li>{@code recentToolCalls}：容量固定为 {@link #RECENT_TOOL_CALL_LIMIT} 的双端队列，
 *       新的在前，供 WP-8 检查命令展示。</li>
 * </ul>
 * <p>
 * <b>线程模型</b>：状态与最近调用记录在推理线程（IO 池）与游戏线程之间共享，
 * 因此对可变字段的读写使用 {@code synchronized}。注意队列操作与
 * {@code state} 读写各自同步，不追求跨字段的原子快照——
 * WP-8 只读取展示用信息，允许「状态与记录相差一拍」。
 */
public final class RegistryEntry {
	/** 最近工具调用记录的保留条数。 */
	public static final int RECENT_TOOL_CALL_LIMIT = 8;

	private final String agentType;
	private final String sessionId;
	private final AgentHostSide side;
	private final EmbodiedAgentHandle agent;
	@Nullable
	private final Object toolkit;

	private final Deque<ToolCallRecord> recentToolCalls = new ArrayDeque<>(RECENT_TOOL_CALL_LIMIT);
	private AgentState state = AgentState.IDLE;

	/**
	 * 构造条目。
	 *
	 * @param agentType 智能体类型名（非空白）
	 * @param sessionId 会话身份（非空白）
	 * @param side 宿主侧
	 * @param agent 智能体句柄
	 * @param toolkit 工具集（可为 null：无工具智能体也是合法形态）
	 * @throws IllegalArgumentException agentType 或 sessionId 为空白
	 * @throws NullPointerException 其余必填参数为 null
	 */
	public RegistryEntry(String agentType, String sessionId, AgentHostSide side,
			EmbodiedAgentHandle agent, @Nullable Object toolkit) {
		if (agentType == null || agentType.isBlank()) {
			throw new IllegalArgumentException("agentType must not be blank");
		}
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("sessionId must not be blank");
		}
		this.agentType = agentType;
		this.sessionId = sessionId;
		this.side = Objects.requireNonNull(side, "side");
		this.agent = Objects.requireNonNull(agent, "agent");
		this.toolkit = toolkit;
	}

	/** @return 智能体类型名 */
	public String agentType() {
		return this.agentType;
	}

	/** @return 会话身份 */
	public String sessionId() {
		return this.sessionId;
	}

	/** @return 宿主侧 */
	public AgentHostSide side() {
		return this.side;
	}

	/** @return 智能体句柄 */
	public EmbodiedAgentHandle agent() {
		return this.agent;
	}

	/** @return 工具集引用；未提供时为 {@code null} */
	@Nullable
	public Object toolkit() {
		return this.toolkit;
	}

	/** @return 当前运行状态 */
	public synchronized AgentState state() {
		return this.state;
	}

	/**
	 * 更新运行状态（WP-3 调用）。
	 *
	 * @param state 新状态
	 */
	public synchronized void setState(AgentState state) {
		this.state = Objects.requireNonNull(state, "state");
	}

	/**
	 * 记录一次工具调用；超出 {@link #RECENT_TOOL_CALL_LIMIT} 时丢弃最旧的一条。
	 *
	 * @param record 工具调用记录
	 */
	public synchronized void recordToolCall(ToolCallRecord record) {
		Objects.requireNonNull(record, "record");
		this.recentToolCalls.addFirst(record);
		while (this.recentToolCalls.size() > RECENT_TOOL_CALL_LIMIT) {
			this.recentToolCalls.removeLast();
		}
	}

	/**
	 * 最近工具调用的快照（新的在前）。
	 *
	 * @return 不可变列表；无记录时为空列表
	 */
	public synchronized List<ToolCallRecord> recentToolCalls() {
		return List.copyOf(new ArrayList<>(this.recentToolCalls));
	}

	/** @return 供日志使用的单行描述（不含 api_key 等敏感字段） */
	@Override
	public String toString() {
		return "RegistryEntry[" + this.side + " " + this.agentType + "/" + this.sessionId
			+ " state=" + state() + "]";
	}
}
