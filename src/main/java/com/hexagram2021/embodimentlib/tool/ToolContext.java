package com.hexagram2021.embodimentlib.tool;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 一次工具调用的执行上下文——即「这个工具此刻在为哪个实体干活」
 * （PRD §4.1.3 / §4.5：工具永远绑定<b>执行时</b>所在实体，LLM 无需传实体）。
 * <p>
 * <b>为什么需要它</b>：LLM 只看得见工具名与 JSON 参数，看不见「谁在调用」。若让模型自己传
 * 实体 UUID，它既容易编造，也会带来越权风险（去操作视野外的实体）。因此绑定关系由库在
 * 派发工具时注入，模型只描述「做什么」，不描述「对谁做」。
 * <p>
 * <b>不可变</b>：使用 record。工具只能读取绑定，不能改写——否则一个工具就能把后续工具的
 * 目标换掉，形成隐蔽的串扰。
 *
 * @param side 宿主侧（SERVER / CLIENT）。CLIENT 侧工具只能读本地状态，不得产生权威世界变更
 * @param entity 执行时绑定的实体
 * @param agentType 该实体所属的 agent-type（用于按类型决定能力/提示词）
 * @param sessionId 会话身份（用于工具审计记录的回填）
 */
public record ToolContext(AgentHostSide side, LivingEntity entity, String agentType, String sessionId) {
	/**
	 * 紧凑构造器：校验必填字段非空白。
	 *
	 * @throws IllegalArgumentException agentType / sessionId 为空白
	 */
	public ToolContext {
		if (agentType.isBlank()) {
			throw new IllegalArgumentException("agentType must not be blank");
		}
		if (sessionId.isBlank()) {
			throw new IllegalArgumentException("sessionId must not be blank");
		}
	}

	/**
	 * 绑定实体是否仍然可用（存活且未从世界移除）。
	 * <p>
	 * 26.1.2 中 {@code Entity#isAlive()} 已等价于 {@code !isRemoved()}，
	 * 而 {@code LivingEntity} 覆写它并额外要求血量 &gt; 0，因此这里只需一次调用即可覆盖
	 * 「死亡」与「卸载」两种失效情形（PLAN WP-5 ② 要求的 {@code "entity unavailable"} 判定依据）。
	 *
	 * @return 可用返回 true；死亡或已卸载返回 false
	 */
	public boolean isEntityUsable() {
		return this.entity.isAlive();
	}

	/**
	 * 绑定实体所在的世界。
	 *
	 * @return 实体所在 Level
	 */
	public Level level() {
		return this.entity.level();
	}

	/**
	 * 把绑定实体当作 {@link Mob} 看待，用于需要寻路的能力。
	 * <p>
	 * PRD 对 #7/#8/#18 等移动类工具的要求是：非寻路实体返回 {@code "pathfinding not supported"}
	 * 而不是抛异常。因此这里返回可空值，由调用方产出对应文本。
	 *
	 * @return 若实体是 Mob 则返回它，否则返回 null
	 */
	public @Nullable Mob asMob() {
		return this.entity instanceof Mob mob ? mob : null;
	}
}
