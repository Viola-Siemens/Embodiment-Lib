package com.hexagram2021.embodimentlib.tool.action;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 持续跟随目标的 AI goal（PLAN WP-7 #18 的实现载体）。
 * <p>
 * 为什么用 {@link Goal} 而不是「每次调用重新寻路」：跟随是<b>持续行为</b>——
 * 目标会走开、会拐弯、会跳，靠模型反复调用工具去追，既慢又会把上下文烧在
 * 「再走一步」这种琐事上。挂进 {@code GoalSelector} 后，实体每 tick 自己维持距离，
 * 模型只需下一次「跟着它」和一次「别跟了」。
 *
 * <h2>控制位</h2>
 * 声明 {@code MOVE} 与 {@code LOOK}：跟随期间实体不该同时被闲逛/看风景的 goal 抢走
 * 移动权（原版 {@code GoalSelector} 按控制位互斥），但它<b>不</b>抢 {@code JUMP} 与
 * {@code TARGET}——遇到障碍该跳还是要跳，该有攻击目标也仍然有。
 *
 * <h2>目标消失即放弃</h2>
 * {@link #canUse()} 每次评估都重新查目标：目标死亡/离开世界后本 goal 自然不再生效，
 * 不会带着一个失效的 UUID 空转。真正的状态清理（从 {@code GoalSelector} 与
 * {@link FollowService} 的表中移除）由 {@link FollowService} 的生命周期钩子负责。
 *
 * @author liudongyu
 */
final class FollowGoal extends Goal {
	private static final double SPEED = 1.0;

	private final Mob mob;
	private final UUID targetId;
	private final double distance;

	/**
	 * 构造跟随 goal。
	 *
	 * @param mob 跟随者（必须是 {@code Mob}，需要 {@code PathNavigation}）
	 * @param targetId 目标实体 UUID
	 * @param distance 期望保持的距离
	 */
	FollowGoal(Mob mob, UUID targetId, double distance) {
		this.mob = mob;
		this.targetId = targetId;
		this.distance = distance;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * 目标实体 UUID。
	 *
	 * @return 目标 UUID
	 */
	UUID targetId() {
		return this.targetId;
	}

	@Override
	public boolean canUse() {
		return this.target() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.target() != null;
	}

	@Override
	public void tick() {
		LivingEntity target = this.target();
		if (target == null) {
			return;
		}
		if (this.mob.distanceTo(target) > this.distance) {
			this.mob.getNavigation().moveTo(target, SPEED);
		} else {
			// 已在期望距离内：停住而不是继续贴上去，否则会不停推挤目标。
			this.mob.getNavigation().stop();
		}
		this.mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}

	/** 解析当前目标；不存在、已死亡或不是活体时返回 null。 */
	private @Nullable LivingEntity target() {
		if (!this.mob.isAlive()) {
			return null;
		}
		Entity entity = this.mob.level().getEntity(this.targetId);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}
}
