package com.hexagram2021.embodimentlib.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /embodimentlib inspect} 的权限判定（PLAN WP-8）。
 * <p>
 * 判定本体只有一行（{@code permissions.hasPermission(REQUIRED)}），之所以仍抽成独立类，
 * 是因为「谁能看智能体的会话预览」是<b>安全相关</b>的：它必须有一个可被单测直接命中的入口，
 * 而不是以 lambda 形态埋在命令注册里、只能靠读代码确认。
 * {@link PermissionSet} 是函数式接口（{@code boolean hasPermission(Permission)}），
 * 因此放行/拒绝两条分支都能在纯 JUnit 下构造出来验证。
 *
 * <h2>与 PLAN 原文的偏差（26.1.2 权限 API 更名）</h2>
 * PLAN 写的是 {@code ctx.getSource().hasPermission(2)}。26.1.2 已把「整数 op 等级」换成
 * {@code PermissionSet} + {@link Permissions} 常量：{@code CommandSourceStack} 上<b>不再有</b>
 * {@code hasPermission(int)}，改为 {@code permissions()} 返回 {@link PermissionSet}。
 * 旧「权限等级 2」的等价物是 {@link Permissions#COMMANDS_GAMEMASTER}
 * （{@code HasCommandLevel(PermissionLevel.GAMEMASTERS)}，而 {@code GAMEMASTERS} 的 id 恰为 2）。
 * 语义不变：仍然是「操作员二级及以上」，已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class CommandPermissions {
	/**
	 * 执行 {@code inspect} 所需的权限。
	 * <p>
	 * 与「操作员权限等级 2」等价：报告会打印会话预览（可能含玩家说过的话），
	 * 属于「运维/开发者可见」而非人人可见的内容。
	 */
	public static final Permission REQUIRED = Permissions.COMMANDS_GAMEMASTER;

	/** 权限不足时的提示文本。 */
	public static final String DENIED_MESSAGE =
		"Requires operator permission level 2 (gamemaster) to inspect an agent.";

	private CommandPermissions() {
	}

	/**
	 * 权限集是否允许执行检查命令。
	 *
	 * @param permissions 命令源的权限集
	 * @return 允许返回 true
	 */
	public static boolean canInspect(PermissionSet permissions) {
		return permissions.hasPermission(REQUIRED);
	}

	/**
	 * 便捷重载：直接从命令源判定。
	 * <p>
	 * 该重载被命令树的 {@code requires(...)} 谓词引用；权限不足时 Brigadier 会把这个命令
	 * 从可解析集合中移除（等价于「命令不存在」），这正是原版对无权限者的一贯行为。
	 *
	 * @param source 命令源
	 * @return 允许返回 true
	 */
	public static boolean canInspect(CommandSourceStack source) {
		return canInspect(source.permissions());
	}

	/**
	 * 权限不足时的反馈组件。
	 *
	 * @return 文本组件
	 */
	public static Component deniedMessage() {
		return Component.literal(DENIED_MESSAGE);
	}
}
