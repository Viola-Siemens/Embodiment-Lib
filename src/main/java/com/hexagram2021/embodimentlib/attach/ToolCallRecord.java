package com.hexagram2021.embodimentlib.attach;

import java.util.Objects;

/**
 * 一次工具调用的审计记录（PLAN WP-2 ②：WP-2 定义最小形态，WP-6 产生记录）。
 * <p>
 * 由 {@link RegistryEntry} 以环形双端队列保存最近 N 条，供
 * {@code /embodimentlib inspect}（WP-8）展示「最近工具调用」。
 * <p>
 * <b>安全约束</b>：{@code input} 与 {@code observation} 是<b>已截断</b>的展示文本。
 * 调用方在构造前必须完成截断（{@link #MAX_TEXT_LENGTH}），因为工具输入可能包含
 * 玩家聊天内容，而 PRD §4.1.1 / §6.3 要求此类文本不得随网络包下发到客户端——
 * 截断放在记录侧可保证无论谁读取都拿不到超长原文。
 *
 * @param toolName 工具名（如 {@code "move_to"}）
 * @param input 工具输入 JSON 的截断文本
 * @param observation 回喂模型的 observation 文本（成功为结果，失败为错误描述）的截断文本
 * @param timestampMillis 记录时刻的 {@code System.currentTimeMillis()}
 */
public record ToolCallRecord(String toolName, String input, String observation, long timestampMillis) {
	/** 单条文本字段的最大字符数（超出部分截断并追加省略号）。 */
	public static final int MAX_TEXT_LENGTH = 512;
	/** 截断时追加的省略标记。 */
	public static final String TRUNCATION_SUFFIX = "...";

	/**
	 * 紧凑构造器：校验非空并统一执行截断。
	 * <p>
	 * 截断在此处强制执行，而不是依赖调用方自觉：这是「不泄漏超长文本」合约的唯一落点。
	 *
	 * @throws NullPointerException 任一字段为 null
	 */
	public ToolCallRecord {
		Objects.requireNonNull(toolName, "toolName");
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(observation, "observation");
		input = truncate(input);
		observation = truncate(observation);
	}

	/**
	 * 截断到 {@link #MAX_TEXT_LENGTH} 个字符。
	 * <p>
	 * 含省略标记的总长度不超过上限，便于按固定宽度渲染检查报告。
	 *
	 * @param text 原始文本
	 * @return 长度不超过 {@link #MAX_TEXT_LENGTH} 的文本
	 */
	public static String truncate(String text) {
		if (text.length() <= MAX_TEXT_LENGTH) {
			return text;
		}
		return text.substring(0, MAX_TEXT_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
	}
}
