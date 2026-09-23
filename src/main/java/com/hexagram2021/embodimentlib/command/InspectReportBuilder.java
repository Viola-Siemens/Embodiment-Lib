package com.hexagram2021.embodimentlib.command;

import com.hexagram2021.embodimentlib.attach.ToolCallRecord;

import java.nio.file.Path;
import java.util.List;

/**
 * 把 {@link InspectData} 格式化成 {@code /embodimentlib inspect} 的多行报告
 * （PLAN WP-8，<b>纯逻辑</b>：零 Minecraft / 零 NeoForge 依赖）。
 * <p>
 * 本类只有一个公开入口 {@link #build(InspectData)}，<b>全部文字都出自这里</b>。
 * 这样做的直接好处是 PLAN 的验收项「报告含五类信息且不含 api_key」可以被逐行断言，
 * 而不必启动服务器去肉眼看命令输出——运维界面恰恰是最容易被「改一行没测到」破坏的地方。
 *
 * <h2>报告形态</h2>
 * <pre>
 * === embodimentlib inspect ===
 * target: <名字> (explicit argument|command source entity|nearest attached entity nearby)
 * side: SERVER
 * agent-type: village_npc
 * session-id: 8b1c…
 * state: IDLE | not registered
 * profile: quest_giver (anthropic / claude-sonnet-4-5) | -
 * tool calls (last 5):
 *   1. move_to in={"x":10} out="arrived"
 *   (no tool calls recorded)
 * conversation preview: <最多 200 字符>
 * session files: history 1024 bytes @ …/agent_state.json | not persisted yet
 * </pre>
 * 未附着实体只输出前两行再加一行 {@value #NO_AGENT_ATTACHED}——其余五类信息都无从谈起，
 * 用一行说清比留一堆空字段更利于运维判断「这个实体根本没被挂上智能体」。
 *
 * <h2>为什么不做「本类之外再截断」</h2>
 * 会话预览的 200 字符上限在本类<b>内部</b>执行（{@link #truncate}），而不是信任调用方：
 * 报告是给人看、也会进日志的东西，它的长度上限属于呈现契约，放在呈现层才守得住。
 *
 * @author liudongyu
 */
public final class InspectReportBuilder {
	/** 报告首行。 */
	public static final String HEADER = "=== embodimentlib inspect ===";
	/** 未附着实体的明示文本（PLAN WP-8 验收项 2 的约定文本）。 */
	public static final String NO_AGENT_ATTACHED = "no agent attached";
	/** 附着但注册表没有条目时的状态文本。 */
	public static final String NOT_REGISTERED = "not registered";
	/** 无 profile 可展示时的占位。 */
	public static final String NO_PROFILE = "-";
	/** 无工具调用时的占位行。 */
	public static final String NO_TOOL_CALLS = "  (no tool calls recorded)";
	/** 会话历史尚未落盘时的文本。 */
	public static final String NO_HISTORY = "not persisted yet";
	/** 会话预览的字符上限（PLAN WP-8：前 200 字符）。 */
	public static final int DEFAULT_CONVERSATION_PREVIEW_CHARS = 200;
	/** 截断标记。 */
	public static final String TRUNCATION_SUFFIX = "...";
	/** 工具调用条目的缩进。 */
	private static final String ITEM_INDENT = "  ";

	private InspectReportBuilder() {
	}

	/**
	 * 生成报告全文。
	 *
	 * @param data 报告数据
	 * @return 多行文本（不含颜色/翻译组件，纯文本，便于日志与命令输出）
	 */
	public static String build(InspectData data) {
		StringBuilder report = new StringBuilder(256);
		report.append(HEADER).append('\n');
		report.append("target: ").append(data.targetLabel())
				.append(" (").append(data.targetSource().label()).append(")\n");
		report.append("side: ").append(data.side()).append('\n');

		if (!data.attached()) {
			report.append(NO_AGENT_ATTACHED);
			return report.toString();
		}

		report.append("agent-type: ").append(data.agentType()).append('\n');
		report.append("session-id: ").append(data.sessionId()).append('\n');
		report.append("state: ").append(data.registered() ? data.state().name() : NOT_REGISTERED).append('\n');
		report.append("profile: ")
				.append(data.profile() == null ? NO_PROFILE : data.profile().display()).append('\n');
		appendToolCalls(report, data);
		report.append("conversation preview: ")
				.append(data.conversationPreview() == null ? NO_HISTORY : truncate(
						data.conversationPreview(), DEFAULT_CONVERSATION_PREVIEW_CHARS
				))
				.append('\n');
		report.append("session files: ").append(describeSessionFiles(data));
		return report.toString();
	}

	private static void appendToolCalls(StringBuilder report, InspectData data) {
		List<ToolCallRecord> calls = data.recentToolCalls();
		int shown = Math.min(calls.size(), data.toolCallLimit());
		report.append("tool calls (last ").append(data.toolCallLimit()).append("):\n");
		if (shown == 0) {
			report.append(NO_TOOL_CALLS).append('\n');
			return;
		}
		for (int index = 0; index < shown; index++) {
			ToolCallRecord call = calls.get(index);
			report.append(ITEM_INDENT).append(index + 1).append(". ")
					.append(call.toolName())
					.append(" in=").append(call.input())
					.append(" out=").append(call.observation())
					.append('\n');
		}
	}

	private static String describeSessionFiles(InspectData data) {
		if (!data.hasHistory()) {
			return NO_HISTORY;
		}
		Path path = data.historyPath();
		return "history " + data.historySizeBytes() + " bytes @ " + path;
	}

	/**
	 * 截断到指定字符数，超出部分以 {@value #TRUNCATION_SUFFIX} 收尾（总长不超过上限）。
	 *
	 * @param text 原始文本
	 * @param maxChars 上限（含省略标记）
	 * @return 不超过上限的文本
	 */
	public static String truncate(String text, int maxChars) {
		if (maxChars < TRUNCATION_SUFFIX.length()) {
			throw new IllegalArgumentException(
					"maxChars must be at least " + TRUNCATION_SUFFIX.length() + ", got " + maxChars
			);
		}
		if (text.length() <= maxChars) {
			return text;
		}
		return text.substring(0, maxChars - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
	}
}
