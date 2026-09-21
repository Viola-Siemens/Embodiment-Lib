package com.hexagram2021.embodimentlib.memory;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.attach.AgentAttachment;
import com.hexagram2021.embodimentlib.attach.EntityAttachmentTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话生命周期接线（PLAN WP-4 ③ 的保存时机）。
 * <p>
 * 只做一件事：<b>把「什么时候该写盘」翻译成对 {@link SessionStore} 的调用</b>。
 * 「写穿」已经保证每次改记忆都落盘，这里的三条路径是<b>兜底与清理</b>：
 * <ol>
 *   <li><b>实体离开世界 / 死亡</b> → {@link SessionStore#evict(String)}：写盘并摘除缓存。
 *       这就是 PLAN 验收项「{@code unregister} 路径触发 save」；
 *       与「实体还在但没人再用这个 agent」不同，实体消失后缓存里那份数据再也不会有新的改动，
 *       继续留着只会让内存随历史实体数增长；</li>
 *   <li><b>服务端存档（{@code LevelEvent.Save}）/ 停服（{@code ServerStoppingEvent}）</b> →
 *       {@link SessionStore#flushAll()}：把所有缓存会话落到磁盘；</li>
 *   <li><b>客户端世界卸载（{@code LevelEvent.Unload}）</b> → 客户端 {@code flushAll()}：
 *       客户端没有「存档事件」，断开连接就是它唯一的退出时机。</li>
 * </ol>
 *
 * <h2>为什么不在 {@code attach.AgentLifecycle} 里顺手清</h2>
 * 会话存储是本包里更高层的关注点；让 {@code attach}（更底层）反向依赖它会形成
 * 「实体生命周期需要知道某个持久化设施的私有状态」的耦合。这里改为本特性自带监听器，
 * 监听与 {@code AgentLifecycle} 相同的事件集，效果等价而依赖方向干净。
 * （与 WP-7 的 {@code FollowService} 同一取舍，已记入 PLAN 偏差记录。）
 *
 * <h2>为什么没有独立的「决策」类</h2>
 * WP-2 的 {@code AgentLifecyclePlan} 值得单独存在，是因为它的决策有
 * 「side × 是否已注册 × 实体是否还在」三个输入。这里是单一输入
 * （「缓存里有没有这个会话」），而那份判定的真实语义就是
 * {@code SessionStore#evict} 的返回值——把它再包一层枚举只会多一个必须同步维护的副本。
 * 判定因此放在 {@code SessionStore} 内部，并由其单测直接覆盖。
 *
 * @author liudongyu
 */
public final class SessionLifecycle {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.memory");

	private static boolean registered;

	private SessionLifecycle() {
	}

	/**
	 * 注册会话持久化的生命周期钩子（主类构造期调用一次；重复调用无副作用）。
	 */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		NeoForge.EVENT_BUS.addListener(SessionLifecycle::onEntityLeaveLevel);
		NeoForge.EVENT_BUS.addListener(SessionLifecycle::onLivingDeath);
		NeoForge.EVENT_BUS.addListener(SessionLifecycle::onLevelSave);
		NeoForge.EVENT_BUS.addListener(SessionLifecycle::onLevelUnload);
		NeoForge.EVENT_BUS.addListener(SessionLifecycle::onServerStopping);
	}

	private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		evictFor(event.getEntity(), event.getLevel().isClientSide());
	}

	private static void onLivingDeath(LivingDeathEvent event) {
		evictFor(event.getEntity(), event.getEntity().level().isClientSide());
	}

	private static void onLevelSave(LevelEvent.Save event) {
		// 存档事件双端都会触发；客户端没有权威存档，只处理服务端。
		if (event.getLevel().isClientSide()) {
			return;
		}
		SessionStore.forSide(AgentHostSide.SERVER).flushAll();
	}

	private static void onLevelUnload(LevelEvent.Unload event) {
		LevelAccessor level = event.getLevel();
		if (level.isClientSide()) {
			// 客户端断开连接 = 客户端会话的退出时机（PRD §4.4：会话随做推理的那台机器走）。
			SessionStore.forSide(AgentHostSide.CLIENT).flushAll();
		}
	}

	private static void onServerStopping(ServerStoppingEvent event) {
		SessionStore serverStore = SessionStore.forSide(AgentHostSide.SERVER);
		int flushed = serverStore.flushAll();
		// 单人存档切换世界时 JVM 不重启，缓存必须清空，否则上个世界的会话会串到下一个世界。
		int dropped = serverStore.clearCache();
		if (flushed > 0 || dropped > 0) {
			LOGGER.info("Server stopping: flushed {} and dropped {} cached session(s)", flushed, dropped);
		}
	}

	/**
	 * 实体消失时处理它的会话：写盘 + 摘除缓存。
	 *
	 * @param entity 离开世界或死亡的实体
	 * @param clientSide 该实体所在端是否为客户端
	 */
	private static void evictFor(Entity entity, boolean clientSide) {
		String sessionId = AgentAttachment.getSessionId(new EntityAttachmentTarget(entity));
		// 空串代表未附着（或 addon 只写了 agent_type 的中间态），这类实体的会话事件占绝大多数，静默返回即可。
		if (sessionId.isBlank()) {
			return;
		}
		AgentHostSide side = clientSide ? AgentHostSide.CLIENT : AgentHostSide.SERVER;
		SessionStore store = SessionStore.forSide(side);
		if (store.evict(sessionId)) {
			LOGGER.debug("Evicted session {} on {} after entity left", sessionId, side);
		}
	}
}
