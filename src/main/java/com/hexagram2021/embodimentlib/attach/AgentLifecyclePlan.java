package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import org.jspecify.annotations.Nullable;

/**
 * 生命周期决策（PLAN WP-2 ③ 的纯逻辑部分）。
 * <p>
 * 从实体加入/离开世界的处理流程中剥离出来的<b>决策</b>，不含任何 Minecraft / FML 依赖，
 * 因此可以被 JUnit 单测穷举覆盖。{@link AgentLifecycle} 只负责把它接到事件总线上。
 * <p>
 * 三个事件被折叠为两类动作，因为「离开世界」与「实体卸下」在注册表看来是同一件事
 * （都要求注销并关闭句柄）：
 * <ul>
 *   <li>{@link Action#NONE}：无事可做（未附着实体、客户端侧、或本就无条目）；</li>
 *   <li>{@link Action#UNREGISTER}：注销并关闭该 session 的条目。</li>
 * </ul>
 * <b>注意本类不做「兜底注册」的决策</b>：向注册表写入条目需要构造 {@link EmbodiedAgentHandle}，
 * 而按 PLAN WP-2 ③ 的定稿，构造智能体是 addon（或 WP-10 门面）的职责，库不做隐式兜底注册。
 * 因此加入世界时唯一可能的动作就是 {@code NONE}，该方法的存在只为让
 * 「客户端侧永不触达服务端注册表」这一约束有明确的落点与被测试的载体。
 */
public final class AgentLifecyclePlan {
	/** 生命周期动作。 */
	public enum Action {
		/** 无事可做。 */
		NONE,
		/** 注销并关闭该 session 的条目。 */
		UNREGISTER
	}

	private AgentLifecyclePlan() {
	}

	/**
	 * 决策：实体加入世界时该做什么。
	 * <p>
	 * 恒为 {@link Action#NONE}。库不隐式注册智能体：注册需要模型 profile、系统提示词与
	 * 工具集，这些只有 addon 知道；代其决定会造成「用默认模型悄悄发起真实计费请求」。
	 * 客户端侧同样返回 {@code NONE}——客户端实体绝不会写入服务端注册表（PRD §4.1.1）。
	 *
	 * @param side 宿主侧
	 * @param attached 实体是否带完整附着（{@link AgentAttachment#isAttached}）
	 * @return {@link Action#NONE}
	 */
	@SuppressWarnings("unused")
	public static Action onJoin(AgentHostSide side, boolean attached) {
		return Action.NONE;
	}

	/**
	 * 决策：实体离开世界（或死亡）时该做什么。
	 * <p>
	 * 命中条件：服务端 + 该实体确实登记过条目。PRD §4.4 明确规定
	 * 「卸载的实体 agent 不运行，直接关闭」。
	 * <p>
	 * <b>不要求实体仍处于已附着状态</b>：addon 可能在实体离开前先清掉附着
	 * （例如把村民转回普通村民），此时条目必须照样被关闭，否则句柄泄漏。
	 * 判定依据是注册表里有没有条目，而不是实体当前长什么样。
	 * <p>
	 * 客户端侧不持有服务端条目，命令线程也无需查询，直接 {@link Action#NONE}。
	 *
	 * @param side 宿主侧
	 * @param hasEntry 该实体对应的 session 在注册表中是否已有条目
	 * @return 命中时为 {@link Action#UNREGISTER}，否则 {@link Action#NONE}
	 */
	public static Action onRemove(AgentHostSide side, boolean hasEntry) {
		if (side != AgentHostSide.SERVER) {
			return Action.NONE;
		}
		return hasEntry ? Action.UNREGISTER : Action.NONE;
	}

	/**
	 * 决策：实体被扫过时（每 tick 兜底）该做什么。
	 * <p>
	 * 与 {@link #onRemove} 对称：只清理「条目还在、实体已经不存在」的泄漏条目，
	 * 同样不做隐式注册。
	 *
	 * @param side 宿主侧
	 * @param hasEntry 注册表中是否已有该 session 的条目
	 * @param entityPresent 该 session 对应的实体是否仍在世界中
	 * @return 条目存在但实体已消失时为 {@link Action#UNREGISTER}，否则 {@link Action#NONE}
	 */
	public static Action onSweep(AgentHostSide side, boolean hasEntry, boolean entityPresent) {
		if (side != AgentHostSide.SERVER || !hasEntry) {
			return Action.NONE;
		}
		return entityPresent ? Action.NONE : Action.UNREGISTER;
	}

	/**
	 * 从实体读取 session-id，读取失败（未附着）时返回 {@code null}。
	 * <p>
	 * 供事件处理器安全地取键：未附着实体不应产生任何注册表查询。
	 *
	 * @param target 附着承载对象
	 * @return 非空 session-id；未附着时为 {@code null}
	 */
	public static @Nullable String sessionIdOf(AttachmentTarget target) {
		String sessionId = AgentAttachment.getSessionId(target);
		return sessionId.isBlank() ? null : sessionId;
	}
}
