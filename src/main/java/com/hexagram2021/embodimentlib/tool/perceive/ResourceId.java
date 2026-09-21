package com.hexagram2021.embodimentlib.tool.perceive;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * 资源标识符的纯逻辑校验与切分（无 Minecraft 依赖）。
 * <p>
 * 26.1.2 把 {@code ResourceLocation} 更名为 {@link net.minecraft.resources.Identifier}，
 * 而它的校验规则是「逐字符白名单」而不是正则。模型给的方块/实体类型 id 必须先过这一层，
 * 否则会在世界侧得到一个必然失败、却看不出原因的空指针/未找到。
 * 把规则集中到本类，是为了让「纯逻辑层放行、世界侧却解析失败」这种分叉不可能发生。
 *
 * <h2>规则（镜像 {@code Identifier.tryParse}）</h2>
 * <ul>
 *   <li>可带 {@code namespace:path}；无冒号或缺省命名空间（如 {@code ":stone"}）按
 *       {@value #DEFAULT_NAMESPACE} 补全；</li>
 *   <li>命名空间字符集 {@code [a-z0-9_.-]}，且不得为 {@code ".."}；</li>
 *   <li>路径字符集 {@code [a-z0-9/._-]}。</li>
 * </ul>
 *
 * <h2>与 {@code Identifier} 的刻意差异</h2>
 * 原版 {@code Identifier.isValidPath("")} 返回 {@code true}，因此 {@code "minecraft:"}
 * 在原版是<b>合法</b>标识符（路径为空）。本类把它判为<b>非法</b>：一个空路径的资源
 * 标识符在任何注册表里都不可能命中，与其放行后在工具里落成 {@code "not found"}
 * （模型会以为「世界真的没有这种方块」，从而去改半径重试），不如当场回
 * {@code "invalid block id"}（模型能立刻看出是自己拼错了）。这是更严的判定，
 * 已在 PLAN 的 WP-7 偏差记录中写明。
 *
 * @author liudongyu
 */
public final class ResourceId {
	/** 缺省命名空间（与原版 {@code Identifier.DEFAULT_NAMESPACE} 一致）。 */
	public static final String DEFAULT_NAMESPACE = "minecraft";

	private ResourceId() {
	}

	/**
	 * 校验一个资源标识符是否合法。
	 *
	 * @param id 待校验文本（可为 null）
	 * @return 合法返回 true
	 */
	public static boolean isValid(@Nullable String id) {
		return canonicalize(id) != null;
	}

	/**
	 * 把资源标识符规约为 {@code "namespace:path"} 规范形态。
	 * <p>
	 * 补全缺省命名空间是必要的：模型常写 {@code "zombie"}，而注册表键永远是
	 * {@code "minecraft:zombie"}，直接字符串比较会永远不相等。
	 *
	 * @param id 待规约文本（可为 null）
	 * @return 规范形态；不合法时返回 null
	 */
	public static @Nullable String canonicalize(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String trimmed = id.strip().toLowerCase(Locale.ROOT);
		int colon = trimmed.indexOf(':');
		// 多个冒号（"a:b:c"）不是合法标识符：原版会把 "b:c" 当作路径进而拒绝。
		if (trimmed.indexOf(':', colon + 1) >= 0) {
			return null;
		}
		if (colon < 0) {
			return isValidPath(trimmed) ? DEFAULT_NAMESPACE + ":" + trimmed : null;
		}
		String namespace = trimmed.substring(0, colon);
		String path = trimmed.substring(colon + 1);
		if (!isValidPath(path)) {
			return null;
		}
		// 原版允许 ":path"（空命名空间）并按 minecraft 补全，此处跟随。
		if (namespace.isEmpty()) {
			return DEFAULT_NAMESPACE + ":" + path;
		}
		return isValidNamespace(namespace) ? namespace + ":" + path : null;
	}

	/**
	 * 取资源标识符的路径部分（{@code "minecraft:iron_ore"} → {@code "iron_ore"}）。
	 * <p>
	 * 回喂给模型的 observation 只需要路径：命名空间是查找用的内部细节，
	 * 重复它只会占用上下文（PRD §4.5 #1 的示例即 {@code "iron_ore at ..."}）。
	 *
	 * @param id 资源标识符（可为规范形态或裸路径）
	 * @return 路径部分
	 */
	public static String pathOf(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	/**
	 * 取资源标识符的命名空间部分。
	 *
	 * @param id 资源标识符（可为规范形态或裸路径）
	 * @return 命名空间；裸路径按 {@value #DEFAULT_NAMESPACE} 返回
	 */
	public static String namespaceOf(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(0, colon) : DEFAULT_NAMESPACE;
	}

	private static boolean isValidNamespace(String namespace) {
		if (namespace.equals("..")) {
			return false;
		}
		for (int i = 0; i < namespace.length(); i++) {
			if (!validNamespaceChar(namespace.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidPath(String path) {
		// 刻意比原版更严：空路径（如 "minecraft:"）判非法，理由见类 Javadoc。
		if (path.isEmpty()) {
			return false;
		}
		for (int i = 0; i < path.length(); i++) {
			if (!validPathChar(path.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean validPathChar(char c) {
		return validNamespaceChar(c) || c == '/';
	}

	private static boolean validNamespaceChar(char c) {
		return c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
	}
}
