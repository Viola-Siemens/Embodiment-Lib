package com.hexagram2021.embodimentlib.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Griefing 检查助手（PLAN WP-5 ③，PRD §4.9）。
 * <p>
 * 所有破坏性工具（挖掘、放置、攻击、容器转移等）在执行前都必须过这里，
 * 使 AI 实体像原版生物一样遵守 {@code mobGriefing} 游戏规则与其它 mod 的事件拦截。
 *
 * <h2>与 PLAN 原设计的偏差（API 核对结论）</h2>
 * PLAN 原本示意自行构造 {@code new EntityMobGriefingEvent(entity, pos)} 并检查
 * {@code post(...).isCanceled()}。实测 26.1.2 源码后确认该写法<b>不成立</b>：
 * <ul>
 *   <li>构造器是 {@code EntityMobGriefingEvent(ServerLevel level, Entity entity)}——
 *       <b>没有 BlockPos 参数</b>（该事件只回答「这个实体现在能不能破坏」，与具体坐标无关）；</li>
 *   <li>{@code EntityEvent} 继承自 {@code Event} 而<b>非</b> {@code ICancellableEvent}，
 *       因此 {@code isCanceled()} 不存在，也没有「取消」这一概念；</li>
 *   <li>判定唯一依据是 {@code canGrief()}，且它已把 {@code GameRules.MOB_GRIEFING} 的初值
 *       纳入（构造时 {@code canGrief = level.getGameRules().get(MOB_GRIEFING)}）。</li>
 * </ul>
 * 更重要的是，NeoForge 已经提供了规范入口 {@link EventHooks#canEntityGrief}，
 * 其实现正是「post 事件并取 canGrief()」。因此本类<b>直接复用</b>它，而不是自己 post——
 * 这样若 NeoForge 未来调整该事件的语义/新增加载条件，本库自动跟随，无需改动。
 *
 * <h2>服务端专属</h2>
 * 该事件需要 {@link ServerLevel}（要读游戏规则）。CLIENT 侧没有权威世界状态，
 * 破坏性动作本就不应在客户端发生，故 {@link #denied} 对非 ServerLevel 一律返回
 * {@code true}（保守拒绝）。这与 PRD §4.1.1「CLIENT 不产生权威世界变更」一致：
 * 宁可让工具回一句 denied，也不能让客户端悄悄改了本地世界造成双端不一致。
 */
public final class Griefing {
	/** 被拒绝时工具必须返回的 observation 文本（PRD §4.9 约定，WP-6/7 的工具复用）。 */
	public static final String DENIED = "griefing denied";

	private Griefing() {
	}

	/**
	 * 判断一次破坏性动作是否被拒绝。
	 * <p>
	 * 实体与世界均非空（包级 {@code @NullMarked} 默认）；实体已死亡/卸载、或世界非
	 * {@link ServerLevel}（客户端）都会保守拒绝，返回 true。
	 *
	 * @param entity 执行动作的实体
	 * @param level 动作发生的世界；非 {@link ServerLevel}（客户端）时保守拒绝
	 * @return 被拒绝返回 true（调用方须返回 {@link #DENIED}）；放行返回 false
	 */
	public static boolean denied(LivingEntity entity, Level level) {
		if (!(level instanceof ServerLevel serverLevel)) {
			// 缺少权威判定依据（客户端）：破坏性动作的失败必须是「什么都没发生」。
			return true;
		}
		if (!entity.isAlive()) {
			// 实体已死亡/卸载：让它去挖方块本身就没有意义，直接拒绝。
			return true;
		}
		return !EventHooks.canEntityGrief(serverLevel, entity);
	}

	/**
	 * 便捷重载：从 {@link ToolContext} 取实体与世界。
	 *
	 * @param ctx 工具上下文
	 * @return 被拒绝返回 true
	 */
	public static boolean denied(ToolContext ctx) {
		return denied(ctx.entity(), ctx.level());
	}

	/**
	 * 判断某实体是否被允许执行破坏性动作（{@link #denied} 的取反，便于阅读）。
	 *
	 * @param ctx 工具上下文
	 * @return 允许返回 true
	 */
	public static boolean allowed(ToolContext ctx) {
		return !denied(ctx);
	}
}
