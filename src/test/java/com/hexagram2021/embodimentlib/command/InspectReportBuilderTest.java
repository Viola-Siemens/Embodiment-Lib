package com.hexagram2021.embodimentlib.command;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.hexagram2021.embodimentlib.attach.AgentState;
import com.hexagram2021.embodimentlib.attach.ToolCallRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InspectReportBuilder} 与 {@link InspectData} 的单测（PLAN WP-8 验收标准 1/2/4）。
 * <p>
 * 覆盖：五类信息的逐行形态、未附着实体的明示、空注册表 vs 有记录的两种状态、
 * 工具调用的条数与顺序、会话预览的截断上限，以及<b>报告不含 api_key</b> 这条隐私硬约束。
 * 全程纯 JUnit：报告数据是构造出来的，不需要服务器、实体或注册表。
 * <p>
 * 断言以「逐行比对」为主而非整段字符串：报告是人读的东西，行序与行首文本才是契约；
 * 整段比对还会把平台相关的路径分隔符卷进来。
 */
class InspectReportBuilderTest {
	private static final String SESSION_ID = "8b1c1e2a-3f4d-4a5b-8c9d-0e1f2a3b4c5d";
	private static final String API_KEY = "sk-super-secret-key";
	private static final String BASE_URL = "https://internal.example.invalid/v1";
	private static final Path HISTORY_PATH =
		Path.of("world", "embodimentlib", "sessions", SESSION_ID, "__anon__", SESSION_ID, "agent_state.json");

	private static InspectData.ProfileView profileView() {
		// 用带密钥与内网地址的真实 AgentProfile 构造视图：确认映射时二者都被丢弃。
		AgentProfile profile = new AgentProfile(
			AgentProfile.PROTOCOL_ANTHROPIC, BASE_URL, API_KEY, "claude-sonnet-4-5");
		return InspectData.ProfileView.of("quest_giver", profile);
	}

	private static ToolCallRecord record(String tool, String input, String observation) {
		return new ToolCallRecord(tool, input, observation, 1_700_000_000_000L);
	}

	private static InspectData attached(List<ToolCallRecord> calls, String preview,
			Path historyPath, long historySize) {
		return new InspectData(AgentHostSide.SERVER, "Villager(8b1c1e2a)", InspectData.TargetSource.EXPLICIT,
			"village_npc", SESSION_ID, profileView(), AgentState.IDLE,
			calls, preview, historyPath, historySize, InspectData.DEFAULT_TOOL_CALL_LIMIT);
	}

	private static List<String> lines(String report) {
		return report.lines().toList();
	}

	@Test
	@DisplayName("验收①：附着实体的报告含五类信息（类型/会话、profile、工具调用、预览、状态）")
	void reportContainsAllFiveCategories() {
		InspectData data = attached(
			List.of(record("loco.move_to", "{\"x\":10}", "arrived")),
			"SERVER village_npc/8b1c... state=IDLE recentTools=1",
			HISTORY_PATH, 1024L);

		List<String> report = lines(InspectReportBuilder.build(data));

		assertEquals(11, report.size(), () -> "报告行数变化需同步更新本用例: " + report);
		assertEquals("=== embodimentlib inspect ===", report.get(0));
		assertEquals("target: Villager(8b1c1e2a) (explicit argument)", report.get(1));
		assertEquals("side: SERVER", report.get(2));
		assertEquals("agent-type: village_npc", report.get(3));           // 类别 1
		assertEquals("session-id: " + SESSION_ID, report.get(4));         // 类别 1
		assertEquals("state: IDLE", report.get(5));                       // 类别 5
		assertEquals("profile: quest_giver (anthropic / claude-sonnet-4-5)", report.get(6)); // 类别 2
		assertEquals("tool calls (last 5):", report.get(7));              // 类别 3
		assertEquals("  1. loco.move_to in={\"x\":10} out=arrived", report.get(8));
		assertEquals("conversation preview: SERVER village_npc/8b1c... state=IDLE recentTools=1",
			report.get(9));                                              // 类别 4
		assertEquals("session files: history 1024 bytes @ " + HISTORY_PATH, report.get(10));
	}

	@Test
	@DisplayName("验收①（隐私硬约束）：报告不含 api_key，也不含 base_url")
	void reportNeverLeaksApiKeyOrBaseUrl() {
		String report = InspectReportBuilder.build(attached(List.of(), "preview", HISTORY_PATH, 1L));

		assertFalse(report.contains(API_KEY), "报告不得出现 api_key");
		assertFalse(report.contains("api_key"), "报告不得出现 'api_key' 字样");
		assertFalse(report.contains(BASE_URL), "报告不得出现 base_url");
		// 但协议与模型名必须在，否则运维看不到路由结果
		assertTrue(report.contains("anthropic / claude-sonnet-4-5"), report);
	}

	@Test
	@DisplayName("验收②：未附着实体只输出身份行 + 明示 no agent attached")
	void notAttachedReportIsMinimal() {
		InspectData data = InspectData.notAttached(AgentHostSide.SERVER, "Pig(00000000)",
			InspectData.TargetSource.NEAREST_ATTACHED);

		List<String> report = lines(InspectReportBuilder.build(data));

		assertEquals(4, report.size(), () -> "未附着报告应只有 4 行: " + report);
		assertEquals("target: Pig(00000000) (nearest attached entity nearby)", report.get(1));
		assertEquals("side: SERVER", report.get(2));
		assertEquals(InspectReportBuilder.NO_AGENT_ATTACHED, report.get(3));
		// 不应出现任何「看起来像有智能体」的行
		assertFalse(report.contains("agent-type"), report.toString());
		assertFalse(report.stream().anyMatch(line -> line.startsWith("profile")), report.toString());
	}

	@Test
	@DisplayName("验收②：未附着实体的 attached()/registered()/hasHistory() 全为假")
	void notAttachedPredicates() {
		InspectData data = InspectData.notAttached(AgentHostSide.CLIENT, "Cow(11111111)",
			InspectData.TargetSource.SOURCE_ENTITY);
		assertFalse(data.attached());
		assertFalse(data.registered());
		assertFalse(data.hasHistory());
		assertEquals(AgentHostSide.CLIENT, data.side());
		assertTrue(data.recentToolCalls().isEmpty());
	}

	@Test
	@DisplayName("验收④（空注册表）：附着但没有注册表条目 → not registered + 无工具调用 + 无历史")
	void attachedButNotRegistered() {
		InspectData data = new InspectData(AgentHostSide.SERVER, "Villager(8b1c1e2a)",
			InspectData.TargetSource.EXPLICIT, "village_npc", SESSION_ID,
			profileView(), null, List.of(), null, null, -1L, InspectData.DEFAULT_TOOL_CALL_LIMIT);

		List<String> report = lines(InspectReportBuilder.build(data));

		assertTrue(data.attached());
		assertFalse(data.registered());
		assertFalse(data.hasHistory());
		assertEquals("state: " + InspectReportBuilder.NOT_REGISTERED, report.get(5));
		assertEquals(InspectReportBuilder.NO_TOOL_CALLS, report.get(8));
		assertEquals("conversation preview: " + InspectReportBuilder.NO_HISTORY, report.get(9));
		assertEquals("session files: " + InspectReportBuilder.NO_HISTORY, report.get(10));
	}

	@Test
	@DisplayName("验收④（有记录）：工具调用按「新的在前」编号输出")
	void registeredWithToolCalls() {
		InspectData data = attached(List.of(
			record("action.mine_block", "{\"pos\":[1,2,3]}", "mined"),
			record("perceive.self_status", "{}", "pos=(0,64,0)")),
			"preview", null, -1L);

		List<String> report = lines(InspectReportBuilder.build(data));

		assertEquals("  1. action.mine_block in={\"pos\":[1,2,3]} out=mined", report.get(8));
		assertEquals("  2. perceive.self_status in={} out=pos=(0,64,0)", report.get(9));
	}

	@Test
	@DisplayName("工具调用条数受 toolCallLimit 限制（PLAN：默认 5）")
	void toolCallsAreLimited() {
		List<ToolCallRecord> calls = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			calls.add(record("tool." + index, "{}", "ok"));
		}
		InspectData data = new InspectData(AgentHostSide.SERVER, "Villager(8b1c1e2a)",
			InspectData.TargetSource.EXPLICIT, "village_npc", SESSION_ID,
			profileView(), AgentState.REASONING, calls, "preview", null, -1L, 3);

		String report = InspectReportBuilder.build(data);

		assertTrue(report.contains("tool calls (last 3):"), report);
		assertTrue(report.contains("  3. tool.2 in={} out=ok"), report);
		assertFalse(report.contains("tool.3"), "超出上限的条目不得出现");
		assertEquals(5, InspectData.DEFAULT_TOOL_CALL_LIMIT, "PLAN 规定默认展示 5 条");
	}

	@Test
	@DisplayName("会话预览按 200 字符截断：正好 200 原样输出，超出则加省略号")
	void conversationPreviewIsTruncated() {
		String exact = "x".repeat(InspectReportBuilder.DEFAULT_CONVERSATION_PREVIEW_CHARS);
		assertTrue(InspectReportBuilder.build(attached(List.of(), exact, null, -1L))
			.contains("conversation preview: " + exact));

		String tooLong = "y".repeat(InspectReportBuilder.DEFAULT_CONVERSATION_PREVIEW_CHARS + 50);
		String report = InspectReportBuilder.build(attached(List.of(), tooLong, null, -1L));
		String expected = "y".repeat(InspectReportBuilder.DEFAULT_CONVERSATION_PREVIEW_CHARS - 3) + "...";
		assertTrue(report.contains("conversation preview: " + expected), report);
		assertFalse(report.contains("y".repeat(InspectReportBuilder.DEFAULT_CONVERSATION_PREVIEW_CHARS)), report);
	}

	@Test
	@DisplayName("truncate：上限小于省略标记长度时抛 IAE")
	void truncateRejectsTinyLimit() {
		assertThrows(IllegalArgumentException.class, () -> InspectReportBuilder.truncate("abc", 2));
		assertEquals("abc", InspectReportBuilder.truncate("abc", 3));
		assertEquals("...", InspectReportBuilder.truncate("abcdef", 3));
	}

	@Test
	@DisplayName("三种目标来源都在报告里注明（否则运维不知道这份报告说的是谁）")
	void targetSourceIsAlwaysPrinted() {
		for (InspectData.TargetSource source : InspectData.TargetSource.values()) {
			String report = InspectReportBuilder.build(
				InspectData.notAttached(AgentHostSide.SERVER, "Pig(00000000)", source));
			assertTrue(report.contains("(" + source.label() + ")"), source + " -> " + report);
		}
	}

	@Test
	@DisplayName("InspectData：toolCallLimit 必须为正，工具调用列表被复制且不可变")
	void inspectDataValidatesAndCopies() {
		List<ToolCallRecord> mutable = new ArrayList<>();
		mutable.add(record("a", "{}", "ok"));
		InspectData data = new InspectData(AgentHostSide.SERVER, "Pig(00000000)",
			InspectData.TargetSource.EXPLICIT, "type", "session", null, null,
			mutable, null, null, -1L, 1);
		mutable.clear();
		assertEquals(1, data.recentToolCalls().size(), "构造时应复制列表");
		assertThrows(UnsupportedOperationException.class,
			() -> data.recentToolCalls().add(record("b", "{}", "ok")));

		assertThrows(IllegalArgumentException.class, () -> new InspectData(AgentHostSide.SERVER, "Pig(00000000)",
			InspectData.TargetSource.EXPLICIT, "type", "session", null, null,
			List.of(), null, null, -1L, 0));
	}

	@Test
	@DisplayName("ProfileView：display() 形态固定，且只有名字/协议/模型三个字段")
	void profileViewDisplay() {
		InspectData.ProfileView view = profileView();
		assertEquals("quest_giver (anthropic / claude-sonnet-4-5)", view.display());
		assertEquals("quest_giver", view.name());
		assertEquals("anthropic", view.protocol());
		assertEquals("claude-sonnet-4-5", view.modelName());
	}
}
