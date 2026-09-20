package com.hexagram2021.embodimentlib.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuiltinToolkit} 的单测（PRD §4.5 的 23 工具目录与装配契约）。
 * <p>
 * 本 WP（WP-5）尚未实现任何具体工具，因此这里验证的是<b>目录与装配机制</b>：
 * 清单是否与 PRD 一致、P0/P1 切分是否正确、去重与裁剪是否会响亮失败。
 * WP-6/WP-7 落地后可在此追加「覆盖度校验」用例。
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

	@Test
	@DisplayName("目录恰好 23 个工具，且与 PRD §4.5 表格逐一对应（无缺无多）")
	void catalogMatchesPrdTable() {
		assertEquals(23, BuiltinToolkit.BUILTIN_TOOL_IDS.size(), "PRD §4.5 定义 23 个工具");
		assertEquals(PRD_TOOL_IDS_IN_ORDER.size(), BuiltinToolkit.BUILTIN_TOOL_IDS.size());
		// 用集合比较而非列表比较：本类声明的顺序按 P0/P1 分组（便于装配），
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
	@DisplayName("create() 返回可用的空 Toolkit（WP-6/WP-7 之前不注册任何工具）")
	void createReturnsEmptyToolkitBeforeToolImplementation() {
		Toolkit toolkit = BuiltinToolkit.create();
		assertNotNull(toolkit);
		assertTrue(
				toolkit.getToolNames().isEmpty(),
				"WP-5 阶段尚未实现具体工具；此处为空是预期状态，见 BuiltinToolkit 类 Javadoc"
		);
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
