package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.EmbodimentLib;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 实体附着类型声明（PLAN WP-2 ①）。
 * <p>
 * 只注册两个短字符串附着（{@link #AGENT_TYPE} / {@link #SESSION_ID}）：
 * <ul>
 *   <li>{@code agent_type}：字符类型名（如 {@code "village_npc"}），用于路由 profile 与系统提示词；</li>
 *   <li>{@code session_id}：个体身份，0.1 默认取实体 UUID 字符串，用于会话/记忆隔离。</li>
 * </ul>
 * 重量级运行对象（{@code EmbodiedAgent} / {@code Toolkit}）<b>不</b>作为附着存储（PRD §6.4），
 * 改由 {@link AgentRegistry} 持有，避免实体 NBT 与网络包承载不可序列化的运行时对象。
 * <p>
 * <b>不持久化 / 不自动同步</b>：这里只调用 {@code AttachmentType.builder(...)}，
 * 既不 {@code serialize(...)} 也不 {@code sync(...)}。理由：
 * <ol>
 *   <li>环境重新加载（F3+T / 数据包重载）会重建实体，附着的权威来源是 addon 的构造期写入，
 *       库若持久化反而会与 addon 的写入产生双写冲突；</li>
 *   <li>PRD §4.1.1 / §6.3 要求 {@code agent_type} 与 {@code session_id}
 *       <b>永不下发客户端</b>，默认为不同步即天然满足该硬约束。</li>
 * </ol>
 * 需要跨存档保留的 addon 可在自己的附着类型上开启序列化，库不代为决定。
 * <p>
 * <b>注册时机</b>：{@link #REGISTER} 由主类构造函数执行
 * {@code REGISTER.register(modEventBus)}，即 {@code RegisterEvent} 阶段写入
 * {@code neoforge:attachment_types} 注册表；注册表尚未就绪前调用
 * {@code entity.setData(...)} 会被 {@code AttachmentHolder#validateAttachmentType}
 * 拒绝（开发环境下抛 {@link IllegalArgumentException}）。
 *
 * @author liudongyu
 */
public final class AttachmentTypes {
	/** 附着类型注册器（命名空间 {@code embodimentlib}）。 */
	public static final DeferredRegister<AttachmentType<?>> REGISTER =
		DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EmbodimentLib.MODID);

	/**
	 * 智能体类型附着键。
	 * <p>
	 * 默认值为{@link AgentAttachment#EMPTY empty}（空串），
	 * 与 {@link AgentAttachment#isAttached} 的「空串视为未附着」判定一致。
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> AGENT_TYPE =
		REGISTER.register("agent_type", () -> AttachmentType.builder(() -> "").build());

	/**
	 * 会话身份附着键。
	 * <p>
	 * 默认值为空串，语义同 {@link #AGENT_TYPE}。
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SESSION_ID =
		REGISTER.register("session_id", () -> AttachmentType.builder(() -> "").build());

	private AttachmentTypes() {
	}
}
