package com.hexagram2021.embodimentlib.tool;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 方块坐标参数的解析（纯逻辑，无 Minecraft 依赖）。
 * <p>
 * 五个内置工具都要从模型那里取一个方块坐标：{@code action.place_block}（{@code hit_pos}）、
 * {@code action.use_item_on}（{@code hit_pos}）、{@code action.interact_with_block}（{@code pos}）、
 * {@code container.inspect}（{@code pos}）、{@code container.transfer}（{@code pos}）。
 * 它们分属两个子包，而「模型到底给了个什么」这件事的判定必须逐字一致。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>必须是长度为 3 的数组；</li>
 *   <li>元素可以是数字或数字字符串（模型常把 {@code 12} 写成 {@code "12"}）；</li>
 *   <li><b>不接受小数</b>：方块坐标是离散的，把 {@code 12.7} 悄悄截断成 {@code 12}
 *       会让模型以为自己命中了它没说的位置；宁可报错让它重给；</li>
 *   <li>失败文本按参数名生成（{@code "invalid input: hit_pos must be [x, y, z] of integers"}），
 *       这样模型能立刻分辨是哪一个参数写错了。</li>
 * </ul>
 *
 * @param x 方块 X
 * @param y 方块 Y
 * @param z 方块 Z
 * @param error 解析失败时的错误文本；成功时为 null（此时 x/y/z 才有意义）
 * @author liudongyu
 */
public record BlockCoordinates(int x, int y, int z, @Nullable String error) {
	/** 目标坐标参数名（{@code action.interact_with_block} / {@code container.*}）。 */
	public static final String POS = "pos";
	/** 被点击方块坐标参数名（{@code action.place_block} / {@code action.use_item_on}）。 */
	public static final String HIT_POS = "hit_pos";

	/**
	 * 构造一组合法坐标。
	 *
	 * @param x 方块 X
	 * @param y 方块 Y
	 * @param z 方块 Z
	 * @return 坐标
	 */
	public static BlockCoordinates of(int x, int y, int z) {
		return new BlockCoordinates(x, y, z, null);
	}

	/**
	 * 构造一个解析失败的结果。
	 *
	 * @param key 出错的参数名
	 * @return 失败结果
	 */
	public static BlockCoordinates invalid(String key) {
		return new BlockCoordinates(0, 0, 0, "invalid input: " + key + " must be [x, y, z] of integers");
	}

	/**
	 * 从模型参数中解析方块坐标。
	 *
	 * @param input 模型参数
	 * @param key 参数名（{@link #POS} 或 {@link #HIT_POS}）
	 * @return 解析结果；{@code error() != null} 时该文本即为 observation
	 */
	public static BlockCoordinates parse(Map<String, Object> input, String key) {
		Object raw = input.get(key);
		if (!(raw instanceof List<?> list) || list.size() != 3) {
			return invalid(key);
		}
		Integer x = toInt(list.get(0));
		Integer y = toInt(list.get(1));
		Integer z = toInt(list.get(2));
		if (x == null || y == null || z == null) {
			return invalid(key);
		}
		return of(x, y, z);
	}

	private static @Nullable Integer toInt(@Nullable Object value) {
		if (value instanceof Number number) {
			// 只接受整数值的 Number：12.7 是模型的表述错误，不是「12 附近」。
			double asDouble = number.doubleValue();
			return asDouble == Math.floor(asDouble) ? (int) asDouble : null;
		}
		if (value instanceof String text) {
			try {
				return Integer.valueOf(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}
