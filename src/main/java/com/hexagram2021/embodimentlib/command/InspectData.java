package com.hexagram2021.embodimentlib.command;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.hexagram2021.embodimentlib.attach.AgentState;
import com.hexagram2021.embodimentlib.attach.ToolCallRecord;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 一次 {@code /embodimentlib inspect} 报告所需的全部事实（PLAN WP-8，<b>纯逻辑</b>：
 * 零 Minecraft / 零 NeoForge 依赖）。
 * <p>
 * <b>为什么先攒成一个值对象再格式化</b>：PRD §4.7 要求的五类信息来自五个互不相干的来源
 * （实体附着、注册表、配置路由、会话存储、运行时句柄），如果让命令实现边读边拼字符串，
 * 那么「报告长什么样」就无法在无游戏进程的环境下验证。把读取（适配器）与呈现（纯逻辑）
 * 分开之后，报告的全部文字都能用构造出来的数据逐行断言。
 *
 * <h2>隐私：这个类型里没有密钥</h2>
 * 注意 {@link #profile()} 的类型是 {@link ProfileView}，它<b>只有</b>
 * {@code name / protocol / modelName} 三个字段。{@link AgentProfile} 里的 {@code apiKey}
 * 与 {@code baseUrl} 在转换时就已被丢弃（见 {@link ProfileView#of}）。
 * 这比「输出时记得别打印 key」强得多——{@link InspectReportBuilder} 即使想打印也无从取出。
 *
 * @param side 该实体所在的宿主侧（决定读哪一份注册表与配置）
 * @param targetLabel 目标实体的人类可读标识（名字 + UUID 摘要），供报告定位「说的是谁」
 * @param targetSource 目标是怎么被选中的（显式参数 / 命令执行者自身 / 附近兜底）
 * @param agentType 附着里的 agent-type；未附着时为空串
 * @param sessionId 附着里的 session-id；未附着时为空串
 * @param profile 解析到的 profile 视图；未附着时为 null
 * @param state 注册表里的运行状态；未注册时为 null（与「已注册但空闲」是不同的事实）
 * @param recentToolCalls 最近工具调用（新的在前，已由 {@link ToolCallRecord} 截断过）
 * @param conversationPreview 已截断的会话预览；无句柄时为 null
 * @param historyPath 会话历史文件的落盘路径；未落盘或无可用根时为 null
 * @param historySizeBytes 会话历史文件字节数；与 {@code historyPath} 同时有效
 * @param toolCallLimit 报告中最多列出多少条工具调用
 * @author liudongyu
 */
public record InspectData(
		AgentHostSide side,
		String targetLabel,
		TargetSource targetSource,
		String agentType,
		String sessionId,
		@Nullable ProfileView profile,
		@Nullable AgentState state,
		List<ToolCallRecord> recentToolCalls,
		@Nullable String conversationPreview,
		@Nullable Path historyPath,
		long historySizeBytes,
		int toolCallLimit) {
	/** 报告中默认列出的工具调用条数（PLAN WP-8：默认 5）。 */
	public static final int DEFAULT_TOOL_CALL_LIMIT = 5;

	/**
	 * 紧凑构造器：校验必填字段，并复制列表（避免调用方事后改动影响已生成的报告）。
	 */
	public InspectData {
		Objects.requireNonNull(side, "side");
		Objects.requireNonNull(targetLabel, "targetLabel");
		Objects.requireNonNull(targetSource, "targetSource");
		Objects.requireNonNull(agentType, "agentType");
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(recentToolCalls, "recentToolCalls");
		if (toolCallLimit < 1) {
			throw new IllegalArgumentException("toolCallLimit must be positive, got " + toolCallLimit);
		}
		recentToolCalls = List.copyOf(recentToolCalls);
	}

	/**
	 * 构造一份「未附着实体」的报告数据。
	 *
	 * @param side 宿主侧
	 * @param targetLabel 目标实体标识
	 * @param targetSource 目标来源
	 * @return 未附着的报告数据（agentType / sessionId 为空串，其余为空）
	 */
	public static InspectData notAttached(AgentHostSide side, String targetLabel, TargetSource targetSource) {
		return new InspectData(side, targetLabel, targetSource, "", "",
			null, null, List.of(), null, null, -1L, DEFAULT_TOOL_CALL_LIMIT);
	}

	/**
	 * 该实体是否已附着（两个字段都非空白才算，与
	 * {@code AgentAttachment#isAttached} 同一判定）。
	 *
	 * @return 已附着返回 true
	 */
	public boolean attached() {
		return !this.agentType.isBlank() && !this.sessionId.isBlank();
	}

	/**
	 * 该会话在注册表里是否有条目。
	 *
	 * @return 有运行状态返回 true
	 */
	public boolean registered() {
		return this.state != null;
	}

	/**
	 * 会话历史是否已经落盘。
	 *
	 * @return 有历史文件返回 true
	 */
	public boolean hasHistory() {
		return this.historyPath != null && this.historySizeBytes >= 0L;
	}

	/** 目标实体的来源（PLAN WP-8：兜底时必须打印来源，否则运维不知道报告说的是谁）。 */
	public enum TargetSource {
		/** 命令里显式给了实体参数。 */
		EXPLICIT("explicit argument"),
		/** 命令由实体执行（如玩家自己敲的），取该实体。 */
		SOURCE_ENTITY("command source entity"),
		/** 既没给参数也不是实体执行的，取附近最近的已附着实体。 */
		NEAREST_ATTACHED("nearest attached entity nearby");

		private final String label;

		TargetSource(String label) {
			this.label = label;
		}

		/**
		 * 供报告展示的来源说明。
		 *
		 * @return 英文短语
		 */
		public String label() {
			return this.label;
		}
	}

	/**
	 * Profile 的<b>可展示视图</b>：只有名字、协议与模型名。
	 * <p>
	 * 刻意不是 {@link AgentProfile} 本身：值对象里多余的字段（{@code apiKey} / {@code baseUrl}）
	 * 一旦进入报告数据，就只剩「每处输出都记得别打印」这一道防线了。
	 *
	 * @param name 配置里的 profile 名（{@code "default"} 或命名 profile）
	 * @param protocol 协议（{@code "openai"} / {@code "anthropic"}）
	 * @param modelName 模型名
	 */
	public record ProfileView(String name, String protocol, String modelName) {
		/**
		 * 紧凑构造器：字段均不可为空。
		 */
		public ProfileView {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(protocol, "protocol");
			Objects.requireNonNull(modelName, "modelName");
		}

		/**
		 * 从配置解析结果与 profile 值对象构造视图（<b>丢弃</b> api_key 与 base_url）。
		 *
		 * @param name 配置里的 profile 名
		 * @param profile 已解析的 profile
		 * @return 可展示视图
		 */
		public static ProfileView of(String name, AgentProfile profile) {
			Objects.requireNonNull(profile, "profile");
			return new ProfileView(name, profile.protocol(), profile.modelName());
		}

		/**
		 * 供报告展示的单行文本：{@code quest_giver (anthropic / claude-sonnet-4-5)}。
		 *
		 * @return 单行文本
		 */
		public String display() {
			return this.name + " (" + this.protocol + " / " + this.modelName + ")";
		}
	}
}
