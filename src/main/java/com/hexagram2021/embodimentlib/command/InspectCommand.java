package com.hexagram2021.embodimentlib.command;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.attach.*;
import com.hexagram2021.embodimentlib.config.EmbodimentConfig;
import com.hexagram2021.embodimentlib.config.HostConfig;
import com.hexagram2021.embodimentlib.memory.SessionStore;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * {@code /embodimentlib inspect} 的执行体（PLAN WP-8）：<b>只做读取</b>，不做格式化。
 * <p>
 * 本类是 {@link InspectData} 的唯一生产点：把五个来源（实体附着、注册表、配置路由、
 * 会话存储、运行时句柄）读成一份数据，然后交给
 * {@link InspectReportBuilder} 呈现。所有「怎么读」的疑问都留在这里，
 * 所有「打印成什么样」的疑问都留在纯逻辑层。
 *
 * <h2>目标实体解析（PLAN：选择器 / UUID / 最近实体兜底，并打印来源）</h2>
 * <ol>
 *   <li>显式给了参数 → 用 {@link EntityArgument}（它本身支持名字、UUID 与 {@code @e} 等选择器，
 *       因此不需要自己写三种解析）；</li>
 *   <li>没给参数但命令由实体执行（玩家自己敲的）→ 用该实体；</li>
 *   <li>否则 → 找命令源周围 {@value #NEAREST_SEARCH_RADIUS} 格内<b>已附着</b>的最近实体
 *       （命令方块 / 控制台执行时需要它，否则无从指定目标）。</li>
 * </ol>
 * 三种来源都会在报告里打印（{@link InspectData.TargetSource}），
 * 否则运维看到一份报告却不知道它说的是谁。
 *
 * <h2>只读与隐私</h2>
 * 不修改世界、不改智能体状态；报告只回给执行者（{@code sendSuccess(..., false)}），
 * 不广播。api_key 与 base_url 在 {@link InspectData.ProfileView#of} 处就被丢弃，
 * 本类<b>不允许</b>把 profile 值对象直接塞进报告数据。
 *
 * <h2>线程</h2>
 * Brigadier 命令在服务器线程执行，因此这里可以直接读实体与世界状态。
 *
 * @author liudongyu
 */
public final class InspectCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.command");
	/** 兜底搜索半径（格）。 */
	public static final double NEAREST_SEARCH_RADIUS = 10.0;
	/** 找不到任何候选目标时的失败文本。 */
	public static final String NO_TARGET =
		"No target entity: pass one explicitly, or stand within " + (int) NEAREST_SEARCH_RADIUS
			+ " blocks of an attached entity. Usage: " + EmbodimentCommandTree.USAGE_HINT;

	private InspectCommand() {
	}

	/**
	 * 显式给了实体参数时的入口。
	 *
	 * @param context 命令上下文（{@code entity} 参数必然存在）
	 * @return 命令结果码（1 = 成功）
	 * @throws CommandSyntaxException 选择器无法解析出<b>唯一</b>实体时（Brigadier 会把它转成
	 *         面向玩家的错误提示，这正是 {@code EntityArgument} 的既有契约）
	 */
	public static int runExplicit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Entity target = EntityArgument.getEntity(context, EmbodimentCommandTree.ENTITY_ARGUMENT);
		return report(context.getSource(), target, InspectData.TargetSource.EXPLICIT);
	}

	/**
	 * 未给实体参数时的入口：优先取命令执行者自身，其次取附近已附着的最近实体。
	 *
	 * @param context 命令上下文
	 * @return 命令结果码（1 = 成功，0 = 找不到目标）
	 */
	public static int runFallback(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		Entity self = source.getEntity();
		if (self != null) {
			return report(source, self, InspectData.TargetSource.SOURCE_ENTITY);
		}
		Entity nearest = findNearestAttached(source);
		if (nearest == null) {
			source.sendFailure(Component.literal(NO_TARGET));
			return 0;
		}
		return report(source, nearest, InspectData.TargetSource.NEAREST_ATTACHED);
	}

	/** 在命令源周围找最近的已附着实体；没有则返回 null。 */
	private static @Nullable Entity findNearestAttached(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		AABB box = new AABB(origin, origin).inflate(NEAREST_SEARCH_RADIUS);
		Entity nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (Entity candidate : level.getEntitiesOfClass(Entity.class, box,
				entity -> AgentAttachment.isAttached(new EntityAttachmentTarget(entity)))) {
			double distanceSq = candidate.distanceToSqr(origin);
			if (distanceSq <= NEAREST_SEARCH_RADIUS * NEAREST_SEARCH_RADIUS && distanceSq < nearestSq) {
				nearestSq = distanceSq;
				nearest = candidate;
			}
		}
		return nearest;
	}

	/** 采集数据、生成报告并回给执行者。 */
	private static int report(CommandSourceStack source, Entity target, InspectData.TargetSource targetSource) {
		InspectData data = collect(target, targetSource);
		source.sendSuccess(() -> Component.literal(InspectReportBuilder.build(data)), false);
		// INFO 级留痕：命令是运维动作，日志里应能查到「谁在什么时候看了哪个会话」。
		LOGGER.info("inspect {} by {} -> {} {}", data.targetLabel(), source.getTextName(),
			data.attached() ? data.agentType() + "/" + data.sessionId() : InspectReportBuilder.NO_AGENT_ATTACHED,
			data.targetSource().label());
		return 1;
	}

	/**
	 * 读取一个实体的全部报告事实。
	 * <p>
	 * 未附着时立刻返回 {@link InspectData#notAttached}：此时既没有 session-id 可查注册表，
	 * 也没有 agent-type 可解析 profile，继续往下读只会得到一串「不存在」。
	 *
	 * @param target 目标实体
	 * @param targetSource 目标来源
	 * @return 报告数据
	 */
	public static InspectData collect(Entity target, InspectData.TargetSource targetSource) {
		EntityAttachmentTarget attachmentTarget = new EntityAttachmentTarget(target);
		AgentHostSide side = AgentAttachment.sideOf(target);
		String label = describeEntity(target);
		String agentType = AgentAttachment.getType(attachmentTarget);
		String sessionId = AgentAttachment.getSessionId(attachmentTarget);
		if (agentType.isBlank() || sessionId.isBlank()) {
			return InspectData.notAttached(side, label, targetSource);
		}

		RegistryEntry entry = AgentRegistry.forSide(side).get(sessionId);
		EmbodiedAgentHandle handle = entry == null ? null : entry.agent();
		List<ToolCallRecord> toolCalls = handle == null ? List.of() : handle.recentToolCalls();
		String preview = handle == null ? null
			: handle.conversationPreview(InspectReportBuilder.DEFAULT_CONVERSATION_PREVIEW_CHARS);

		HostConfig config = EmbodimentConfig.forSide(side);
		HostConfig.NamedProfile named = config.resolveNamedProfile(agentType);

		SessionStore sessions = SessionStore.forSide(side);
		boolean hasHistory = sessions.isAvailable() && sessions.hasHistory(sessionId);
		return new InspectData(side, label, targetSource, agentType, sessionId,
			InspectData.ProfileView.of(named.name(), named.profile()),
			entry == null ? null : entry.state(),
			toolCalls, preview,
			hasHistory ? sessions.historyPath(sessionId) : null,
			hasHistory ? sessions.historySizeBytes(sessionId) : -1L,
			InspectData.DEFAULT_TOOL_CALL_LIMIT);
	}

	/**
	 * 目标实体的人类可读标识：{@code 名字(uuid 前 8 位)}。
	 * <p>
	 * 只用 UUID 前缀：完整 UUID 有 36 字符，对定位「是哪一只」而言前 8 位已足够，
	 * 而报告越短越适合贴在聊天框里看。
	 *
	 * @param entity 目标实体
	 * @return 标识文本
	 */
	public static String describeEntity(Entity entity) {
		UUID uuid = entity.getUUID();
		String shortId = uuid.toString().substring(0, 8);
		String name = entity.getName().getString();
		return name.isBlank() ? shortId : name + "(" + shortId + ")";
	}
}
