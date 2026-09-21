package com.hexagram2021.embodimentlib.tool;

import com.hexagram2021.embodimentlib.tool.action.AttackEntityTool;
import com.hexagram2021.embodimentlib.tool.action.MineBlockTool;
import com.hexagram2021.embodimentlib.tool.action.UseItemTool;
import com.hexagram2021.embodimentlib.tool.loco.JumpTool;
import com.hexagram2021.embodimentlib.tool.loco.LookAtTool;
import com.hexagram2021.embodimentlib.tool.loco.MoveToTool;
import com.hexagram2021.embodimentlib.tool.meta.SayTool;
import com.hexagram2021.embodimentlib.tool.meta.WaitTool;
import com.hexagram2021.embodimentlib.tool.perceive.BlockStateAtTool;
import com.hexagram2021.embodimentlib.tool.perceive.InventoryContentsTool;
import com.hexagram2021.embodimentlib.tool.perceive.NearestBlockTool;
import com.hexagram2021.embodimentlib.tool.perceive.SelfStatusTool;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * 内置工具集工厂（PLAN WP-5 ④）。
 * <p>
 * 负责把内置工具装配成一个可直接交给 {@code HarnessAgent} 的 {@link Toolkit}，
 * 并提供 addon 定制入口（禁用/替换某个工具）。
 *
 * <h2>当前状态：WP-6 已注册 P0 的 12 个工具，P1 待 WP-7</h2>
 * PRD §4.5 定义了 23 个工具。WP-6 实现并注册了第一批 12 个（感知/移动/行动/元操作），
 * 见 {@link #create()}；其余 11 个（容器、放置、交互等 P1）由 WP-7 补齐。
 * <ul>
 *   <li>已注册工具：{@code perceive.nearest_block / block_state_at / inventory_contents / self_status}、
 *       {@code loco.move_to / jump / look_at}、
 *       {@code action.mine_block / use_item / attack_entity}、
 *       {@code meta.wait / say}；</li>
 *   <li>未注册（WP-7）：{@code perceive.inventory_slot / nearby_entities}、
 *       {@code loco.move_to_entity}、{@code action.place_block / use_item_on / interact_with_block / drop_item / follow_entity / stop_follow}、
 *       {@code container.inspect / transfer}。</li>
 * </ul>
 *
 * <h2>对外契约（addon 视角）</h2>
 * 本类是内置工具的<b>装配与查询入口</b>。addon 关心的只有一个维度：
 * 「有哪些内置工具、某个 ID 是否存在、能不能裁掉」。实现时的开发分期（P0/P1）
 * 是<b>内部口径</b>，不暴露到这里——addon 感知的只是工具的「存在性与功能」，而非何时被实现。
 *
 * <h2>与 AgentScope 的边界</h2>
 * {@code HarnessAgent.Builder#toolkit} 会对传入的 Toolkit 做<b>深拷贝</b>，
 * 因此调用方拿到 agent 后再改这个 Toolkit 不会影响 agent，反之亦然。
 * 这意味着「先 create() 再追加自定义工具」是安全的用法。
 *
 * @author liudongyu
 */
public final class BuiltinToolkit {
	/**
	 * PRD §4.5 定义的 23 个内置工具 ID。
	 * <p>
	 * 这是 addon 可感知的工具全集清单，唯一权威：用于查询某个内置工具是否存在、
	 * {@link #without} 的校验、以及 WP-6/WP-7/WP-10 的完成度自检
	 * （「目录里的每个 ID 都有实现」）。
	 */
	public static final List<String> BUILTIN_TOOL_IDS = List.of(
		"perceive.nearest_block",
		"perceive.block_state_at",
		"perceive.inventory_contents",
		"perceive.self_status",
		"loco.move_to",
		"loco.jump",
		"loco.look_at",
		"action.mine_block",
		"action.use_item",
		"action.attack_entity",
		"meta.wait",
		"meta.say",
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
		"container.transfer");

	private BuiltinToolkit() {
	}

	/**
	 * WP-6 已实现的 12 个内置工具（ID → 实例）。
	 * <p>
	 * {@link #create()} 与 {@link #without} 共用此清单，保证两处装配绝对一致。
	 * WP-7 在此追加 P1 的 11 个工具。
	 *
	 * @return 按注册顺序的 ID → 工具映射
	 */
	private static Map<String, AgentTool> builtinTools() {
		Map<String, AgentTool> tools = new LinkedHashMap<>();
		for (AgentTool tool : List.of(
			// perceive（WP-6 ①）：只读世界。
			new NearestBlockTool(),
			new BlockStateAtTool(),
			new InventoryContentsTool(),
			new SelfStatusTool(),
			// loco（WP-6 ②）：移动与朝向。
			new MoveToTool(),
			new JumpTool(),
			new LookAtTool(),
			// action（WP-6 ②）：世界副作用。
			new MineBlockTool(),
			new UseItemTool(),
			new AttackEntityTool(),
			// meta（WP-6 ③）：智能体自身行为。
			new WaitTool(),
			new SayTool())) {
			tools.put(tool.getName(), tool);
		}
		return tools;
	}

	/**
	 * 创建内置工具集（WP-6：P0 的 12 个工具已注册；WP-7 补齐 P1）。
	 *
	 * @return 装配好的 Toolkit；调用方可安全地继续追加自定义工具
	 */
	public static Toolkit create() {
		Toolkit toolkit = new Toolkit();
		registerAll(toolkit, builtinTools().values());
		return toolkit;
	}

	/**
	 * 创建工具集，但排除指定的工具 ID。
	 * <p>
	 * 已注册的 12 个 WP-6 工具中排除；尚未实现的 ID（P1，WP-7）即使被排除也
	 * 不会报错——它们本来就不在工具集里。
	 *
	 * @param excludedToolIds 要排除的工具 ID
	 * @return 装配好的 Toolkit
	 * @throws IllegalArgumentException 传入了不在 {@link #BUILTIN_TOOL_IDS} 中的 ID
	 *         （拼错工具名会让「禁用」静默失效，这是安全相关的，必须响亮地失败）
	 */
	public static Toolkit without(String... excludedToolIds) {
		Set<String> excluded = Set.of(excludedToolIds);
		for (String id : excluded) {
			if (!BUILTIN_TOOL_IDS.contains(id)) {
				throw new IllegalArgumentException("unknown built-in tool id: '" + id + "'");
			}
		}
		Toolkit toolkit = new Toolkit();
		for (Map.Entry<String, AgentTool> entry : builtinTools().entrySet()) {
			if (!excluded.contains(entry.getKey())) {
				register(toolkit, entry.getValue());
			}
		}
		return toolkit;
	}

	/**
	 * 把工具注册进指定 Toolkit，并做重名检查。
	 * <p>
	 * AgentScope 的 {@code registerAgentTool} 对重名工具的处理是替换，会让「另一个 WP
	 * 不小心用了同名」变成静默覆盖——在 23 个工具分两批实现的场景下这是真实风险，故显式拦下。
	 *
	 * @param toolkit 目标工具集
	 * @param tool 工具实例
	 * @throws IllegalArgumentException toolkit 中已存在同名工具
	 */
	public static void register(Toolkit toolkit, AgentTool tool) {
		if (toolkit.getToolNames().contains(tool.getName())) {
			throw new IllegalArgumentException("duplicate tool name: '" + tool.getName() + "'");
		}
		toolkit.registerAgentTool(tool);
	}

	/**
	 * 批量注册，语义同 {@link #register}。
	 *
	 * @param toolkit 目标工具集
	 * @param tools 工具实例
	 */
	public static void registerAll(Toolkit toolkit, Iterable<? extends AgentTool> tools) {
		for (AgentTool tool : tools) {
			register(toolkit, tool);
		}
	}

	/**
	 * 校验一批工具是否恰好覆盖给定的 ID 清单（无缺、无多、无重）。
	 * <p>
	 * 供 WP-6/WP-7 的自检用例使用：把「目录里写了 23 个，实际实现了几个」这个
	 * 容易出错的人工核对变成一条断言。
	 *
	 * @param tools 待校验的工具
	 * @param expectedIds 期望的 ID 清单
	 * @return 校验结果描述：全部匹配时为空列表，否则每项描述一处差异
	 */
	public static List<String> validateCoverage(Iterable<? extends AgentTool> tools, List<String> expectedIds) {
		Map<String, Integer> actual = new LinkedHashMap<>();
		for (AgentTool tool : tools) {
			actual.merge(tool.getName(), 1, Integer::sum);
		}

		List<String> problems = new ArrayList<>();
		for (String id : expectedIds) {
			Integer count = actual.remove(id);
			if (count == null) {
				problems.add("missing tool: " + id);
			} else if (count > 1) {
				problems.add("duplicate tool: " + id + " (x" + count + ")");
			}
		}
		for (String extra : actual.keySet()) {
			problems.add("unexpected tool: " + extra);
		}
		return List.copyOf(problems);
	}

	/**
	 * 取工具集内的工具名快照。
	 * <p>
	 * 参数可空是刻意的：WP-2 的 {@code RegistryEntry.toolkit} 在收窄前以 {@code @Nullable}
	 * 形态存在，调用方可能确实拿到 null，这里返回空列表让它免去判空。
	 *
	 * @param toolkit 工具集；为 {@code null} 时返回空列表
	 * @return 工具名列表
	 */
	public static List<String> toolNames(@Nullable Toolkit toolkit) {
		return toolkit == null ? List.of() : List.copyOf(toolkit.getToolNames());
	}
}