/**
 * 公共 API：对 addon 作者暴露的稳定接口。
 * <br/>
 * 负责：
 * <br/>
 * <ol>
 *   <li>宿主侧枚举（SERVER / CLIENT 双端隔离语义）</li>
 *   <li>模型提供方 Profile 值对象（构造时校验）</li>
 *   <li>渲染事件（{@link com.hexagram2021.embodimentlib.api.event.AgentSayEvent}）
 *       —— 事件必须携带执行实体，是<b>唯一的例外</b>：它依赖 Minecraft / NeoForge 类型，
 *       见 {@code api/event/package-info} 的说明；其余类不包含任何 Minecraft / NeoForge
 *       运行时依赖，可纯 JUnit 测试</li>
 * </ol>
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.api;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
