/**
 * 工具契约与基础设施（PLAN WP-5）。
 * <p>
 * 本包是「LLM 想动世界」与「世界真的被改动」之间唯一的通道，提供四件事：
 * <ul>
 *   <li><b>基类</b>：{@link com.hexagram2021.embodimentlib.tool.EmbodiedToolBase}
 *       —— 子类只写「对绑定实体做什么」，线程、绑定、失败转文本由基类兜住；</li>
 *   <li><b>实体绑定</b>：{@link com.hexagram2021.embodimentlib.tool.ToolContext} +
 *       {@link com.hexagram2021.embodimentlib.tool.ToolContextScope}
 *       —— 工具永远作用于「执行时所在实体」，LLM 无需也无法指定实体（PRD §4.1.3）；</li>
 *   <li><b>Griefing</b>：{@link com.hexagram2021.embodimentlib.tool.Griefing}
 *       —— 破坏性动作统一走原版 {@code mobGriefing} 规则；</li>
 *   <li><b>装配与权限</b>：{@link com.hexagram2021.embodimentlib.tool.BuiltinToolkit} +
 *       {@link com.hexagram2021.embodimentlib.tool.ToolPermissionChecker}
 *       —— 23 个工具的目录、装配、裁剪，以及 addon 的否决钩子（PRD §4.6）。</li>
 * </ul>
 *
 * <h2>本包不变式</h2>
 * <ol>
 *   <li><b>工具永不抛异常给推理循环</b>：一切失败（异常/超时/无目标/被拒/实体消失）
 *       都转成文本 observation（PRD §4.5）。</li>
 *   <li><b>工具体在游戏线程执行</b>；等待发生在 IO 线程侧，游戏线程零 park
 *       （见 {@link com.hexagram2021.embodimentlib.runtime.ThreadBridge}）。</li>
 *   <li><b>破坏性动作先过 Griefing</b>，被拒一律返回
 *       {@link com.hexagram2021.embodimentlib.tool.Griefing#DENIED}。</li>
 *   <li><b>绑定不可被工具改写</b>：{@code ToolContext} 是 record，工具只能读。</li>
 * </ol>
 *
 * <h2>可测性</h2>
 * 「工具体」与「世界操作」被刻意分开：工具体的契约（绑定、失败转文本、参数解析）
 * 可在无游戏进程下用桩 {@code ToolContext} 直接驱动验证；
 * 真正的世界改动由 WP-6/WP-7 的工具类承担，其逻辑尽量下沉到可单测的纯函数。
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
