package com.hexagram2021.embodimentlib.tool;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 槽位相关的纯助手：文本规约 + 槽号/数量参数解析（无 Minecraft 依赖）。
 * <p>
 * 有五个内置工具都要跟「第几号槽里有多少东西」打交道：{@code perceive.inventory_contents}、
 * {@code perceive.inventory_slot}、{@code action.drop_item}、{@code container.inspect}、
 * {@code container.transfer}。它们共享两类极易写歪的东西：
 * <ul>
 *   <li><b>文本形状</b>——observation 是模型理解世界的唯一依据，格式漂移会直接降低它的判断质量；</li>
 *   <li><b>参数解析</b>——模型经常把数字写成字符串（{@code "5"}）、给负数、给越界槽号，
 *       每个工具各写一遍必然出现「有的工具回落默认值、有的报错」的不一致。</li>
 * </ul>
 * 故集中到基础设施层，与 {@link ToolResults}（通用参数解析 / observation 规约）同层。
 * 本类只处理<b>槽位语义</b>的部分，通用参数解析仍归 {@code ToolResults}。
 *
 * <h2>文本契约</h2>
 * <ul>
 *   <li>单槽行：{@code "slot 0: 64x minecraft:cobblestone"}；</li>
 *   <li>可损物品追加耐久：{@code "slot 1: 1x minecraft:iron_pickaxe (durability 100/250)"}；
 *       耐久写<b>剩余/最大</b>而非已损值——模型关心「还能用多久」；</li>
 *   <li>多行用 {@code "; "} 连接成一行（observation 是单行文本，换行会在日志里断行）；</li>
 *   <li>「空」有两种表述：实体库存为空 = {@link #INVENTORY_EMPTY}，
 *       方块容器为空 = {@link #CONTAINER_EMPTY}——对模型这是两个不同的事实。</li>
 * </ul>
 *
 * @author liudongyu
 */
public final class Slots {
	/** 实体没有库存时的 observation（PRD §4.5 #3 约定文本）。 */
	public static final String NO_INVENTORY = "inventory not supported on this entity";
	/** 实体有库存但全空时的 observation。 */
	public static final String INVENTORY_EMPTY = "inventory empty";
	/** 方块容器存在但全空时的 observation。 */
	public static final String CONTAINER_EMPTY = "container empty";
	/** 指定槽位为空时的 observation。 */
	public static final String SLOT_EMPTY = "slot empty";
	/** slot 参数缺失、为负或非整数时的 observation。 */
	public static final String INVALID_SLOT = "invalid input: slot must be a non-negative integer";
	/** slot 参数超出容器槽数时的 observation。 */
	public static final String SLOT_OUT_OF_RANGE = "invalid input: slot out of range";
	/** count 参数缺失、非正或非整数时的 observation。 */
	public static final String INVALID_COUNT = "invalid input: count must be a positive integer";

	private Slots() {
	}

	/**
	 * 构造单槽行（无耐久）。
	 *
	 * @param slot 槽位索引
	 * @param itemId 物品完整资源标识符（如 {@code "minecraft:cobblestone"}）
	 * @param count 数量
	 * @return 形如 {@code "slot 0: 64x minecraft:cobblestone"} 的文本
	 */
	public static String line(int slot, String itemId, int count) {
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
	public static String lineWithDurability(int slot, String itemId, int count, int damage, int maxDamage) {
		return line(slot, itemId, count) + " (durability " + (maxDamage - damage) + "/" + maxDamage + ")";
	}

	/**
	 * 把槽位行列表拼成一条 observation，空列表按「实体库存为空」表述。
	 *
	 * @param lines 槽位行（已按槽序）
	 * @return 单行文本；空列表返回 {@link #INVENTORY_EMPTY}
	 */
	public static String join(List<String> lines) {
		return join(lines, INVENTORY_EMPTY);
	}

	/**
	 * 把槽位行列表拼成一条 observation，空列表由调用方指定表述。
	 *
	 * @param lines 槽位行（已按槽序）
	 * @param emptyText 列表为空时的文本（如 {@link #CONTAINER_EMPTY}）
	 * @return 单行文本
	 */
	public static String join(List<String> lines, String emptyText) {
		return lines.isEmpty() ? emptyText : String.join("; ", lines);
	}

	/**
	 * 解析槽号参数（兼容模型把数字写成字符串）。
	 *
	 * @param raw 原始参数；可为 null
	 * @return 非负整数槽号；缺失、为负或非法时为 null
	 */
	public static @Nullable Integer parseIndex(@Nullable Object raw) {
		Integer value = toInt(raw);
		return value != null && value >= 0 ? value : null;
	}

	/**
	 * 解析数量参数（兼容模型把数字写成字符串）。
	 *
	 * @param raw 原始参数；可为 null
	 * @return 正整数数量；缺失、非正或非法时为 null
	 */
	public static @Nullable Integer parseCount(@Nullable Object raw) {
		Integer value = toInt(raw);
		return value != null && value >= 1 ? value : null;
	}

	/**
	 * 校验槽号是否落在容器范围内。
	 *
	 * @param slot 已解析的槽号；为 null 表示参数缺失/非法
	 * @param size 容器槽数
	 * @return 合法时返回 null；否则返回应回喂模型的错误文本
	 */
	public static @Nullable String validateIndex(@Nullable Integer slot, int size) {
		if (slot == null) {
			return INVALID_SLOT;
		}
		return slot < size ? null : SLOT_OUT_OF_RANGE;
	}

	private static @Nullable Integer toInt(@Nullable Object raw) {
		if (raw instanceof Number number) {
			return number.intValue();
		}
		if (raw instanceof String text) {
			try {
				return Integer.valueOf(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}
