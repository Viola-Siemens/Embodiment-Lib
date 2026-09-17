package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * {@code entity -> (agent_type, session_id)} 的解析入口（PLAN WP-2 ③）。
 * <p>
 * 全部方法只做「读附着 + 空串判定」，不做注册表副作用，因此可在无游戏进程的表单下用桩对象单测（见 {@code AgentAttachmentTest}）。
 * <p>
 * <b>为什么不用 {@code entity.getData(...)} 直接读</b>：{@code IAttachmentHolder#getData}
 * 在键缺失时会把默认值<b>写入</b>该实体。若库在「只是看一眼」的路径上调用它，每个被扫描到的实体都会凭空多出两个附着对象。这里统一走
 * {@link AttachmentTarget#getExistingAttachment} 的无副作用读取契约。
 *
 * @author liudongyu
 */
public final class AgentAttachment {
	/** 未附着时的空值。 */
	public static final String EMPTY = "";

	private AgentAttachment() {
	}

	/**
	 * 写入智能体类型。
	 *
	 * @param target 附着承载对象（通常为实体）
	 * @param agentType 智能体类型名（如 {@code "village_npc"}）
	 * @throws NullPointerException target 或 agentType 为 null
	 */
	public static void setType(AttachmentTarget target, String agentType) {
		target.setAttachment(AttachmentTypes.AGENT_TYPE.get(), Objects.requireNonNull(agentType, "agentType"));
	}

	/**
	 * 写入会话身份。
	 *
	 * @param target 附着承载对象（通常为实体）
	 * @param sessionId 会话身份（0.1 默认取实体 UUID 字符串）
	 * @throws NullPointerException target 或 sessionId 为 null
	 */
	public static void setSessionId(AttachmentTarget target, String sessionId) {
		target.setAttachment(AttachmentTypes.SESSION_ID.get(), Objects.requireNonNull(sessionId, "sessionId"));
	}

	/**
	 * 读取智能体类型（无副作用）。
	 *
	 * @param target 附着承载对象
	 * @return 已写入的类型；未附着或已写入空串时返回 {@link #EMPTY}（null 亦折叠为 {@link #EMPTY}）
	 */
	public static String getType(AttachmentTarget target) {
		return normalize(target.getExistingAttachment(AttachmentTypes.AGENT_TYPE.get()));
	}

	/**
	 * 读取会话身份（无副作用）。
	 *
	 * @param target 附着承载对象
	 * @return 已写入的会话身份；未附着或已写入空串时返回 {@link #EMPTY}（null 亦折叠为 {@link #EMPTY}）
	 */
	public static String getSessionId(AttachmentTarget target) {
		return normalize(target.getExistingAttachment(AttachmentTypes.SESSION_ID.get()));
	}

	/**
	 * 判定实体是否已附着（两个字符串均非空白才算附着）。
	 * <p>
	 * 只写其中一个字段的实体视为<b>未附着</b>：这通常意味着 addon 的初始化中途失败，
	 * 此时注册一个没有类型（无法路由 profile）或没有身份（无法隔离会话）的智能体都是错误的。
	 *
	 * @param target 附着承载对象
	 * @return 两个字符串均非空白时 {@code true}
	 */
	public static boolean isAttached(AttachmentTarget target) {
		return !getType(target).isBlank() && !getSessionId(target).isBlank();
	}

	/**
	 * 解析宿主侧。
	 * <p>
	 * SERVER / CLIENT 的判定依据是 {@code Level#isClientSide}，而非实体是否由玩家操控
	 * （PRD §4.1.1：集成服务器与客户端是两套互不共享的运行时；单人游戏里服务端实体
	 * 与客户端实体是两个不同对象，必须落到不同的注册表实例）。
	 *
	 * @param entity 待判定实体
	 * @return {@code entity.level().isClientSide()} 为真时 {@link AgentHostSide#CLIENT}，否则 {@link AgentHostSide#SERVER}
	 * @throws NullPointerException entity 为 null
	 */
	public static AgentHostSide sideOf(Entity entity) {
		Objects.requireNonNull(entity, "entity");
		return entity.level().isClientSide() ? AgentHostSide.CLIENT : AgentHostSide.SERVER;
	}

	/**
	 * 解析实体默认的会话身份。
	 * <p>
	 * PLAN §8 决策 5：session-id 默认值 = 实体 UUID 字符串（每个物理实体 1:1）。
	 *
	 * @param entity 目标实体
	 * @return {@code entity.getStringUUID()}
	 * @throws NullPointerException entity 为 null
	 */
	public static String defaultSessionId(Entity entity) {
		return Objects.requireNonNull(entity, "entity").getStringUUID();
	}

	/** 归一化读取结果：null 与空白统一折叠为 {@link #EMPTY}。 */
	private static String normalize(@Nullable String value) {
		return value == null ? EMPTY : value;
	}
}
