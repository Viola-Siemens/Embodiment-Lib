package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.Map;

/**
 * {@code perceive.block_state_at} 的纯逻辑层（PLAN WP-6 #2，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（读 {@code BlockState}、收集属性值）在 {@link BlockStateAtTool} 中完成；
 * 本类负责<b>状态串的序列化</b>——把「方块 id + 有序属性表」规约为
 * `minecraft:oak_door[half=lower,facing=east]` 这样的标准形态（PRD 契约示例）。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>有属性：{@code <id>[<key>=<value>,...]}，键按属性名升序排列（与 Minecraft 的
 *       {@code BlockState} toString 约定一致，保证输出稳定可预期）；</li>
 *   <li>无属性：仅 {@code <id>}；</li>
 *   <li>空气方块：{@value #AIR}；世界外/未加载：{@value #VOID}。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class BlockStateLogic {
	/** 空气方块的 observation。 */
	public static final String AIR = "air";
	/** 世界外（越界/未加载区块）的 observation。 */
	public static final String VOID = "void";

	private BlockStateLogic() {
	}

	/**
	 * 判断坐标是否在世界的建造高度内。
	 *
	 * @param y 方块 Y 坐标
	 * @param minY 世界最小建造高度（含）
	 * @param maxY 世界最大建造高度（含）
	 * @return 在界内返回 true
	 */
	public static boolean isInBuildHeight(int y, int minY, int maxY) {
		return y >= minY && y <= maxY;
	}

	/**
	 * 序列化方块状态为 observation 文本。
	 * <p>
	 * 属性表按属性名升序输出（调用方原地排序，或保证已排序），
	 * 生成 {@code minecraft:oak_door[half=lower,facing=east]}。
	 *
	 * @param fullId 方块完整资源标识符（如 {@code "minecraft:oak_door"}）
	 * @param properties 属性名 → 属性值（已按 key 升序）
	 * @return 序列化结果；无属性时仅返回 id
	 */
	public static String serialize(String fullId, Map<String, String> properties) {
		if (properties.isEmpty()) {
			return fullId;
		}
		StringBuilder sb = new StringBuilder(fullId).append('[');
		boolean first = true;
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return sb.append(']').toString();
	}

	/**
	 * 规约完整 observation：世界外/未加载 → {@link #VOID}；空气 → {@link #AIR}；
	 * 否则走 {@link #serialize}。
	 *
	 * @param inWorld 坐标是否在世界内且区块已加载
	 * @param isAir 方块是否为空气
	 * @param fullId 方块完整资源标识符
	 * @param properties 属性名 → 属性值（已按 key 升序）
	 * @return observation 文本
	 */
	public static String describe(boolean inWorld, boolean isAir, String fullId, Map<String, String> properties) {
		if (!inWorld) {
			return VOID;
		}
		if (isAir) {
			return AIR;
		}
		return serialize(fullId, properties);
	}
}