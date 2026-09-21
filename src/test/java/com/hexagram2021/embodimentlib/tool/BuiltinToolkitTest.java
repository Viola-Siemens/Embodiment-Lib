package com.hexagram2021.embodimentlib.tool;

import com.google.common.collect.Lists;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BuiltinToolkit} 的单测（PRD §4.5 的 23 工具目录与装配契约）。
 * <p>
 * WP-6 实现并注册了 P0 的 12 个工具，WP-7 补齐了 P1 的 11 个；本类验证：目录与 PRD
 * 一致、去重与裁剪响亮失败、{@link BuiltinToolkit#create()} 恰好装配 23 个工具、
 * P1 的 11 个确实在其中、覆盖度校验能抓住缺失/多余/重复。
 */
class BuiltinToolkitTest {
	/** PRD §4.5 表格中的 23 个工具 ID，按目录出现顺序硬编码，作为对实现的独立复核。 */
	private static final List<String> PRD_TOOL_IDS_IN_ORDER = List.of(
			"perceive.nearest_block",
			"perceive.block_state_at",
			"perceive.inventory_contents",
			"perceive.inventory_slot",
			"perceive.nearby_entities",
			"perceive.self_status",
			"loco.move_to",
			"loco.move_to_entity",
			"loco.jump",
			"loco.look_at",
			"action.mine_block",
			"action.place_block",
			"action.use_item",
			"action.use_item_on",
			"action.interact_with_block",
			"action.attack_entity",
			"action.drop_item",
			"action.follow_entity",
			"action.stop_follow",
			"container.inspect",
			"container.transfer",
			"meta.wait",
			"meta.say"
	);

	/** WP-7 补齐的 P1 工具 ID（11 个），用于确认第二批确实被装配。 */
	private static final List<String> WP7_P1_TOOL_IDS = List.of(
			"perceive.inventory_slot",
			"perceive.nearby_entities",
			"loco.move_to_entity",
			"action.place_block",
			"action.use_item_on",
			"action.interact_with_block",
			"action.drop_item",
			"action.follow_entity",
			"action.stop_follow",
			"container.inspect",
			"container.transfer"
	);

	@Test
	@DisplayName("目录恰好 23 个工具，且与 PRD §4.5 表格逐一对应（无缺无多）")
	void catalogMatchesPrdTable() {
		assertEquals(23, BuiltinToolkit.BUILTIN_TOOL_IDS.size(), "PRD §4.5 定义 23 个工具");
		assertEquals(PRD_TOOL_IDS_IN_ORDER.size(), BuiltinToolkit.BUILTIN_TOOL_IDS.size());
		// 用集合比较而非列表比较：本类声明的顺序按类别分组（便于阅读与装配），
		// 与 PRD 表格顺序不同，两者都应成立，故只校验「内容一致」。
		assertEquals(
				PRD_TOOL_IDS_IN_ORDER.stream().sorted().toList(),
				BuiltinToolkit.BUILTIN_TOOL_IDS.stream().sorted().toList(),
				"目录内容应与 PRD §4.5 完全一致"
		);
	}

	@Test
	@DisplayName("目录内无重复 ID（重复会让装配静默覆盖）")
	void catalogHasNoDuplicates() {
		long distinct = BuiltinToolkit.BUILTIN_TOOL_IDS.stream().distinct().count();
		assertEquals(BuiltinToolkit.BUILTIN_TOOL_IDS.size(), distinct, "工具 ID 不得重复");
	}

	@Test
	@DisplayName("工具 ID 均为 category.action 形式且全小写（命名卫生）")
	void toolIdsFollowNamingConvention() {
		for (String id : BuiltinToolkit.BUILTIN_TOOL_IDS) {
			assertTrue(id.contains("."), () -> "工具 ID 应含类别前缀: " + id);
			assertEquals(id.toLowerCase(Locale.ROOT), id, () -> "工具 ID 应全小写: " + id);
		}
	}

	@Test
	@DisplayName("create() 装配全部 23 个工具（无缺无多无重）")
	void createRegistersWholeCatalog() {
		Toolkit toolkit = BuiltinToolkit.create();
		assertNotNull(toolkit);
		List<String> names = BuiltinToolkit.toolNames(toolkit);
		assertEquals(23, names.size(), "PRD §4.5 的 23 个工具应全部注册");
		assertEquals(BuiltinToolkit.BUILTIN_TOOL_IDS.stream().sorted().toList(),
				names.stream().sorted().toList(),
				"create() 注册的工具应与目录逐项一致");
	}

	@Test
	@DisplayName("create() 的装配通过 validateCoverage 自检（无缺无多无重）")
	void createCoverageMatchesCatalog() {
		Toolkit toolkit = BuiltinToolkit.create();
		List<AgentTool> tools = toolkit.getToolNames().stream()
				.map(toolkit::getTool)
				.toList();
		List<String> problems = BuiltinToolkit.validateCoverage(tools, BuiltinToolkit.BUILTIN_TOOL_IDS);
		assertTrue(problems.isEmpty(), () -> "create() 装配应与目录一致: " + problems);
	}

	@Test
	@DisplayName("create() 确实包含 WP-7 补齐的 11 个 P1 工具（逐个点名，避免漏注册）")
	void createIncludesEveryWp7Tool() {
		List<String> names = BuiltinToolkit.toolNames(BuiltinToolkit.create());
		for (String id : WP7_P1_TOOL_IDS) {
			assertTrue(names.contains(id), () -> "WP-7 的工具未注册: " + id);
		}
		assertEquals(11, WP7_P1_TOOL_IDS.size(), "WP-7 应补齐 11 个工具");
	}

	@Test
	@DisplayName("create() 注册的每个工具都能按 ID 取回实例（不是空占位）")
	void createToolsAreRetrievable() {
		Toolkit toolkit = BuiltinToolkit.create();
		for (String id : BuiltinToolkit.BUILTIN_TOOL_IDS) {
			AgentTool tool = toolkit.getTool(id);
			assertNotNull(tool, () -> "按 ID 取不到工具实例: " + id);
			assertEquals(id, tool.getName(), () -> "工具实例名与目录不一致: " + id);
			assertNotNull(tool.getDescription(), () -> "工具缺少给 LLM 的描述: " + id);
			Object schema = tool.getParameters().get("type");
			assertEquals("object", schema, () -> "工具参数根节点应为 object: " + id);
		}
	}

	@Test
	@DisplayName("without() 排除的工具确实不出现在结果中，其余保留")
	void withoutFiltersOutExcludedTools() {
		Toolkit toolkit = BuiltinToolkit.without("action.mine_block", "meta.say");
		List<String> names = BuiltinToolkit.toolNames(toolkit);
		assertEquals(21, names.size(), "排除 2 个后应剩 21 个");
		assertTrue(!names.contains("action.mine_block") && !names.contains("meta.say"),
				"被排除的工具不得出现在结果中");
		assertTrue(names.contains("perceive.self_status"), "未排除的工具应保留");
		assertTrue(names.contains("container.transfer"), "P1 工具同样应保留");
	}

	@Test
	@DisplayName("without() 可以裁掉整个目录（返回空工具集，供「只用自定义工具」的 addon）")
	void withoutCanExcludeWholeCatalog() {
		Toolkit toolkit = BuiltinToolkit.without(BuiltinToolkit.BUILTIN_TOOL_IDS.toArray(new String[0]));
		assertTrue(BuiltinToolkit.toolNames(toolkit).isEmpty(), "全部排除后应为空");
	}

	@Test
	@DisplayName("without() 对未知工具 ID 响亮失败（拼错会让「禁用」静默失效）")
	void withoutRejectsUnknownToolId() {
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> BuiltinToolkit.without("action.mine_blok")
		);
		assertTrue(ex.getMessage().contains("action.mine_blok"), "异常应指明是哪个 ID 有问题");
	}

	@Test
	@DisplayName("without() 接受目录内的合法 ID")
	void withoutAcceptsKnownToolId() {
		assertNotNull(BuiltinToolkit.without("action.mine_block"));
		assertNotNull(BuiltinToolkit.without("meta.say", "meta.wait"));
	}

	@Test
	@DisplayName("toolNames：null toolkit 返回空列表，便于调用方免去判空")
	void toolNamesHandlesNull() {
		assertTrue(BuiltinToolkit.toolNames(null).isEmpty());
		assertTrue(BuiltinToolkit.toolNames(new Toolkit()).isEmpty());
	}

	@Test
	@DisplayName("validateCoverage：完全匹配时无问题报告")
	void validateCoveragePassesOnExactMatch() {
		List<String> problems = BuiltinToolkit.validateCoverage(
				List.of(stubTool("a.one"), stubTool("a.two")), List.of("a.one", "a.two"));
		assertTrue(problems.isEmpty(), () -> "不应报告问题: " + problems);
	}

	@Test
	@DisplayName("validateCoverage：检出缺失的工具")
	void validateCoverageDetectsMissing() {
		List<String> problems = BuiltinToolkit.validateCoverage(
				List.of(stubTool("a.one")), List.of("a.one", "a.two"));
		assertEquals(1, problems.size());
		assertTrue(problems.getFirst().contains("missing") && problems.getFirst().contains("a.two"));
	}

	@Test
	@DisplayName("validateCoverage：检出多余的工具")
	void validateCoverageDetectsExtra() {
		List<String> problems = BuiltinToolkit.validateCoverage(
				List.of(stubTool("a.one"), stubTool("a.extra")), List.of("a.one"));
		assertEquals(1, problems.size());
		assertTrue(problems.getFirst().contains("unexpected") && problems.getFirst().contains("a.extra"));
	}

	@Test
	@DisplayName("validateCoverage：检出重复的工具")
	void validateCoverageDetectsDuplicate() {
		List<String> problems = BuiltinToolkit.validateCoverage(
				List.of(stubTool("a.one"), stubTool("a.one")), List.of("a.one"));
		assertEquals(1, problems.size());
		assertTrue(problems.getFirst().contains("duplicate"), () -> "应报告重复: " + problems);
	}

	@Test
	@DisplayName("register：重名工具被拦下（避免两个 WP 各自实现时静默覆盖）")
	void registerRejectsDuplicateName() {
		Toolkit toolkit = new Toolkit();
		BuiltinToolkit.register(toolkit, stubTool("a.one"));
		assertEquals(List.of("a.one"), BuiltinToolkit.toolNames(toolkit));

		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> BuiltinToolkit.register(toolkit, stubTool("a.one"))
		);
		assertTrue(ex.getMessage().contains("a.one"));
	}

	@Test
	@DisplayName("register：正常注册后工具名可被检索")
	void registerAddsToolToToolkit() {
		Toolkit toolkit = new Toolkit();
		BuiltinToolkit.registerAll(toolkit, List.of(stubTool("a.one"), stubTool("b.two")));
		assertEquals(2, toolkit.getToolNames().size());
		assertTrue(toolkit.getToolNames().containsAll(List.of("a.one", "b.two")));
	}

	@Test
	@DisplayName("registerAll：空集合不报错（addon 可能动态算出空列表）")
	void registerAllAcceptsEmpty() {
		Toolkit toolkit = new Toolkit();
		BuiltinToolkit.registerAll(toolkit, Lists.newArrayList());
		assertTrue(toolkit.getToolNames().isEmpty());
	}

	/** 构造一个只用于装配测试的哑工具（不执行任何逻辑）。 */
	private static AgentTool stubTool(String name) {
		return new AgentTool() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public String getDescription() {
				return "stub";
			}

			@Override
			public Map<String, Object> getParameters() {
				return Map.of("type", "object", "properties", Map.of());
			}

			@Override
			public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
				return Mono.just(ToolResultBlock.text("stub"));
			}
		};
	}
}
