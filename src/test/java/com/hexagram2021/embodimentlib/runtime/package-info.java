/**
 * WP-3 运行时包装的单测（PLAN §4 WP-3「验收标准」）。
 * <p>
 * 本包测试的构造前提：<b>不启动游戏进程、不发起任何真实 LLM 调用</b>
 * （AGENTS.md §7 第 8 条 / PLAN §3.8）。为此使用两类桩：
 * <ul>
 *   <li>{@link com.hexagram2021.embodimentlib.runtime.RecordingExecutor}：
 *       记录并手动驱动「游戏线程执行器」，用于断言桥接方向与零 park；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.runtime.FakeModel}：
 *       脚本化的 {@code Model} 实现，用于驱动 ReAct 循环而无需网络。</li>
 * </ul>
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.runtime;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
