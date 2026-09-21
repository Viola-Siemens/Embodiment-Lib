package com.hexagram2021.embodimentlib.memory;

/**
 * 会话数据的落盘端口（PLAN WP-4，<b>纯逻辑</b>：零 Minecraft / 零 NeoForge 依赖）。
 * <p>
 * {@link SessionData} 的写穿时机（「改一次就落一次盘」）是契约的一部分，
 * 但「盘在哪」不是。<b>把落盘抽成一个方法引用</b>换来了两件事：
 * <ul>
 *   <li>{@code SessionData} 可以在纯 JUnit 下完整覆盖——用一个记录调用的桩即可断言
 *       「哪一次修改触发了写穿、哪一次没有（例如删不存在的键）」，无需任何文件系统；</li>
 *   <li>「本端根目录尚不可用」这种情形不必在数据层写 {@code if (root == null)}：
 *       由实现方（{@link SessionStore}）决定降级为 no-op 还是真写。</li>
 * </ul>
 * 这与 WP-3 把线程派发抽成 {@code GameThreadExecutor} 端口、WP-5 把工具逻辑下沉到
 * {@code ToolResults} 是同一个手法：<b>把不可测的副作用挪到边界之外</b>。
 *
 * @author liudongyu
 */
@FunctionalInterface
public interface SessionSink {
	/**
	 * 空实现：什么都不写。
	 * <p>
	 * 用于「本端尚无可用根目录」等场景，使会话数据退化为纯内存态——
	 * 智能体照常运行，只是本轮不留下痕迹（并由 {@link SessionStore} 记一条告警）。
	 */
	SessionSink NONE = (_, _) -> {
	};

	/**
	 * 持久化一份会话数据。
	 *
	 * @param sessionId 会话身份
	 * @param data 待写入的数据（实现方只读其 {@code workingMemory()} / {@code todo()} 快照）
	 */
	void save(String sessionId, SessionData data);
}
