/**
 * 元操作类内置工具（PLAN WP-6 ③，PRD §4.5 #22/#23）。
 * <p>
 * 控制智能体自身行为而非世界：等 N tick、说一句话。
 * 每个工具 = 纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 薄适配器
 * （{@code *Tool}）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li>{@code meta.wait} 非阻塞：返回 {@code "waited"} 后由循环驱动侧决定何时
 *       恢复推理（0.1 未接线，见 {@code WaitTool} 说明），绝不 park 游戏线程；</li>
 *   <li>{@code meta.say} 通过 {@code NeoForge.EVENT_BUS} post
 *       {@link com.hexagram2021.embodimentlib.api.event.AgentSayEvent}，
 *       库不绘制任何东西（PRD §4.5 #23/§4.7）。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool.meta;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;