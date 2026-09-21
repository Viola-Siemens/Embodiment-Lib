package com.hexagram2021.embodimentlib.tool.perceive;

import com.hexagram2021.embodimentlib.tool.Slots;

import java.util.List;

/**
 * {@code perceive.inventory_slot} 的纯逻辑层（PLAN WP-7 #4，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（解析库存容器、取槽内 {@code ItemStack}、读组件）在
 * {@link InventorySlotTool} 中完成；本类负责<b>单槽详细描述的形状</b>。
 * 槽号解析与校验属跨工具共性，已上提到 {@link Slots}（{@code parseIndex} /
 * {@code validateIndex}），此处不再重复。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>无库存实体：{@code "inventory not supported on this entity"}（{@link Slots#NO_INVENTORY}）；</li>
 *   <li>槽号缺失/非法：{@code "invalid input: slot must be a non-negative integer"}；</li>
 *   <li>槽号越界：{@code "invalid input: slot out of range"}；</li>
 *   <li>槽为空：{@code "slot empty"}（{@link Slots#SLOT_EMPTY}）；</li>
 *   <li>有物品：{@code "slot 3: 1x minecraft:diamond_sword (custom name \"Excalibur\"; enchantments sharpness 5; durability 250/250)"}。</li>
 * </ul>
 *
 * <h2>「组件」的 0.1 范围</h2>
 * PRD §4.5 #4 要求「components, enchantments, custom name」。把 {@code ItemStack}
 * 的<b>全部</b>组件倒给模型是不可行的：原版每个物品都挂着十几到几十个默认组件
 * （{@code max_stack_size}、{@code repair_cost}、{@code rarity}…），一次调用就能
 * 吃掉模型可观的上下文预算，而这堆默认值里没有一个能帮助决策。
 * 因此 0.1 只报告<b>玩家在物品提示框上看得见</b>的那几类：自定义名、附魔、耐久、可消耗。
 * 这是有意的取舍，已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class InventorySlotLogic {
	private InventorySlotLogic() {
	}

	/**
	 * 规约单槽的详细描述。
	 *
	 * @param slot 槽号
	 * @param itemId 物品完整资源标识符
	 * @param count 数量
	 * @param traits 附加特征文本（自定义名/附魔/耐久/可消耗），可为空列表
	 * @return observation 文本
	 */
	public static String describe(int slot, String itemId, int count, List<String> traits) {
		String head = Slots.line(slot, itemId, count);
		return traits.isEmpty() ? head : head + " (" + String.join("; ", traits) + ")";
	}

	/**
	 * 构造「自定义名」特征文本。
	 *
	 * @param name 自定义名（已取为纯文本）
	 * @return 特征文本
	 */
	public static String customName(String name) {
		return "custom name \"" + name + "\"";
	}

	/**
	 * 构造「附魔」特征文本。
	 *
	 * @param entries 形如 {@code "sharpness 5"} 的条目（调用方已排序）
	 * @return 特征文本
	 */
	public static String enchantments(List<String> entries) {
		return "enchantments " + String.join(", ", entries);
	}

	/**
	 * 构造「耐久」特征文本。
	 *
	 * @param remaining 剩余耐久
	 * @param maxDamage 最大耐久
	 * @return 特征文本
	 */
	public static String durability(int remaining, int maxDamage) {
		return "durability " + remaining + "/" + maxDamage;
	}

	/**
	 * 构造「可消耗」特征文本。
	 *
	 * @return 特征文本
	 */
	public static String consumable() {
		return "consumable";
	}
}
