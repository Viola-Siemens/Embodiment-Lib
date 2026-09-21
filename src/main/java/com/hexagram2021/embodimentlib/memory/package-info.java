/**
 * 会话与记忆持久化（PLAN WP-4，PRD §4.4 / §6.4）。
 * <p>
 * 按 {@code (host-side, session-id)} 持久化会话历史、工作记忆与待办清单，
 * 两棵树永不合并：
 * <pre>
 * SERVER: &lt;world dir&gt;/embodimentlib/sessions/&lt;session-id&gt;/
 * CLIENT: &lt;config dir&gt;/embodimentlib/sessions/&lt;session-id&gt;/
 * </pre>
 *
 * <h2>本包的分层（与 WP-5 的「纯逻辑 + 薄适配器」同一思路）</h2>
 * <ol>
 *   <li>{@link com.hexagram2021.embodimentlib.memory.SessionPaths}：<b>纯逻辑</b>。
 *       目录布局、文件名、session-id 合法性（含路径穿越与 Windows 设备名拦截）。
 *       零 Minecraft / 零 NeoForge 依赖，可在纯 JUnit 下完整覆盖；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.memory.SessionSink} +
 *       {@link com.hexagram2021.embodimentlib.memory.SessionData}：<b>纯逻辑</b>。
 *       工作记忆 / 待办的增删语义与「写穿」时机，落盘动作通过端口注入；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.memory.SessionStore}：<b>适配器</b>。
 *       解析本端根目录（世界目录 / 配置目录）、Gson 读写、内存缓存、
 *       AgentScope 状态存储与历史文件查询；</li>
 *   <li>{@link com.hexagram2021.embodimentlib.memory.SessionLifecycle}：
 *       生命周期事件接线（实体离开/死亡 → flush + 摘缓存；存档/停服 → flushAll）。</li>
 * </ol>
 *
 * <h2>本包不变式</h2>
 * <ol>
 *   <li>session-id 必须先过 {@code SessionPaths.isValidSessionId}，否则
 *       <b>响亮地抛 {@code IllegalArgumentException}</b>——静默清洗会把
 *       {@code "../evil"} 变成一个合法目录名，让「越权写了别的会话」变成无人察觉的事实；</li>
 *   <li>根目录不可用（服务器尚未起世界 / 客户端尚未就绪）时<b>降级而非崩溃</b>：
 *       {@code load} 仍返回可用的内存态，落盘变成 no-op 并只告警一次；</li>
 *   <li>落盘一律「临时文件 + 原子改名」，避免崩溃/断电留下半截 JSON；</li>
 *   <li>读取失败（文件损坏、权限不足）不得抛出：会话文件坏掉不该让实体加载失败，
 *       以空数据开始并在日志中留下证据；</li>
 *   <li>会话历史（对话记录）由 AgentScope 的 {@code AgentStateStore} 负责，
 *       本包只负责<b>把它指到正确的目录</b>并提供路径查询——本库不解析
 *       AgentScope 的消息 schema（那是 WP-8 展示层的事）。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.memory;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
