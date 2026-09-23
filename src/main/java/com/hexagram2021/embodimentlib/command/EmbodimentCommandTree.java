package com.hexagram2021.embodimentlib.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * 命令树装配（PLAN WP-8，解决 PRD 开放决策 3 的 {@code /embodimentlib} 命令树）。
 * <pre>
 * /embodimentlib inspect [&lt;entity&gt;]     # 主体：检查实体的智能体状态
 * /emb inspect [&lt;entity&gt;]               # 别名前缀（PLAN 明确要求）
 * </pre>
 * {@code talk} 子命令属 WP-9（演示触发器），本 WP 只交付 {@code inspect}。
 *
 * <h2>为什么树构造对命令源类型泛型化</h2>
 * {@link #rootNode} / {@link #inspectNode} 不写死 {@code CommandSourceStack}，
 * 而由调用方注入 {@code requires} 谓词与两个执行器。这不是为了「可复用」，
 * 而是为了 PLAN 的验收项「别名 {@code /emb inspect} 的命令树解析正确」<b>真的能被单测</b>：
 * 单测可以用一个哑 source 类型注册同一棵树，让真实 Brigadier 去解析命令串，
 * 从而验证别名、子命令名、参数名与两个分支的执行器都被正确接上——
 * 而不是只断言几个字符串常量。生产路径 {@link #register} 才把它具体化为
 * {@code CommandSourceStack} 形态。
 *
 * <h2>两个执行器</h2>
 * 未给实体参数与给了实体参数走不同入口（而不是在执行器里 catch「参数不存在」）：
 * Brigadier 的 {@code getArgument} 在参数缺失时抛 {@code IllegalArgumentException}，
 * 用它来区分「可选参数」会把正常的用户输入变成异常路径。
 *
 * @author liudongyu
 */
public final class EmbodimentCommandTree {
	/** 主命令字面量。 */
	public static final String ROOT_LITERAL = "embodimentlib";
	/** 别名前缀字面量（PLAN：{@code /emb inspect}）。 */
	public static final String ALIAS_LITERAL = "emb";
	/** 子命令字面量。 */
	public static final String INSPECT_LITERAL = "inspect";
	/** 目标实体参数名。 */
	public static final String ENTITY_ARGUMENT = "entity";
	/** 用法提示（兜底找不到目标时回给执行者，让它知道可以显式指定）。 */
	public static final String USAGE_HINT = "/" + ROOT_LITERAL + " " + INSPECT_LITERAL
		+ " [" + ENTITY_ARGUMENT + "]" + " (alias /" + ALIAS_LITERAL + " " + INSPECT_LITERAL + ")";

	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.command");

	private EmbodimentCommandTree() {
	}

	/**
	 * 构造 {@code inspect} 子命令节点。
	 *
	 * @param requirement 权限谓词（不满足时该命令不可被解析）
	 * @param fallbackExecutor 未给实体参数时执行（命令执行者自身 / 附近兜底）
	 * @param explicitExecutor 给了实体参数时执行
	 * @param <S> 命令源类型
	 * @return 子命令节点
	 */
	public static <S> LiteralArgumentBuilder<S> inspectNode(Predicate<S> requirement,
			Command<S> fallbackExecutor, Command<S> explicitExecutor) {
		ArgumentType<EntitySelector> entityType = EntityArgument.entity();
		return LiteralArgumentBuilder.<S>literal(INSPECT_LITERAL)
			.requires(requirement)
			.executes(fallbackExecutor)
			.then(RequiredArgumentBuilder.<S, EntitySelector>argument(ENTITY_ARGUMENT, entityType)
				.executes(explicitExecutor));
	}

	/**
	 * 构造一个含 {@code inspect} 子命令的命令根节点。
	 *
	 * @param literal 根字面量（{@link #ROOT_LITERAL} 或 {@link #ALIAS_LITERAL}）
	 * @param requirement 权限谓词
	 * @param fallbackExecutor 未给实体参数时执行
	 * @param explicitExecutor 给了实体参数时执行
	 * @param <S> 命令源类型
	 * @return 根节点
	 */
	public static <S> LiteralArgumentBuilder<S> rootNode(String literal, Predicate<S> requirement,
			Command<S> fallbackExecutor, Command<S> explicitExecutor) {
		return LiteralArgumentBuilder.<S>literal(literal)
			.then(inspectNode(requirement, fallbackExecutor, explicitExecutor));
	}

	/**
	 * 把命令树注册到事件给出的 dispatcher（主类在构造期挂到游戏事件总线）。
	 * <p>
	 * 主命令与别名注册<b>同一批</b>节点构造器（只是根字面量不同），
	 * 因此两棵子树不可能出现「别名少了权限谓词」之类的漂移。
	 *
	 * @param event 命令注册事件
	 */
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(rootNode(ROOT_LITERAL,
			CommandPermissions::canInspect, InspectCommand::runFallback, InspectCommand::runExplicit));
		event.getDispatcher().register(rootNode(ALIAS_LITERAL,
			CommandPermissions::canInspect, InspectCommand::runFallback, InspectCommand::runExplicit));
		LOGGER.info("Registered command tree /{} {} (alias /{} {}) for environment {}",
			ROOT_LITERAL, INSPECT_LITERAL, ALIAS_LITERAL, INSPECT_LITERAL, event.getCommandSelection());
	}
}
