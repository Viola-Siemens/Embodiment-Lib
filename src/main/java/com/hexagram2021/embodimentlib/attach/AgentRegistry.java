package com.hexagram2021.embodimentlib.attach;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行期智能体注册表（PLAN WP-2 ②）：按 {@code session-id} 维度管理
 * {@link RegistryEntry} 的生命周期。
 * <p>
 * <b>双端隔离（PRD §4.1.1 / §6.3 硬约束）</b>：SERVER 与 CLIENT <b>各持一份实例</b>
 * （{@link #server()} / {@link #client()}），二者永不共享条目。智能体本体、
 * 配置、会话目录、API key 都因此天然隔离。
 * <p>
 * <b>并发</b>：条目表使用 {@link ConcurrentHashMap}。注册/注销发生在游戏线程
 * （实体加入/离开世界），查询可能发生在命令线程或推理线程（IO 池）。
 * 需要注意的是，<b>「查重 + 替换 + 关闭旧句柄」是复合操作</b>，
 * 这里通过 {@link ConcurrentHashMap#compute} 的原子性保证不会出现
 * 「两个线程都认为自己替换成功、旧句柄被关闭两次/漏关一次」。
 *
 * @author liudongyu
 */
public final class AgentRegistry {
	/** 服务端注册表实例：集成服务器与专用服务器共用。 */
	private static final AgentRegistry SERVER = new AgentRegistry(AgentHostSide.SERVER);
	/** 客户端注册表实例：仅本玩家进程可见。 */
	private static final AgentRegistry CLIENT = new AgentRegistry(AgentHostSide.CLIENT);

	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.registry");

	private final AgentHostSide side;
	private final Map<String, RegistryEntry> bySession = Maps.newConcurrentMap();

	private AgentRegistry(AgentHostSide side) {
		this.side = side;
	}

	/**
	 * 创建独立实例（仅供单测使用）。
	 * <p>
	 * 生产代码一律走 {@link #server()} / {@link #client()} 两个单例：注册表是
	 * 「一个宿主侧一份」的全局设施，多实例会让「实体在这份表里、命令查那份表」
	 * 的错配成为可能。单测需要隔离的实例才能真正验证替换/注销行为，
	 * 否则用例之间会通过全局单例互相污染。
	 *
	 * @param side 宿主侧
	 * @return 全新的空注册表
	 */
	static AgentRegistry isolated(AgentHostSide side) {
		return new AgentRegistry(side);
	}

	/**
	 * 服务端注册表（唯一实例）。
	 *
	 * @return SERVER 侧注册表
	 */
	public static AgentRegistry server() {
		return SERVER;
	}

	/**
	 * 客户端注册表（唯一实例）。
	 *
	 * @return CLIENT 侧注册表
	 */
	public static AgentRegistry client() {
		return CLIENT;
	}

	/**
	 * 按宿主侧选择注册表。
	 *
	 * @param side 宿主侧
	 * @return 该侧对应的注册表实例
	 */
	public static AgentRegistry forSide(AgentHostSide side) {
		return switch (Objects.requireNonNull(side, "side")) {
			case SERVER -> SERVER;
			case CLIENT -> CLIENT;
		};
	}

	/** @return 本注册表的宿主侧 */
	public AgentHostSide side() {
		return this.side;
	}

	/**
	 * 查询会话对应的条目。
	 *
	 * @param sessionId 会话身份
	 * @return 条目；不存在或 sessionId 为 null 时返回 {@code null}
	 */
	public @Nullable RegistryEntry get(@Nullable String sessionId) {
		return sessionId == null ? null : this.bySession.get(sessionId);
	}

	/**
	 * 查询条目，不存在时返回 {@code null}。
	 * <p>
	 * 与 {@link #get(String)} 完全等价，仅用于在调用点显式表达「可能不存在」的语义。
	 *
	 * @param sessionId 会话身份
	 * @return 条目或 {@code null}
	 */
	public @Nullable RegistryEntry find(@Nullable String sessionId) {
		return get(sessionId);
	}

	/** @return 本注册表当前是否为空 */
	public boolean isEmpty() {
		return this.bySession.isEmpty();
	}

	/** @return 当前条目数 */
	public int size() {
		return this.bySession.size();
	}

	/**
	 * 注册条目；同一 {@code session-id} 已存在时<b>关闭旧句柄</b>再替换（防泄漏）。
	 * <p>
	 * 关闭动作在 {@link ConcurrentHashMap#compute} 内完成，因此同一 session 的
	 * 并发注册会串行化并各自关闭它实际替换掉的那个句柄，不会漏关也不会重复关。
	 *
	 * @param entry 待注册条目
	 * @return 本次注册后的生效条目（即 {@code entry} 自身）
	 * @throws NullPointerException entry 为 null
	 * @throws IllegalArgumentException entry 的宿主侧与本注册表不一致
	 */
	public RegistryEntry register(RegistryEntry entry) {
		Objects.requireNonNull(entry, "entry");
		if (entry.side() != this.side) {
			// 跨端注册是隔离约束被破坏的信号，必须立刻失败而不是静默接受。
			throw new IllegalArgumentException(
				"cannot register " + entry.side() + " entry into the " + this.side + " registry: "
					+ entry.agentType() + "/" + entry.sessionId());
		}
		this.bySession.compute(entry.sessionId(), (sessionId, previous) -> {
			if (previous != null) {
				LOGGER.info("Replacing existing agent for session {} ({}); closing previous instance",
					sessionId, previous.agentType());
				closeQuietly(previous);
			}
			return entry;
		});
		LOGGER.debug("Registered agent {} for session {} on {}", entry.agentType(), entry.sessionId(), this.side);
		return entry;
	}

	/**
	 * 注销并关闭条目。
	 *
	 * @param sessionId 会话身份
	 * @return 被移除的条目；不存在时返回 {@code null}
	 */
	public @Nullable RegistryEntry unregister(@Nullable String sessionId) {
		if (sessionId == null) {
			return null;
		}
		RegistryEntry removed = this.bySession.remove(sessionId);
		if (removed == null) {
			return null;
		}
		closeQuietly(removed);
		LOGGER.debug("Unregistered agent {} for session {} on {}", removed.agentType(), sessionId, this.side);
		return removed;
	}

	/**
	 * 清空本注册表并关闭全部条目（用于服务器停止/客户端断开时的兜底清理）。
	 * <p>
	 * 逐个 remove 而非 {@code clear()}：必须保证每个条目都被关闭，
	 * 且期间新注册的条目不会被误关（{@code clear()} 会把并发注册的条目一起吞掉）。
	 */
	public void clear() {
		for (String sessionId : Lists.newArrayList(this.bySession.keySet())) {
			unregister(sessionId);
		}
	}

	/**
	 * 全部条目的快照。
	 *
	 * @return 不可变列表；为空时返回空列表
	 */
	public Collection<RegistryEntry> all() {
		return List.copyOf(this.bySession.values());
	}

	/**
	 * 关闭条目并吞掉异常。
	 * <p>
	 * 关闭失败不应阻断注册表的替换/注销流程——否则一个坏句柄会让整个 session
	 * 永远无法被替换或清理，泄漏面反而扩大。异常以 ERROR 记录，保证可观测。
	 */
	private static void closeQuietly(RegistryEntry entry) {
		try {
			entry.agent().close();
		} catch (RuntimeException ex) {
			// 不捕获 Error：OutOfMemoryError 等不应被库吞掉。
			LOGGER.error("Failed to close agent for session {}", entry.sessionId(), ex);
		}
	}
}
