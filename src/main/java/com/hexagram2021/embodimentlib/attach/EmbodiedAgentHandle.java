package com.hexagram2021.embodimentlib.attach;

import java.util.List;

/**
 * 注册表持有的智能体句柄（PLAN WP-2 ②「{@code EmbodiedAgent}（WP-3 类型，未合并用桩）」）。
 * <p>
 * WP-3 的 {@code EmbodiedAgent} 是实现本接口的真实类型。抽成接口而非直接依赖具体类，
 * 是为了让 WP-2 的注册表生命周期（register / 重复 register / unregister / close）
 * 能在没有 AgentScope 运行时的前提下被 JUnit 单测完整覆盖——单测用记录型桩即可。
 * <p>
 * <b>生命周期合约</b>：{@link #close()} 必须幂等。注册表的「重复 register 关闭旧条目」与
 * 「unregister 关闭条目」两条路径都可能对同一个句柄调用 {@code close()}，
 * 非幂等实现会造成重复释放（重复关闭 HTTP 客户端、重复刷写会话文件等）。
 */
public interface EmbodiedAgentHandle extends AutoCloseable {
	/**
	 * 当前状态（由 WP-3 在推理/工具调用前后维护）。
	 *
	 * @return 当前状态；未开始任何推理时为 {@link AgentState#IDLE}
	 */
	AgentState state();

	/**
	 * 最近若干条工具调用记录（新的在前）。
	 *
	 * @return 不可变快照；无记录时返回空列表，<b>不得</b>返回 null
	 */
	List<ToolCallRecord> recentToolCalls();

	/**
	 * 关闭句柄并释放底层资源（模型客户端、会话刷写等）。
	 * <p>
	 * 必须幂等；实现<b>不得</b>抛出受检异常。
	 */
	@Override
	void close();
}
