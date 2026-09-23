/**
 * 调试命令与可观测性（PLAN WP-8，PRD §4.7）。
 * <p>
 * 0.1 只提供一个运维命令：{@code /embodimentlib inspect [<entity>]}（含 {@code /emb inspect} 别名），
 * 打印某个实体智能体的五类信息：{@code agent-type} / {@code session-id}、解析到的 profile
 * （协议与模型名，<b>绝不含 api_key</b>）、最近工具调用、会话预览、当前运行状态。
 *
 * <h2>本包的分层（与 WP-5 的「纯逻辑 + 薄适配器」同一思路）</h2>
 * <ol>
 *   <li>{@link com.hexagram2021.embodimentlib.command.InspectData}：<b>纯逻辑</b>数据载体。
 *       只含报告需要的事实（含一个<b>只带协议与模型名</b>的 profile 视图）；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.command.InspectReportBuilder}：<b>纯逻辑</b>格式化。
 *       报告的全部文字都出自这里，可在无游戏进程下单测；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.command.EmbodimentCommandTree}：命令树装配。
 *       树构造对命令源类型泛型化，因此单测可以用哑 source 让真实 Brigadier 解析
 *       {@code emb inspect ...} 并断言别名与结构；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.command.InspectCommand}：适配器。
 *       解析目标实体 → 读附着/注册表/配置/会话 → 组装 {@code InspectData} → 输出；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.command.CommandPermissions}：权限判定
 *       （{@code PermissionSet} 是函数式接口，因此放行/拒绝两条分支都能单测）。</li>
 * </ol>
 *
 * <h2>本包不变式</h2>
 * <ol>
 *   <li><b>隐私硬约束</b>：{@code InspectData} 里压根没有 api_key 与 base_url 字段，
 *       因此「报告不含密钥」是<b>结构上不可能违反</b>的，而不是靠每处输出自觉过滤；</li>
 *   <li>会话内容只以<b>截断</b>形态出现（预览默认 200 字符），且截断在纯逻辑层完成；</li>
 *   <li>命令执行结果只回给执行者（{@code sendSuccess(..., false)}），不广播到全服聊天；
 *       失败用 {@code sendFailure}，并返回 0 让命令方块/脚本能察觉；</li>
 *   <li>命令不修改世界、不改智能体状态，纯只读。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.command;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
