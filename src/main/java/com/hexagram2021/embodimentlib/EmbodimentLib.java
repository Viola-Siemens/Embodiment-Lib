package com.hexagram2021.embodimentlib;

import com.hexagram2021.embodimentlib.attach.AgentLifecycle;
import com.hexagram2021.embodimentlib.attach.AttachmentTypes;
import com.hexagram2021.embodimentlib.config.EmbodimentConfig;
import com.hexagram2021.embodimentlib.gametest.ConfigGameTests;
import com.hexagram2021.embodimentlib.tool.action.FollowService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embodiment Lib 主类（NeoForge 库模组）。
 * <ul>
 *   <li>WP-0：构建基线 / JarInJar 打入 AgentScope / 元数据 / 发布。</li>
 *   <li>WP-1：注册双端独立配置（server.toml / client.toml，ModConfigSpec）与 GameTest 装载。</li>
 *   <li>WP-2：注册实体附着类型（agent_type / session_id）并接线实体生命周期钩子。</li>
 * </ul>
 */
@Mod(EmbodimentLib.MODID)
public final class EmbodimentLib {
	/** 模组 ID（与 neoforge.mods.toml 的 modId 一致）。 */
	public static final String MODID = "embodimentlib";
	private static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	/**
	 * 主类构造器：注册双端独立配置、实体附着类型与事件监听。
	 *
	 * @param modEventBus  Mod 事件总线（配置加载 / GameTest 注册 / 附着类型注册挂载于此）
	 * @param modContainer 当前 mod 容器（用于注册 server/client 配置文件）
	 */
	public EmbodimentLib(IEventBus modEventBus, ModContainer modContainer) {
		// WP-1：双端各自独立的 ModConfigSpec（PRD §4.3 / §6.4），互不读取
		modContainer.registerConfig(ModConfig.Type.SERVER, EmbodimentConfig.SERVER_SPEC, "embodimentlib/server.toml");
		modContainer.registerConfig(ModConfig.Type.CLIENT, EmbodimentConfig.CLIENT_SPEC, "embodimentlib/client.toml");

		// WP-2：实体附着类型注册（写入 neoforge:attachment_types 注册表）
		AttachmentTypes.REGISTER.register(modEventBus);
		// WP-2：实体加入/离开世界、死亡、服务器停止的生命周期钩子（游戏事件总线）
		AgentLifecycle.register();
		// WP-7：持续跟随工具的状态清理（唯一带持久副作用的工具，见 FollowService）
		FollowService.register();

		modEventBus.addListener(this::onConfigLoading);
		modEventBus.addListener(this::onRegisterGameTests);
		LOGGER.info("Embodiment Lib initialized: dual-side configs, attachments and lifecycle hooks registered.");
	}

	private void onConfigLoading(ModConfigEvent.Loading event) {
		if (event.getConfig().getSpec() == EmbodimentConfig.SERVER_SPEC) {
			EmbodimentConfig.SERVER.validateRouting();
			LOGGER.info("Server config loaded: {}", event.getConfig().getFileName());
		} else if (event.getConfig().getSpec() == EmbodimentConfig.CLIENT_SPEC) {
			EmbodimentConfig.CLIENT.validateRouting();
			LOGGER.info("Client config loaded: {}", event.getConfig().getFileName());
		}
	}

	private void onRegisterGameTests(RegisterGameTestsEvent event) {
		ConfigGameTests.onRegisterGameTests(event);
	}
}
