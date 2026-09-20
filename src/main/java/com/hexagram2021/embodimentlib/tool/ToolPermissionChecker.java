package com.hexagram2021.embodimentlib.tool;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 工具权限否决钩子（PLAN WP-5 ⑤，PRD §4.6 addon 扩展点）。
 * <p>
 * addon 可实现本接口，在每次工具调用<b>之前</b>做一次裁决：领地保护、白名单、
 * 「这个 NPC 不许攻击村民」之类的领域规则。返回 false 则本次调用被否决，
 * 工具输出 {@code "permission denied"}，模型会看到并自行换一种做法。
 *
 * <h2>为什么是「否决」而不是「允许」</h2>
 * 默认必须是<b>放行</b>。若设计成「只有显式允许才能执行」，那么 addon 一旦忘记注册检查器，
 * 所有工具都会静默失效——这种「默认坏掉」的设计对库来说不可接受。
 * 因此本接口只提供否决能力，默认实现（不注册时）恒为放行。
 *
 * <h2>线程语义</h2>
 * 本接口的 {@link #check} 在<b>游戏线程</b>上被调用（与工具体同线程），
 * 因此实现可以安全地读取世界状态（这正是领地保护类检查所需要的）。
 * 但实现<b>不得</b>长时间阻塞——它处在 tick 的关键路径上。
 *
 * @author liudongyu
 */
@FunctionalInterface
public interface ToolPermissionChecker {
	/** 被否决时工具必须返回的 observation 文本（PRD §4.6 约定）。 */
	String DENIED = "permission denied";

	/**
	 * 裁决一次工具调用是否放行。
	 *
	 * @param ctx 工具上下文（含执行实体、宿主侧、会话身份）
	 * @param toolName 工具名（如 {@code "action.mine_block"}）
	 * @param input 模型给出的参数；只读，实现不应修改
	 * @return 放行返回 true；否决返回 false
	 */
	boolean check(ToolContext ctx, String toolName, Map<String, Object> input);

	/**
	 * 返回一个「与」组合的检查器：两者都放行才放行。
	 * <p>
	 * 用于把多个 addon 的规则串起来。注意短路语义——{@code this} 否决时
	 * 不再调用 {@code other}，因此检查器的副作用（如计数）不应被依赖。
	 *
	 * @param other 另一个检查器
	 * @return 组合后的检查器
	 */
	default ToolPermissionChecker and(ToolPermissionChecker other) {
		return (ctx, toolName, input) -> this.check(ctx, toolName, input)
			&& other.check(ctx, toolName, input);
	}

	/**
	 * 构造一个恒放行的检查器。
	 *
	 * @return 恒放行的检查器
	 */
	static ToolPermissionChecker allowAll() {
		return (ctx, toolName, input) -> true;
	}

	/**
	 * 构造一个按工具名黑名单否决的检查器。
	 * <p>
	 * 覆盖最常见的一类需求（「这个 agent 不许挖方块」），让 addon 不必为此写一个类。
	 *
	 * @param deniedToolNames 要否决的工具名
	 * @return 命中黑名单即否决的检查器
	 */
	static ToolPermissionChecker denyTools(String... deniedToolNames) {
		Set<String> denied = Set.of(deniedToolNames);
		return (ctx, toolName, input) -> !denied.contains(toolName);
	}

	/**
	 * 在指定检查器下执行任务，被否决时返回 {@link #DENIED}。
	 * <p>
	 * 把「检查 + 生成否决文本」这一步固定下来，避免每个调用点各写一遍
	 * （写漏一处就是一个权限绕过）。
	 *
	 * @param checker 检查器；{@code null} 视为恒放行
	 * @param ctx 工具上下文
	 * @param toolName 工具名
	 * @param input 模型参数
	 * @param task 放行后要执行的任务
	 * @return 任务结果，或被否决时的 {@link #DENIED}
	 */
	static String guarded(@Nullable ToolPermissionChecker checker, ToolContext ctx, String toolName,
			Map<String, Object> input, Supplier<String> task) {
		if (checker != null && !checker.check(ctx, toolName, input)) {
			return DENIED;
		}
		return task.get();
	}
}
