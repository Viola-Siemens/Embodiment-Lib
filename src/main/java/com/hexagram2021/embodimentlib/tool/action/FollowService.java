package com.hexagram2021.embodimentlib.tool.action;

import com.google.common.collect.Maps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「持续跟随」的状态归属与生命周期（PLAN WP-7 #18/#19）。
 * <p>
 * 这是 23 个内置工具里<b>唯一带持久副作用</b>的一个（PLAN WP-7 风险备注即指出此点）：
 * 开始跟随之后，即使模型不再调用任何工具，实体每 tick 也在移动。
 * 因此必须回答两个问题：<b>状态存在哪</b>、<b>什么时候清</b>。
 *
 * <h2>状态存在哪</h2>
 * 一个 {@code UUID → FollowGoal} 的进程内表。挂在实体对象上（比如写进附件）看似更自然，
 * 但 {@code Goal} 的生命周期与实体绑死、且 {@code GoalSelector} 只认对象引用，
 * 一旦实体被重新加载就再也拿不回那个 goal 的引用来 {@code removeGoal}——
 * 那会留下一个永远无法停止的跟随。以 UUID 为键的表让「停止」始终可达。
 *
 * <h2>什么时候清</h2>
 * 三个入口，缺一不可：
 * <ol>
 *   <li>{@code action.stop_follow} → {@link #stop(Mob)}（正常路径）；</li>
 *   <li>实体离开世界 / 死亡 → {@link #forget(Entity)}。既清理「它作为跟随者」的条目，
 *       也清理「它作为目标」的条目——后者若不清理，跟随者会挂着一个永不出现的 UUID
 *       空转（原版实体重载后 UUID 不会复用）；</li>
 *   <li>服务器停止 → 清空整张表。单人存档切世界时 JVM 不重启，不清就会把上个世界的
 *       UUID 带进下一个世界。</li>
 * </ol>
 *
 * <h2>与 PLAN 措辞的偏差</h2>
 * PLAN WP-7 风险备注写的是「必须在 WP-2 的 unregister 路径上联动」。实现上这里是
 * <b>本特性自带的监听器</b>（{@link #register()}），监听与 WP-2 的
 * {@code AgentLifecycle} 完全相同的三个事件。理由：{@code attach} 包是比 {@code tool}
 * 更底层的层，让 {@code AgentLifecycle} 反向依赖 {@code tool.action.FollowService}
 * 会把「实体生命周期」与「某个工具的私有状态」耦合起来；而监听同一组事件已经获得
 * 等价效果。该取舍已记入 PLAN 偏差记录。
 *
 * <h2>线程</h2>
 * 工具的 {@code run} 保证在游戏线程执行，因此表本身不需要锁；但生命周期事件可能来自
 * 不同线程（例如服务器停止时的主线程收尾），故仍用 {@link ConcurrentHashMap}
 * 消除「读一半被改」的可能——这类表一旦抛 {@code ConcurrentModificationException}，
 * 会把一次无关的 entity 事件变成服务器崩溃。
 *
 * @author liudongyu
 */
public final class FollowService {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.tool");
	/**
	 * 跟随 goal 的优先级。
	 * <p>
	 * 原版 {@code GoalSelector} 中数字越小越优先。取 2：高于绝大多数闲逛/看风景 goal
	 * （让明确指令压过环境行为），又不去抢 0～1 的水面浮起、着火惊慌等生存行为。
	 */
	private static final int FOLLOW_PRIORITY = 2;
	private static final Map<UUID, FollowGoal> FOLLOWERS = Maps.newConcurrentMap();

	private static boolean hooksRegistered;

	private FollowService() {
	}

	/**
	 * 注册生命周期清理钩子（由主类在构造期调用一次；重复调用无副作用）。
	 */
	public static void register() {
		if (hooksRegistered) {
			return;
		}
		hooksRegistered = true;
		NeoForge.EVENT_BUS.addListener(FollowService::onEntityLeaveLevel);
		NeoForge.EVENT_BUS.addListener(FollowService::onLivingDeath);
		NeoForge.EVENT_BUS.addListener(FollowService::onServerStopped);
	}

	/**
	 * 让 {@code mob} 开始持续跟随目标；若它已在跟随，则替换为新的目标/距离。
	 *
	 * @param mob 跟随者
	 * @param targetId 目标实体 UUID
	 * @param distance 期望保持的距离
	 */
	public static void start(Mob mob, UUID targetId, double distance) {
		stop(mob);
		FollowGoal goal = new FollowGoal(mob, targetId, distance);
		mob.goalSelector.addGoal(FOLLOW_PRIORITY, goal);
		FOLLOWERS.put(mob.getUUID(), goal);
	}

	/**
	 * 停止 {@code mob} 的跟随（幂等）。
	 *
	 * @param mob 跟随者
	 * @return 之前确实在跟随返回 true
	 */
	public static boolean stop(Mob mob) {
		FollowGoal goal = FOLLOWERS.remove(mob.getUUID());
		if (goal == null) {
			return false;
		}
		mob.goalSelector.removeGoal(goal);
		mob.getNavigation().stop();
		return true;
	}

	/**
	 * 该实体当前是否在跟随（供命令/调试与单测观察状态）。
	 *
	 * @param entity 待查实体
	 * @return 在跟随返回 true
	 */
	public static boolean isFollowing(Entity entity) {
		return FOLLOWERS.containsKey(entity.getUUID());
	}

	/**
	 * 清理与某实体相关的全部跟随状态：它作为跟随者、以及它作为目标。
	 *
	 * @param entity 离开世界或死亡的实体
	 */
	static void forget(Entity entity) {
		UUID id = entity.getUUID();
		if (entity instanceof Mob mob) {
			stop(mob);
		}
		// 目标消失：把「跟着它」的条目一并撤掉，避免跟随者挂着一个永不出现的 UUID。
		FOLLOWERS.entrySet().removeIf(entry -> {
			if (!entry.getValue().targetId().equals(id)) {
				return false;
			}
			entry.getValue().stop();
			return true;
		});
	}

	/**
	 * 当前跟随者数量（可观测性；服务器停止时也用于判断是否需要打日志）。
	 *
	 * @return 跟随者数量
	 */
	public static int followerCount() {
		return FOLLOWERS.size();
	}

	private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		forget(event.getEntity());
	}

	private static void onLivingDeath(LivingDeathEvent event) {
		forget(event.getEntity());
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		int size = FOLLOWERS.size();
		if (size > 0) {
			LOGGER.info("Server stopping: clearing {} follow order(s)", size);
		}
		FOLLOWERS.clear();
	}
}
