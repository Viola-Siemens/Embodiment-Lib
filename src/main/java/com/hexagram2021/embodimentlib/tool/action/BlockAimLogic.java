package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.BlockCoordinates;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「瞄准一个方块」的参数解析纯逻辑（PLAN WP-7 #12/#14/#15 共用，无 Minecraft 依赖）。
 * <p>
 * 三个工具都要从模型那里取「哪个方块、哪一面、哪只手」。坐标解析属跨子包共性，
 * 已上提到 {@link BlockCoordinates}；本类负责<b>朝向与手别</b>，
 * 并把三者合成一个可直接消费的 {@link Aim}。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@code hit_pos}：见 {@link BlockCoordinates}（长度 3 的整数数组）；</li>
 *   <li>{@code face}：{@code down/up/north/south/east/west} 六选一，缺省 {@code up}；</li>
 *   <li>{@code hand}：{@code main}/{@code off}，缺省 {@code main}（复用
 *       {@link UseItemLogic#parseHand(Map)}，与 {@code action.use_item} 同一语义）。</li>
 * </ul>
 *
 * <h2>为什么 face 用字符串而不是 {@code Direction}</h2>
 * 本类必须保持无 Minecraft 依赖才能在纯 JUnit 下覆盖；{@code Direction} 的解析留在
 * 适配器里（{@code Direction.byName}），而<b>合法性判定</b>在此完成，
 * 保证「纯逻辑层放行、世界侧解析失败」不可能发生。
 *
 * @author liudongyu
 */
public final class BlockAimLogic {
	/** {@code face} 不是六个合法朝向之一时的 observation。 */
	public static final String INVALID_FACE =
		"invalid input: face must be one of down, up, north, south, east, west";
	/** {@code hand} 不是 main/off 时的 observation。 */
	public static final String INVALID_HAND = "invalid input: hand must be \"main\" or \"off\"";
	/** 允许的朝向（与 {@code Direction} 的序列化名逐字一致，小写）。 */
	public static final List<String> FACES = List.of("down", "up", "north", "south", "east", "west");
	/** 缺省朝向：向上放置是最常见的意图。 */
	public static final String DEFAULT_FACE = "up";

	/** 已解析的瞄准参数；{@code error} 非空时其余字段无意义。 */
	public record Aim(int x, int y, int z, String face, String hand, @Nullable String error) {
		/**
		 * 构造一组合法瞄准参数。
		 *
		 * @param x 方块 X
		 * @param y 方块 Y
		 * @param z 方块 Z
		 * @param face 朝向（小写名）
		 * @param hand 手别（{@code "main"} / {@code "off"}）
		 * @return 瞄准参数
		 */
		public static Aim of(int x, int y, int z, String face, String hand) {
			return new Aim(x, y, z, face, hand, null);
		}

		/**
		 * 构造一个解析失败的结果。
		 *
		 * @param error 应回喂模型的错误文本
		 * @return 失败结果
		 */
		public static Aim invalid(String error) {
			return new Aim(0, 0, 0, DEFAULT_FACE, UseItemLogic.DEFAULT_HAND, error);
		}
	}

	private BlockAimLogic() {
	}

	/**
	 * 解析 {@code face} 参数。
	 *
	 * @param raw 原始参数；可为 null（表示缺省）
	 * @return 小写朝向名；非法时为 null
	 */
	public static @Nullable String parseFace(@Nullable Object raw) {
		if (raw == null) {
			return DEFAULT_FACE;
		}
		String face = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
		return FACES.contains(face) ? face : null;
	}

	/**
	 * 解析 {@code hit_pos} + {@code face} + {@code hand}。
	 *
	 * @param input 模型参数
	 * @return 解析结果；{@code error() != null} 时该文本即为 observation
	 */
	public static Aim parseAim(Map<String, Object> input) {
		BlockCoordinates coordinates = BlockCoordinates.parse(input, BlockCoordinates.HIT_POS);
		if (coordinates.error() != null) {
			return Aim.invalid(coordinates.error());
		}
		String face = parseFace(input.get("face"));
		if (face == null) {
			return Aim.invalid(INVALID_FACE);
		}
		String hand = UseItemLogic.parseHand(input);
		if (hand == null) {
			return Aim.invalid(INVALID_HAND);
		}
		return Aim.of(coordinates.x(), coordinates.y(), coordinates.z(), face, hand);
	}
}
