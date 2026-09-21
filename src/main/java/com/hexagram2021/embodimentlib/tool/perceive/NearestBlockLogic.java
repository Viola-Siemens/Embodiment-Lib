package com.hexagram2021.embodimentlib.tool.perceive;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * {@code perceive.nearest_block} 的纯逻辑层（PLAN WP-6 #1，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（扫描 {@code BlockPos} 范围、读 {@code BlockState}、取注册表键）
 * 在 {@link NearestBlockTool} 中完成；本类负责<b>最值得测</b>的三件事：
 * <ul>
 *   <li>参数合法性判定（block id / radius 的边界）；</li>
 *   <li>在候选命中集合中选出最近的（点到点距离）；</li>
 *   <li>observation 文本规约（含 {@code "not found"} / {@code "invalid ..."} 形态）。</li>
 * </ul>
 *
 * <h2>契约</h2>
 * 输入 {@code block} 是<b>完整资源标识符</b>（可带命名空间，如 {@code "minecraft:iron_ore"}；
 * 无命名空间时按 {@code minecraft} 缺省）。输出 observation 为
 * `&lt;path&gt; at (x, y, z), distance D`——<b>只取路径部分</b>（命名空间是查找用的，
 * 回喂给模型的定位信息不需要重复它，PRD 示例即 {@code "iron_ore at ..."}）。
 *
 * @author liudongyu
 */
public final class NearestBlockLogic {
	/** 无匹配方块时的 observation。 */
	public static final String NOT_FOUND = "not found";
	/** radius 越界时的 observation。 */
	public static final String INVALID_RADIUS = "invalid radius";
	/** block id 不是合法资源标识符时的 observation。 */
	public static final String INVALID_BLOCK_ID = "invalid block id";
	/** radius 合法下界（1 格，含）。 */
	public static final int MIN_RADIUS = 1;
	/** radius 合法上界（32 格，含）。过大的扫描半径会让游戏线程一次 tick 做海量方块读取，必须封顶。 */
	public static final int MAX_RADIUS = 32;
	/** 默认扫描半径（与 PRD 示例一致）。 */
	public static final int DEFAULT_RADIUS = 8;

	/** 一次扫描命中的候选：完整资源标识符（含命名空间）与方块位置。 */
	public record BlockHit(String fullId, int x, int y, int z) {
	}

	private NearestBlockLogic() {
	}

	/**
	 * 校验 block id 是否为合法资源标识符（{@code "minecraft:iron_ore"} 或 {@code "iron_ore"}）。
	 * <p>
	 * 规则镜像 Minecraft {@code Identifier.tryParse}：可带 {@code namespace:path} 前缀；
	 * 路径字符 {@code [a-z0-9/._-]}，命名空间字符 {@code [a-z0-9._-]} 且不得为 {@code ".."}。
	 * 与游戏内解析保持一致，避免「纯逻辑层放行、世界侧却解析失败」的分叉。
	 *
	 * @param blockId 模型给的 block id（如 {@code "minecraft:iron_ore"}）
	 * @return 合法返回 true
	 */
	public static boolean isValidBlockId(@Nullable String blockId) {
		if (blockId == null || blockId.isBlank()) {
			return false;
		}
		String trimmed = blockId.strip();
		int colon = trimmed.indexOf(':');
		// 不允许出现多个冒号（"a:b:c" 不是合法 Identifier）。
		if (trimmed.indexOf(':', colon + 1) >= 0) {
			return false;
		}
		if (colon >= 0) {
			String namespace = trimmed.substring(0, colon);
			String path = trimmed.substring(colon + 1);
			return isValidNamespace(namespace) && isValidPath(path);
		}
		return isValidPath(trimmed);
	}

	/**
	 * 校验 radius 是否在 {@code [MIN_RADIUS, MAX_RADIUS]} 内。
	 *
	 * @param radius 扫描半径
	 * @return 合法返回 true
	 */
	public static boolean isValidRadius(int radius) {
		return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
	}

	/**
	 * 在候选命中集合中找出与目标点最近的一个 <b>且 id 匹配</b> 的命中。
	 *
	 * @param wantedFullId 期望的完整资源标识符（如 {@code "minecraft:iron_ore"}）
	 * @param ex 实体 EYE 中心 X（世界坐标，方块中心 +0.5）
	 * @param ey 实体 EYE 中心 Y
	 * @param ez 实体 EYE 中心 Z
	 * @param hits 候选命中集合
	 * @return 最近命中；无匹配返回 null
	 */
	@Nullable
	public static BlockHit findNearest(String wantedFullId, double ex, double ey, double ez,
			Iterable<BlockHit> hits) {
		BlockHit best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (BlockHit hit : hits) {
			if (!hit.fullId().equals(wantedFullId)) {
				continue;
			}
			double dx = hit.x() + 0.5 - ex;
			double dy = hit.y() + 0.5 - ey;
			double dz = hit.z() + 0.5 - ez;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = hit;
			}
		}
		return best;
	}

	/**
	 * 规约完整 observation：要么格式化最近命中，要么返回 {@link #NOT_FOUND}。
	 *
	 * @param wantedFullId 期望的完整资源标识符
	 * @param ex 实体 EYE 中心 X
	 * @param ey 实体 EYE 中心 Y
	 * @param ez 实体 EYE 中心 Z
	 * @param hits 候选命中集合
	 * @return observation 文本
	 */
	public static String describe(String wantedFullId, double ex, double ey, double ez, Iterable<BlockHit> hits) {
		BlockHit best = findNearest(wantedFullId, ex, ey, ez, hits);
		if (best == null) {
			return NOT_FOUND;
		}
		double dx = best.x() + 0.5 - ex;
		double dy = best.y() + 0.5 - ey;
		double dz = best.z() + 0.5 - ez;
		return format(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	/**
	 * 取资源标识符的路径部分（{@code "minecraft:iron_ore"} → {@code "iron_ore"}）。
	 *
	 * @param fullId 完整资源标识符
	 * @return 路径部分
	 */
	public static String pathOf(String fullId) {
		int colon = fullId.indexOf(':');
		return colon >= 0 ? fullId.substring(colon + 1) : fullId;
	}

	/**
	 * 把命中规约为 observation 文本：`&lt;path&gt; at (x, y, z), distance D`。
	 *
	 * @param hit 命中
	 * @param distance 距离
	 * @return observation 文本
	 */
	public static String format(BlockHit hit, double distance) {
		return pathOf(hit.fullId()) + " at (" + hit.x() + ", " + hit.y() + ", " + hit.z()
			+ "), distance " + String.format(Locale.ROOT, "%.1f", distance);
	}

	/**
	 * 供扫描侧（与单测）使用：构造一个 {@link BlockHit}。
	 *
	 * @param fullId 完整资源标识符（如 {@code "minecraft:iron_ore"}）
	 * @param x 方块坐标 X
	 * @param y 方块坐标 Y
	 * @param z 方块坐标 Z
	 * @return 命中记录
	 */
	public static BlockHit hit(String fullId, int x, int y, int z) {
		return new BlockHit(fullId, x, y, z);
	}

	private static boolean isValidNamespace(String namespace) {
		if (namespace.equals("..")) {
			return false;
		}
		for (int i = 0; i < namespace.length(); i++) {
			char c = namespace.charAt(i);
			if (c != '_' && c != '-' && c != '.' && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidPath(String path) {
		if (path.isEmpty()) {
			return false;
		}
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c != '_' && c != '-' && c != '/' && c != '.' && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) {
				return false;
			}
		}
		return true;
	}
}