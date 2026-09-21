package com.hexagram2021.embodimentlib.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块读取的通用判定（需要 Minecraft 类型，故不放进 {@code *Logic} 纯逻辑层）。
 * <p>
 * 「这个坐标现在能不能读/写」和「这个坐标上是什么方块」是每个方块类工具都要问的问题
 * （挖、放、用物品点、空手交互、容器读写…共六个）。写六遍的结果是六份细微不同的
 * 边界判断——而边界判断写错的后果很具体：区块未加载时读 {@code BlockState} 会拿到
 * 空气、并在服务端日志里留下「读未加载区块」的噪音，把世界之外的坐标交给
 * {@code setBlock} 则可能抛异常。故集中到本类。
 *
 * @author liudongyu
 */
public final class Blocks {
	private Blocks() {
	}

	/**
	 * 该坐标是否可安全读写：区块已加载且 Y 在世界高度范围内。
	 *
	 * @param level 世界
	 * @param pos 方块坐标
	 * @return 可读写返回 true
	 */
	public static boolean isWritable(Level level, BlockPos pos) {
		return level.isLoaded(pos) && pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY();
	}

	/**
	 * 取某坐标方块的完整资源标识符（{@code "minecraft:oak_door"}）。
	 * <p>
	 * 调用方必须先确认坐标可读写（见 {@link #isWritable}），否则读到的是空气。
	 *
	 * @param level 世界
	 * @param pos 方块坐标
	 * @return 资源标识符文本
	 */
	public static String idAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return key.getNamespace() + ":" + key.getPath();
	}
}
