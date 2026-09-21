package com.hexagram2021.embodimentlib.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * 容器访问助手（需要 Minecraft 类型，故不放进 {@code *Logic} 纯逻辑层）。
 * <p>
 * 「怎么从一个实体身上找到它的库存」是一个只该有一份答案的问题，而它涉及两个
 * 26.1.2 的别扭细节：
 * <ul>
 *   <li>{@link Player} <b>不是</b> {@link Container}——它实现的是
 *       {@code ContainerUser}（只用于「谁打开了这个容器」的追踪），库存要通过
 *       {@code getInventory()} 解包。若按 {@code instanceof Container} 判断，
 *       会得出「玩家没有库存」这一明显错误的结论；</li>
 *   <li>方块侧必须同时满足「有 {@link BlockEntity}」与「该 BE 实现 {@link Container}」
 *       （熔炉、漏斗、潜影盒…），只判其一会把牌子、告示牌之类误判成容器。</li>
 * </ul>
 * 因此 {@code perceive.inventory_contents / inventory_slot}、{@code action.drop_item}、
 * {@code container.inspect / transfer} 全部复用本类，而不是各写一遍。
 *
 * @author liudongyu
 */
public final class Containers {
	private Containers() {
	}

	/**
	 * 解析绑定实体的库存容器。
	 *
	 * @param ctx 工具上下文
	 * @return 库存容器；实体没有库存时返回 null
	 */
	public static @Nullable Container inventoryOf(ToolContext ctx) {
		if (ctx.entity() instanceof Player player) {
			return player.getInventory();
		}
		return ctx.entity() instanceof Container container ? container : null;
	}

	/**
	 * 解析某坐标方块上的容器。
	 * <p>
	 * 双箱（大箱子）只返回坐标所在的<b>半边</b>：原版的双箱合并发生在
	 * {@code ChestBlock#getContainer} 的玩家式访问路径上，而我们刻意不开玩家式 GUI
	 * （PRD §4.5 #20：mob 没有 UI 状态），故不合并。这一点已在 PLAN 偏差记录中写明。
	 *
	 * @param level 世界
	 * @param pos 方块坐标
	 * @return 容器；该处无容器时返回 null
	 */
	public static @Nullable Container blockContainerAt(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof Container container ? container : null;
	}
}
