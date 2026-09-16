package com.hexagram2021.embodimentlib.api;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 智能体宿主侧（PRD §4.1.1）：SERVER 与 CLIENT 是完全隔离的两套运行时。
 * 本枚举不依赖 FML，便于纯 JUnit 单测；配置文件路径形如 {@code config/embodimentlib/server.toml}。
 */
public enum AgentHostSide {
	SERVER,
	CLIENT;

	/** 配置文件所在子目录（相对配置根目录）。 */
	public static final String CONFIG_SUBDIR = "embodimentlib";

	/**
	 * 本端配置文件文件名。
	 * <p>
	 * 使用 {@link Locale#ROOT} 显式限定区域设置：默认区域为土耳其语时
	 * {@code "CLIENT".toLowerCase()} 会产出 {@code "clıent"}（无点 i），导致文件名错误。
	 *
	 * @return server.toml（SERVER）/ client.toml（CLIENT）
	 */
	public String configFileName() {
		return this.name().toLowerCase(Locale.ROOT) + ".toml";
	}

	/**
	 * 本端配置文件的规范路径。
	 *
	 * @param configDir 配置根目录（FML 传入的 config 目录）
	 * @return {@code {configDir}/embodimentlib/{server|client}.toml}
	 */
	public Path configFile(Path configDir) {
		return configDir.resolve(CONFIG_SUBDIR).resolve(this.configFileName());
	}
}
