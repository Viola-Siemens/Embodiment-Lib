package com.hexagram2021.embodimentlib.runtime;

import com.hexagram2021.embodimentlib.attach.AgentState;
import com.hexagram2021.embodimentlib.attach.EmbodiedAgentHandle;
import com.hexagram2021.embodimentlib.attach.ToolCallRecord;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 最小 {@link EmbodiedAgentHandle} 实现，用于在不构建真实 AgentScope 运行时的前提下
 * 测试注册表协作与关闭幂等（PLAN WP-3 验收：close 释放注册表项）。
 */
final class StubHandle implements EmbodiedAgentHandle {
	private final AtomicBoolean everClosed = new AtomicBoolean();
	/** 供测试观察：是否真的进入了关闭流程。 */
	final CountDownLatch closedLatch = new CountDownLatch(1);

	@Override
	public AgentState state() {
		return AgentState.IDLE;
	}

	@Override
	public List<ToolCallRecord> recentToolCalls() {
		return List.of();
	}

	@Override
	public void close() {
		// 模拟 AgentScope 的幂等关闭：重复调用不应有副作用。
		if (this.everClosed.compareAndSet(false, true)) {
			this.closedLatch.countDown();
		}
	}

	/** @return 是否已被关闭 */
	boolean wasClosed() {
		return this.everClosed.get();
	}
}
