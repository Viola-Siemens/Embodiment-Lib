package com.hexagram2021.embodimentlib.attach;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实体生命周期事件接线（PLAN WP-2 ③）。
 * <p>
 * 本类只做「取实体 → 算 session-id → 交给 {@link AgentLifecyclePlan} 决策 → 执行动作」，
 * 所有决策逻辑都在 {@link AgentLifecyclePlan} 中并被单测覆盖。
 * <p>
 * <b>监听的事件</b>：
 * <ul>
 *   <li>{@code EntityLeaveLevelEvent}：实体离开世界 → 注销（PRD §4.4「卸载的实体 agent 不运行，直接关闭」）；</li>
 *   <li>{@code LivingDeathEvent}：生物死亡 → 注销。死亡后实体往往要到 chunk 卸载才触发
 *       leave 事件，期间若玩家仍在对话会读到已死实体，因此死亡即刻注销；</li>
 *   <li>{@code ServerTickEvent.Post}：轻量扫描，清理「实体已消失但条目仍在」的泄漏条目；</li>
 *   <li>{@code ServerStoppedEvent}：服务器停止，清空整个服务端注册表。</li>
 * </ul>
 * <b>刻意不监听</b> {@code EntityJoinLevelEvent}：按 PLAN WP-2 ③ 定稿，构造
 * {@link EmbodiedAgentHandle} 是 addon / WP-10 门面的职责，库不做隐式兜底注册。
 * 该决策在 {@link AgentLifecyclePlan#onJoin} 中显式表达并已被单测覆盖。
 */
public final class AgentLifecycle {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.runtime");
	/** 泄漏扫描的间隔（tick）。600 tick = 30 秒。 */
	private static final int SWEEP_INTERVAL_TICKS = 600;

	private static int tickCounter;

	private AgentLifecycle() {
	}

	/**
	 * 注册全部服务端生命周期监听器（由主类在构造期调用一次）。
	 * <p>
	 * 使用 game 事件总线（{@link NeoForge#EVENT_BUS}）而非 mod 总线：
	 * 实体生命周期事件全部是游戏事件。
	 */
	public static void register() {
		NeoForge.EVENT_BUS.addListener(AgentLifecycle::onEntityLeaveLevel);
		NeoForge.EVENT_BUS.addListener(AgentLifecycle::onLivingDeath);
		NeoForge.EVENT_BUS.addListener(AgentLifecycle::onServerTickPost);
		NeoForge.EVENT_BUS.addListener(AgentLifecycle::onServerStopped);
	}

	private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		// 离开事件在双端都会触发；服务端注册表只由服务端实体驱动。
		if (event.getLevel().isClientSide()) {
			return;
		}
		unregisterFor(entity);
	}

	private static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		unregisterFor(entity);
	}

	/**
	 * 若该实体登记过条目则注销并关闭。
	 * <p>
	 * 注意：这里读的是<b>注册表</b>而不是实体附着。addon 可能在实体离开前先清掉附着，
	 * 此时条目仍必须被关闭（见 {@link AgentLifecyclePlan#onRemove}）。
	 */
	private static void unregisterFor(Entity entity) {
		String sessionId = AgentAttachment.getSessionId(new EntityAttachmentTarget(entity));
		if (AgentLifecyclePlan.onRemove(AgentHostSide.SERVER, AgentRegistry.server().get(sessionId) != null)
				== AgentLifecyclePlan.Action.UNREGISTER) {
			AgentRegistry.server().unregister(sessionId);
		}
	}

	private static void onServerTickPost(ServerTickEvent.Post event) {
		if (++tickCounter < SWEEP_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		sweep(event.getServer());
	}

	/**
	 * 清理泄漏条目：注册表有该 session，但世界里已经没有对应实体。
	 * <p>
	 * 这是兜底而非主路径——主路径是 leave/death 事件。存在的理由是某些卸载路径
	 * （例如区块保存失败、维度删除）可能不投递 leave 事件，而未关闭的
	 * {@code EmbodiedAgent} 会持续持有 HTTP 连接与会话文件句柄。
	 *
	 * @param server 服务器实例
	 */
	private static void sweep(MinecraftServer server) {
		AgentRegistry registry = AgentRegistry.server();
		for (RegistryEntry entry : registry.all()) {
			// 判定「实体是否还在」：只要注册表里的 session 已无实体持有，就回收。
			boolean present = isSessionPresent(server, entry.sessionId());
			if (AgentLifecyclePlan.onSweep(AgentHostSide.SERVER, true, present)
					== AgentLifecyclePlan.Action.UNREGISTER) {
				LOGGER.debug("Sweeping leaked agent entry for session {}", entry.sessionId());
				registry.unregister(entry.sessionId());
			}
		}
	}

	/** 遍历全部已加载实体，判断是否有实体当前持有该 session-id。 */
	private static boolean isSessionPresent(MinecraftServer server, String sessionId) {
		for (var level : server.getAllLevels()) {
			for (Entity entity : level.getEntities().getAll()) {
				if (sessionId.equals(AgentAttachment.getSessionId(new EntityAttachmentTarget(entity)))) {
					return true;
				}
			}
		}
		return false;
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		int size = AgentRegistry.server().size();
		if (size > 0) {
			LOGGER.info("Server stopping: closing {} remaining agent(s)", size);
		}
		AgentRegistry.server().clear();
	}
}
