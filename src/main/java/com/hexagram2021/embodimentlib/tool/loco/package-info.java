/**
 * 移动类内置工具（PLAN WP-6 ②，PRD §4.5 #7/#9/#10）。
 * <p>
 * 改变绑定实体的位置/朝向/姿态：寻路走向目标、原地跳跃、看向目标。
 * 每个工具 = 纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 薄适配器
 * （{@code *Tool}，调用 {@code PathNavigation}/{@code LivingEntity} 的移动方法）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li>非 {@code Mob}（无寻路）不得硬造路径，返回 {@code "pathfinding not supported"}；</li>
 *   <li>移动是「请求 + 快照」而非「阻塞等到位」——绝不 park 游戏线程；
 *       收敛由多次调用达成（每次调用重新评估距离）；</li>
 *   <li>目标坐标由 LLM 给（世界坐标），工具不代替模型选目标。</li>
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