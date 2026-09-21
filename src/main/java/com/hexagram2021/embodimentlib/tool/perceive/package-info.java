/**
 * 感知类内置工具（PLAN WP-6 ① + WP-7 ④，PRD §4.5 #1/#2/#3/#4/#5/#6）。
 * <p>
 * 只读世界、不产生任何副作用：查最近的方块、读方块状态、列库存、细看单个槽位、
 * 列附近活体、看自身状态。
 * 每个工具 = 一个纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 一个
 * 薄适配器（{@code *Tool}，负责与 {@code Level}/{@code BlockState}/注册表交互）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li>不修改世界状态（无破坏、无实体改动），失败一律文本 observation；</li>
 *   <li>扫描/查询结果必须可被 {@code ToolResults} 规约，禁止返回 null/空白；</li>
 *   <li>参数校验由纯逻辑层完成，适配器只收集世界数据；</li>
 *   <li>资源标识符（方块 id / 实体类型 id）的校验一律走 {@link
 *       com.hexagram2021.embodimentlib.tool.perceive.ResourceId}，
 *       不在各工具里重写字符集规则；</li>
 *   <li>实体/方块列表类输出必须<b>稳定排序</b>，否则同一世界的两次调用会给出不同顺序，
 *       模型会误以为世界变了。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool.perceive;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;