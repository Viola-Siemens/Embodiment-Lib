/**
 * 容器类内置工具（PLAN WP-7 #20/#21，PRD §4.5 #20/#21）。
 * <p>
 * 读取与搬运方块容器（箱子、木桶、潜影盒、熔炉、漏斗…）里的物品。
 * 每个工具 = 纯逻辑类（{@code *Logic}，无 Minecraft 依赖、可单测）+ 薄适配器
 * （{@code *Tool}）。
 *
 * <h2>本子包不变式</h2>
 * <ol>
 *   <li><b>不开玩家式 GUI</b>：mob 没有 UI 状态（PRD 明确要求）。因此直接读写
 *       {@code Container} 的数据，也不触发「谁打开了容器」的追踪；</li>
 *   <li><b>无隐式「当前容器」状态</b>：目标坐标每次由模型显式传入
 *       （PRD #21 明确要求，也是上一条的必然结果）；</li>
 *   <li>前置检查与 {@code Griefing} 的取舍在 {@link com.hexagram2021.embodimentlib.tool.container.InspectContainerLogic} 与
 *       {@link com.hexagram2021.embodimentlib.tool.container.TransferContainerTool} 的 Javadoc 中写明；</li>
 *   <li>槽位文本与槽号/数量解析复用基础设施层的 {@code Slots}，
 *       不使用本子包自己的格式。</li>
 * </ol>
 *
 * @author liudongyu
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.tool.container;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
