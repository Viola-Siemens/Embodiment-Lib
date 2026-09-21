/**
 * 行动类内置工具（PLAN WP-6 ②，PRD §4.5 #11/#13/#16）。
 * <p>
 * 对世界产生副作用：挖方块、使用物品、攻击目标。破坏性动作（挖/攻击）
 * 必须先过 {@link com.hexagram2021.embodimentlib.tool.Griefing}。
 * 每个工具 = 纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 薄适配器
 * （{@code *Tool}）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li>破坏性动作（挖/攻击）先 {@code Griefing.denied(ctx)}，被拒返回
 *       {@code "griefing denied"}，不读取、不修改世界；</li>
 *   <li>攻击绝不自动选目标（PRD：#16「The LLM names the victim」）；</li>
 *   <li>攻击/挖掘的伤害与掉落由原版 {@code hurtServer}/{@code destroyBlock} 管道完成，
 *       工具不绕过事件（保证 addon 能监听/拦截）。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool.action;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;