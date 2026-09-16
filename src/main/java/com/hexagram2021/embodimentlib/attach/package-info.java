/**
 * 实体附着与运行期智能体注册表（PLAN WP-2）。
 * <p>
 * 本包提供两件事：
 * <ul>
 *   <li><b>附着</b>：给任意 {@code LivingEntity} 挂两个短字符串
 *       （{@code agent_type} / {@code session_id}），作为「这个实体是一个智能体」的标记；
 *       重量级运行对象（{@code EmbodiedAgent} / {@code Toolkit}）<b>不</b>存 attachment
 *       （PRD §6.4），而由 {@link com.hexagram2021.embodimentlib.attach.AgentRegistry} 持有。</li>
 *   <li><b>注册表</b>：按 {@code (host-side, session-id)} 维度管理智能体生命周期，
 *       SERVER 与 CLIENT 各持一份实例，永不共享（PRD §4.1.1 / §6.3 隔离硬约束）。</li>
 * </ul>
 * <p>
 * 本包刻意不依赖 FML / Minecraft 的注册表初始化时机：附着注册（{@link com.hexagram2021.embodimentlib.attach.AttachmentTypes}）
 * 与事件监听（{@link com.hexagram2021.embodimentlib.attach.AgentLifecycle}）各自独立，
 * 核心逻辑（{@link com.hexagram2021.embodimentlib.attach.AgentRegistry}、
 * {@link com.hexagram2021.embodimentlib.attach.AgentAttachment}）不触及 Minecraft 注册表，可直接被 JUnit 单测覆盖。
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.attach;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
