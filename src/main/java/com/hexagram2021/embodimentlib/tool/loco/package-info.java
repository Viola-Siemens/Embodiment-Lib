/**
 * 移动类内置工具（PLAN WP-6 ② + WP-7 ③，PRD §4.5 #7/#8/#9/#10）。
 * <p>
 * 改变绑定实体的位置/朝向/姿态：寻路走向坐标或走向实体、原地跳跃、看向目标。
 * 每个工具 = 纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 薄适配器
 * （{@code *Tool}，调用 {@code PathNavigation}/{@code LivingEntity} 的移动方法）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li>非 {@code Mob}（无寻路）不得硬造路径，返回 {@code "pathfinding not supported"}；</li>
 *   <li>移动是「请求 + 快照」而非「阻塞等到位」——绝不 park 游戏线程；
 *       收敛由多次调用达成（每次调用重新评估距离）；</li>
 *   <li>目标坐标/目标实体由 LLM 给，工具不代替模型选目标；</li>
 *   <li>「还差多远」的文本形态只有一份（{@link
 *       com.hexagram2021.embodimentlib.tool.loco.MoveToLogic#distanceRemaining(double)}），
 *       {@code move_to} 与 {@code move_to_entity} 必须逐字一致；</li>
 *   <li>持续性跟随（{@code action.follow_entity}）<b>不</b>属于本子包：
 *       它带持久状态，需要生命周期清理，故归 {@code action}。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool.loco;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;