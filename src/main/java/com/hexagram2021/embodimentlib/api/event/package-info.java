/**
 * 渲染/展示事件（PLAN WP-6 ③，PRD §4.5 #23）。
 * <p>
 * 本包只放「库产生、addon 消费」的事件类。0.1 只有一个 {@link com.hexagram2021.embodimentlib.api.event.AgentSayEvent}：
 * {@code meta.say} 工具执行时由库 post，addon 订阅后自行决定如何渲染（聊天、气泡、动作栏、语音——库不画任何东西）。
 * <p>
 * 事件类必然携带游戏对象（执行实体），因此本包<b>依赖 Minecraft / NeoForge 类型</b>——
 * 这与 {@code api} 父包的「无 Minecraft 依赖」约定不同，是刻意的：事件是游戏侧 API，
 * 不可能在脱离游戏对象的表单下定义。纯 JUnit 可测的只有事件的数据规约（getter / 构造校验）。
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.api.event;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;