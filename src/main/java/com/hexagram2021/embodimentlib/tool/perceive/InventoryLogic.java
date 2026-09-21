package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.List;

/**
 * {@code perceive.inventory_contents} 的纯逻辑层（PLAN WP-6 #3，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（判定实体是否实现 {@code Container}、枚举槽位、取物品 id）在
 * {@link InventoryContentsTool} 中完成；本类负责槽位行的规约与文本拼接。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>单槽行：{@code "slot 0: 64x minecraft:cobblestone"}；</li>
 *   <li>可损物品追加耐久：{@code "slot 1: 1x minecraft:iron_pickaxe (durability 100/250)"}；</li>
 *   <li>无库存实体：{@code "inventory not supported on this entity"}；</li>
 *   <li>有库存但全空：{@code "inventory empty"}。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class InventoryLogic {
	/** 实体没有库存时的 observation。 */
	public static final String NOT_SUPPORTED = "inventory not supported on this entity";
	/** 有库存但全空时的 observation。 */
	public static final String EMPTY = "inventory empty";

	private InventoryLogic() {
	}

	/**
	 * 构造单槽行（无耐久）。
	 *
	 * @param slot 槽位索引
	 * @param itemId 物品完整资源标识符（如 {@code "minecraft:cobblestone"}）
	 * @param count 数量
	 * @return 形如 {@code "slot 0: 64x minecraft:cobblestone"} 的文本
	 */
	public static String slotLine(int slot, String itemId, int count) {
		return "slot " + slot + ": " + count + "x " + itemId;
	}

	/**
	 * 构造单槽行（带耐久）。
	 *
	 * @param slot 槽位索引
	 * @param itemId 物品完整资源标识符
	 * @param count 数量
	 * @param damage 已损坏值
	 * @param maxDamage 最大耐久
	 * @return 形如 {@code "slot 1: 1x minecraft:iron_pickaxe (durability 100/250)"} 的文本
	 */
	public static String slotLineDurability(int slot, String itemId, int count, int damage, int maxDamage) {
		return slotLine(slot, itemId, count) + " (durability " + (maxDamage - damage) + "/" + maxDamage + ")";
	}

	/**
	 * 把槽位行列表拼成一条 observation。
	 *
	 * @param lines 槽位行（已按槽序）
	 * @return 用 {@code "; "} 连接的单行文本；空列表返回 {@link #EMPTY}
	 */
	public static String join(List<String> lines) {
		if (lines.isEmpty()) {
			return EMPTY;
		}
		return String.join("; ", lines);
	}
}