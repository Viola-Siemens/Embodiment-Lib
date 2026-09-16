package com.hexagram2021.embodimentlib.config;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Embodiment Lib 配置入口（PRD §4.3 / §6.4）。
 * <p>
 * SERVER 与 CLIENT 各持一份完全独立的 spec 与文件
 * （{@code config/embodimentlib/server.toml} / {@code client.toml}），
 * 两棵树永不合并：值、会话、API key 均按宿主侧隔离。
 * <p>
 * 写法参考 GirlfriendsCommonConfig：ModConfigSpec + 静态持有。
 */
public final class EmbodimentConfig {
	/** SERVER 端配置 spec（{@code config/embodimentlib/server.toml}）。 */
	public static final ModConfigSpec SERVER_SPEC;
	/** SERVER 端配置视图（路由解析入口）。 */
	public static final HostConfig SERVER;
	/** CLIENT 端配置 spec（{@code config/embodimentlib/client.toml}）。 */
	public static final ModConfigSpec CLIENT_SPEC;
	/** CLIENT 端配置视图（路由解析入口）。 */
	public static final HostConfig CLIENT;

	static {
		ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
		SERVER = HostConfig.create(serverBuilder);
		SERVER_SPEC = serverBuilder.build();

		ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
		CLIENT = HostConfig.create(clientBuilder);
		CLIENT_SPEC = clientBuilder.build();
	}

	private EmbodimentConfig() {
	}

	/**
	 * 按宿主侧选择配置视图。
	 *
	 * @param side 宿主侧（SERVER / CLIENT）
	 * @return 该侧对应的 {@link HostConfig}
	 */
	public static HostConfig forSide(AgentHostSide side) {
		return switch (side) {
			case SERVER -> SERVER;
			case CLIENT -> CLIENT;
		};
	}
}
