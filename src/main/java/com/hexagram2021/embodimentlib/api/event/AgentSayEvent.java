package com.hexagram2021.embodimentlib.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.Objects;

/**
 * 智能体「说了一句话」事件（PLAN WP-6 ③，PRD §4.5 #23 / §4.7）。
 * <p>
 * 由内置工具 {@code meta.say} 在执行时 post 到 {@code NeoForge.EVENT_BUS}，
 * addon 订阅后负责渲染（聊天 / 气泡 / 动作栏 / 语音）——<b>库本身不绘制任何东西</b>
 * （PRD：「The library itself draws nothing」）。
 * <p>
 * <b>agent-id = session-id</b>：0.1 中个体身份即会话身份（PRD §4.1.3），
 * 因此事件携带的 {@link #agentId()} 与 {@code ToolContext.sessionId()} 取值一致，
 * addon 可用它把「说话」关联回具体的智能体个体。
 *
 * <h2>为何在 {@code api} 包</h2>
 * 这是 addon 必须能直接 import 的公共 API（{@code api.event.AgentSayEvent}）。
 * 注意 {@code api} 父包约定「无 Minecraft 依赖」，但事件类必须携带执行实体——
 * 这个例外是刻意的，见 {@code api/event/package-info} 的说明。
 *
 * @author liudongyu
 */
public class AgentSayEvent extends Event {
	private final String agentId;
	private final LivingEntity entity;
	private final String text;

	/**
	 * 构造事件。
	 *
	 * @param agentId 智能体身份（0.1 = session-id）
	 * @param entity 执行 {@code meta.say} 的实体
	 * @param text 说的内容；空白文本由 {@link #validateText} 先拦截
	 * @throws NullPointerException 任一参数为 null
	 * @throws IllegalArgumentException text 为空白
	 */
	public AgentSayEvent(String agentId, LivingEntity entity, String text) {
		this.agentId = Objects.requireNonNull(agentId, "agentId");
		this.entity = Objects.requireNonNull(entity, "entity");
		validateText(text);
		this.text = text;
	}

	/**
	 * 校验说话文本（纯函数，便于无实体单测）。
	 *
	 * @param text 说的内容
	 * @throws NullPointerException text 为 null
	 * @throws IllegalArgumentException text 为空白
	 */
	public static void validateText(String text) {
		Objects.requireNonNull(text, "text");
		if (text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
	}

	/** @return 智能体身份（0.1 = session-id） */
	public String agentId() {
		return this.agentId;
	}

	/** @return 执行 {@code meta.say} 的实体 */
	public LivingEntity entity() {
		return this.entity;
	}

	/** @return 说的内容（非空白） */
	public String text() {
		return this.text;
	}
}