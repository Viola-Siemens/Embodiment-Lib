/**
 * 代理运行时包装（PLAN WP-3）。
 * <p>
 * 本包把 AgentScope 的 {@code HarnessAgent} 包装为 Minecraft 侧的 {@link
 * com.hexagram2021.embodimentlib.runtime.EmbodiedAgent}，职责有四：
 * <ol>
 *   <li><b>构建</b>：按 {@link com.hexagram2021.embodimentlib.api.AgentProfile} 选择
 *       OpenAI 兼容 / Anthropic 模型客户端（{@link com.hexagram2021.embodimentlib.runtime.ModelFactory}）；</li>
 *   <li><b>线程桥接</b>：LLM HTTP 在 IO 池，工具体<b>必须</b>桥到游戏线程执行
 *       （{@link com.hexagram2021.embodimentlib.runtime.ThreadBridge}），游戏线程绝不 park（PRD §6.3）；</li>
 *   <li><b>失败转文本</b>：工具异常 / 超时 / 无目标统一转成 observation 文本回喂模型，不进异常路径（PRD §4.2）；</li>
 *   <li><b>状态与审计</b>：维护 {@code AgentState} 与最近工具调用记录，供 WP-8 检查命令读取。</li>
 * </ol>
 * <p>
 * <b>可测性设计</b>：模型构造通过 {@link com.hexagram2021.embodimentlib.runtime.ModelFactory}
 * 端口注入，工具执行通过 {@link com.hexagram2021.embodimentlib.runtime.GameThreadExecutor}
 * 端口注入，循环通过 {@link com.hexagram2021.embodimentlib.runtime.AgentLoop} 纯调度器驱动。
 * 因此本包的全部行为可在无游戏进程、无网络的前提下用 JUnit 单测覆盖
 * （测试注入 {@code FakeModel} / 记录型执行器），符合 PLAN §3.8「测试中不得发起真实 LLM 调用」。
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
