package com.hexagram2021.embodimentlib.attach;

import com.google.common.collect.Lists;
import com.hexagram2021.embodimentlib.api.AgentHostSide;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WP-2 单测用的桩集合。
 * <p>
 * 为什么需要桩：{@code Entity} 的构造会连锁触发 {@code EntityType} 注册表查询、
 * {@code SynchedEntityData} 定义表构建、属性表解析与 NeoForge 事件总线投递，
 * 无法在纯 JUnit（无游戏进程）环境下实例化。库真正需要被验证的语义只有
 * 「读写字符串 / 空串算未附着 / 注册表生命周期 / 事件决策」，
 * 因此这里用内存对象精确复刻 {@link AttachmentTarget} 与 {@link EmbodiedAgentHandle} 的契约。
 * <p>
 * 这类桩不进入生产代码，只存在于 test 源集。
 *
 * @author liudongyu
 */
final class AgentTestSupport {
	private AgentTestSupport() {
	}

	/**
	 * 内存附着承载对象：复刻 {@code AttachmentHolder} 的语义。
	 * <ul>
	 *   <li>{@code null} 值一律被拒绝——与 {@code AttachmentHolder#setData} 的
	 *       {@code Objects.requireNonNull(data)} 一致；</li>
	 *   <li>{@link #getExistingAttachment} 在键缺失时返回 {@code null}，
	 *       <b>不</b>写入默认值（这是与 {@code getData} 的关键差异）。</li>
	 * </ul>
	 *
	 * @author liudongyu
	 */
	static final class FakeAttachmentTarget implements AttachmentTarget {
		private final Map<AttachmentType<?>, Object> attachments = new ConcurrentHashMap<>();

		@Override
		public <T> void setAttachment(AttachmentType<T> type, T value) {
			this.attachments.put(type, java.util.Objects.requireNonNull(value, "value"));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> @Nullable T getExistingAttachment(AttachmentType<T> type) {
			return (T) this.attachments.get(type);
		}

		/** 模拟 {@code IAttachmentHolder#removeData}，用于验证「清掉附着后条目仍须回收」。 */
		void removeAttachment(AttachmentType<?> type) {
			this.attachments.remove(type);
		}

		/** @return 是否已写入过任何附着（用于断言「纯读取不写入」） */
		boolean hasAnyAttachment() {
			return !this.attachments.isEmpty();
		}
	}

	/**
	 * 记录型智能体句柄：统计 {@code close()} 调用次数，并保留一份可读的关闭轨迹。
	 * <p>
	 * {@code closeCount} 是 WP-2 多条验收标准的核心断言依据
	 * （「重复 register 关闭旧条目」「unregister 关闭条目」「幂等」）。
	 *
	 * @author liudongyu
	 */
	static final class FakeAgent implements EmbodiedAgentHandle {
		private final String name;
		private final List<ToolCallRecord> records = Lists.newArrayList();
		private AgentState state = AgentState.IDLE;
		private int closeCount;

		FakeAgent(String name) {
			this.name = name;
		}

		@Override
		public AgentState state() {
			return this.state;
		}

		void setState(AgentState state) {
			this.state = state;
		}

		@Override
		public List<ToolCallRecord> recentToolCalls() {
			return List.copyOf(this.records);
		}

		@Override
		public void close() {
			// 幂等：重复关闭只累加计数，不抛异常。注册表的替换与注销两条路径
			// 都可能对同一句柄调用 close()，非幂等实现会造成重复释放。
			this.closeCount++;
		}

		int closeCount() {
			return this.closeCount;
		}

		@Override
		public String toString() {
			return "FakeAgent[" + this.name + "]";
		}
	}

	/** 构造一个已注册到指定端的条目。 */
	static RegistryEntry entry(String agentType, String sessionId, AgentHostSide side, EmbodiedAgentHandle agent) {
		return new RegistryEntry(agentType, sessionId, side, agent, null);
	}
}
