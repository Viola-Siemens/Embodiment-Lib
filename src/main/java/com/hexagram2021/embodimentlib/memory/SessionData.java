package com.hexagram2021.embodimentlib.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jspecify.annotations.Nullable;

/**
 * 一个会话的工作记忆与待办清单（PLAN WP-4 ②，<b>纯逻辑</b>：零 Minecraft / 零 NeoForge 依赖）。
 * <p>
 * 对应磁盘上的两个文件：
 * <ul>
 *   <li>{@code memory.json}：{@code {"key":"value"}} —— {@link #workingMemory()}；</li>
 *   <li>{@code todo.json}：{@code ["item", ...]} —— {@link #todo()}。</li>
 * </ul>
 * 会话历史（对话缓冲）<b>不在这里</b>：它由 AgentScope 的状态存储负责，
 * 本库只把那个存储指到会话目录（见 {@link SessionPaths} 的类 Javadoc）。
 *
 * <h2>写穿（PLAN WP-4 ③ 的「变更后立即落盘」）</h2>
 * 每个<b>真正改变了数据</b>的方法都会立刻调用 {@link SessionSink#save}。
 * 刻意不做「批量修改后再统一保存」的 API：那需要调用方记得保存，
 * 而忘记保存的后果（智能体突然失忆）极难与别的问题区分开。
 * 反过来，<b>没有改变数据</b>的调用（删除不存在的键、重复加入同一条待办）
 * <b>不会</b>触发写盘——写穿不等于「每次调用都写」。
 *
 * <h2>有序性</h2>
 * 工作记忆用 {@link LinkedHashMap}、待办用 {@link ArrayList}：
 * 两者的顺序对模型是可读信息（「先记 A 还是先记 B」），
 * 而且顺序稳定才能让同一状态每次序列化出逐字相同的文件。
 *
 * <h2>线程</h2>
 * 方法级 {@code synchronized}。工具调用跑在游戏线程，而 addon 可能在 IO 线程读写；
 * 这个对象很小、冲突概率低，用锁换「绝不读到半更新状态」是划算的。
 *
 * @author liudongyu
 */
public final class SessionData {
	private final String sessionId;
	private final SessionSink sink;
	private final Map<String, String> workingMemory = Maps.newLinkedHashMap();
	private final List<String> todo = Lists.newArrayList();

	/**
	 * 构造空的会话数据。
	 * <p>
	 * 包内可见：会话数据只能由 {@link SessionStore#load} 产出，
	 * 保证「一个 session-id 在进程内只有一份活对象」。
	 *
	 * @param sessionId 会话身份（已校验）
	 * @param sink 写穿目标
	 */
	SessionData(String sessionId, SessionSink sink) {
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.sink = Objects.requireNonNull(sink, "sink");
	}

	/**
	 * 从磁盘读到的内容恢复（不触发写穿）。
	 * <p>
	 * 包内可见且<b>不</b>persist：加载不是变更，若它触发写穿，
	 * 一个只读的 {@code load} 就会去改文件时间戳，还会在根目录不可用时刷日志。
	 *
	 * @param memory 工作记忆
	 * @param todoItems 待办清单
	 */
	synchronized void restore(Map<String, String> memory, List<String> todoItems) {
		this.workingMemory.clear();
		this.workingMemory.putAll(memory);
		this.todo.clear();
		this.todo.addAll(todoItems);
	}

	/** @return 会话身份 */
	public String sessionId() {
		return this.sessionId;
	}

	/**
	 * 工作记忆的只读快照（保持插入顺序）。
	 *
	 * @return 不可变映射
	 */
	public synchronized Map<String, String> workingMemory() {
		return Collections.unmodifiableMap(Maps.newLinkedHashMap(this.workingMemory));
	}

	/**
	 * 待办清单的只读快照（保持插入顺序）。
	 *
	 * @return 不可变列表
	 */
	public synchronized List<String> todo() {
		return List.copyOf(this.todo);
	}

	/**
	 * 读取一条工作记忆。
	 *
	 * @param key 键
	 * @return 值；不存在时为 null
	 */
	public synchronized @Nullable String get(String key) {
		return this.workingMemory.get(key);
	}

	/**
	 * 写入/覆盖一条工作记忆（写穿）。
	 *
	 * @param key 键
	 * @param value 值
	 */
	public synchronized void remember(String key, String value) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		String previous = this.workingMemory.put(key, value);
		if (!value.equals(previous)) {
			persist();
		}
	}

	/**
	 * 删除一条工作记忆（写穿；键不存在时不写盘）。
	 *
	 * @param key 键
	 * @return 确实删掉了返回 true
	 */
	public synchronized boolean forget(String key) {
		if (this.workingMemory.remove(key) == null) {
			return false;
		}
		persist();
		return true;
	}

	/**
	 * 追加一条待办（写穿；已存在则不重复添加、也不写盘）。
	 *
	 * @param item 待办内容
	 * @return 确实新增了返回 true
	 */
	public synchronized boolean addTodo(String item) {
		Objects.requireNonNull(item, "item");
		if (this.todo.contains(item)) {
			return false;
		}
		this.todo.add(item);
		persist();
		return true;
	}

	/**
	 * 完成（移除）一条待办（写穿）。
	 *
	 * @param item 待办内容
	 * @return 确实移除了返回 true
	 */
	public synchronized boolean completeTodo(String item) {
		if (!this.todo.remove(item)) {
			return false;
		}
		persist();
		return true;
	}

	/**
	 * 清空工作记忆与待办（写穿）。
	 *
	 * @return 之前非空（即确实发生了变更）返回 true
	 */
	public synchronized boolean clear() {
		if (this.workingMemory.isEmpty() && this.todo.isEmpty()) {
			return false;
		}
		this.workingMemory.clear();
		this.todo.clear();
		persist();
		return true;
	}

	/** @return 工作记忆与待办都为空返回 true */
	public synchronized boolean isEmpty() {
		return this.workingMemory.isEmpty() && this.todo.isEmpty();
	}

	/** @return 工作记忆条数 + 待办条数 */
	public synchronized int size() {
		return this.workingMemory.size() + this.todo.size();
	}

	private void persist() {
		this.sink.save(this.sessionId, this);
	}

	/**
	 * 只输出计数，<b>不输出内容</b>：工作记忆里可能被 addon 写入任何东西
	 * （包括不该进日志的上下文），计数足以定位问题。
	 */
	@Override
	public synchronized String toString() {
		return "SessionData[" + this.sessionId + " memory=" + this.workingMemory.size()
			+ " todo=" + this.todo.size() + "]";
	}
}
