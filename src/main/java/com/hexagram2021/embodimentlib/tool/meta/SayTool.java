package com.hexagram2021.embodimentlib.tool.meta;

import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.api.event.AgentSayEvent;
import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 内置工具 {@code meta.say}（PLAN WP-6 #23，PRD §4.5 #23）。
 * <p>
 * 让绑定实体「说一句话」。工具本身<b>不渲染任何东西</b>——它只负责在游戏线程
 * 向 {@code NeoForge.EVENT_BUS} post {@link AgentSayEvent}，addon 订阅后自行决定
 * 如何呈现（聊天 / 气泡 / 动作栏 / 语音，PRD §4.7）。
 *
 * <h2>事件字段</h2>
 * {@code AgentSayEvent(agentId = session-id, entity, text)}——agent-id 即 session-id
 * （PRD §4.1.3：0.1 个体身份 = 会话身份），addon 据此把「说话」关联回具体个体。
 *
 * <h2>失败形态</h2>
 * 空白/缺失 text 返回 {@code "invalid input: text must not be blank"}（文本，不抛异常；
 * 不 post 无意义事件）。
 *
 * @author liudongyu
 */
public final class SayTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public SayTool() {
		super(
			"meta.say",
			"Say a line of text as this entity. The library only posts an AgentSayEvent on the "
				+ "event bus; rendering (chat, bubble, action bar, voice) is the addon's job. "
				+ "Reports \"said\".",
			ToolResults.objectSchema(Map.of(
				SayLogic.TEXT_KEY, ToolResults.prop("string", "The line of text to say")),
				List.of(SayLogic.TEXT_KEY)),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String validation = SayLogic.validate(input.get(SayLogic.TEXT_KEY));
		if (!SayLogic.SAID.equals(validation)) {
			return validation;
		}
		String text = String.valueOf(input.get(SayLogic.TEXT_KEY));
		NeoForge.EVENT_BUS.post(new AgentSayEvent(ctx.sessionId(), ctx.entity(), text));
		return SayLogic.SAID;
	}
}