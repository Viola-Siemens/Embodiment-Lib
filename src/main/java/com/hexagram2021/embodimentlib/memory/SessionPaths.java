package com.hexagram2021.embodimentlib.memory;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 会话目录布局与 session-id 合法性（PLAN WP-4 ①，<b>纯逻辑</b>：零 Minecraft / 零 NeoForge 依赖）。
 * <p>
 * 把「路径长什么样」和「id 能不能当目录名」抽成独立的一层，理由是这两件事
 * <b>恰好是一个库最容易被攻破的地方</b>：session-id 来自 addon（最终来自模型/玩家语境），
 * 一旦带进 {@code ../}、绝对路径或 Windows 设备名，就会变成「读出/写入别的会话甚至别的目录」。
 * 纯逻辑化之后，全部拒绝规则都能在无游戏进程的环境下逐条断言。
 *
 * <h2>目录布局（PRD §6.4 硬约束）</h2>
 * <pre>
 * &lt;base&gt;/embodimentlib/sessions/&lt;session-id&gt;/
 *   memory.json        ← 工作记忆 {"key":"value"}（本库自管）
 *   todo.json          ← 待办清单 ["item", ...]（本库自管）
 *   __anon__/&lt;session-id&gt;/
 *     agent_state.json ← 会话历史（AgentScope 状态存储自管）
 * </pre>
 * {@code base} 在服务端是<b>世界目录</b>、在客户端是<b>配置目录</b>，两棵树永不合并。
 *
 * <h2>为什么历史文件会多一层 {@code __anon__/<session-id>}</h2>
 * AgentScope 的 {@code JsonFileAgentStateStore} 以 {@code (userId, sessionId)} 两段作键，
 * 而本库不引入「用户」概念（{@code RuntimeContext} 的 userId 为 null），
 * 于是它落在 {@code __anon__} 段下、再按 sessionId 分目录。多出的这一层是
 * AgentScope 的布局事实，不是本库的选择；好处是整个会话（历史 + 记忆 + 待办）
 * 仍然完全自包含在 {@code sessions/<session-id>/} 之内，删除会话 = 删除一个目录。
 *
 * @author liudongyu
 */
public final class SessionPaths {
	/** 模组自己的子目录名（与配置文件目录 {@code config/embodimentlib} 同名，便于辨认归属）。 */
	public static final String MOD_SUBDIR = "embodimentlib";
	/** 会话根目录名。 */
	public static final String SESSIONS_SUBDIR = "sessions";
	/** 工作记忆文件名。 */
	public static final String MEMORY_FILE = "memory.json";
	/** 待办清单文件名。 */
	public static final String TODO_FILE = "todo.json";
	/** 会话状态（含对话缓冲）文件名——AgentScope {@code agent_state} 键的落盘名。 */
	public static final String AGENT_STATE_FILE = "agent_state.json";
	/** AgentScope 在 userId 为 null 时使用的目录段。 */
	public static final String ANONYMOUS_USER_DIR = "__anon__";
	/** session-id 长度上限：默认是实体 UUID（36 字符），留出余量但必须封顶，避免超长路径。 */
	public static final int MAX_SESSION_ID_LENGTH = 64;

	/**
	 * Windows 保留设备名（不区分大小写）。
	 * <p>
	 * {@code Files.createDirectories(root.resolve("CON"))} 在 Windows 上不会创建目录，
	 * 而是打开控制台设备——一个看似成功的写入会静默落到设备上。addon 用
	 * {@code "CON"} 之类当会话名是极少数情况，但拦下它只需三行，漏掉的代价却很难查。
	 */
	private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
		"con", "prn", "aux", "nul",
		"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
		"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private SessionPaths() {
	}

	/**
	 * 本端会话根目录（{@code <base>/embodimentlib/sessions}）。
	 *
	 * @param baseDir 本端根目录：服务端 = 世界目录，客户端 = 配置目录
	 * @return 会话根目录
	 */
	public static Path sessionsRoot(Path baseDir) {
		return baseDir.resolve(MOD_SUBDIR).resolve(SESSIONS_SUBDIR);
	}

	/**
	 * 某个会话的目录。
	 *
	 * @param sessionsRoot 会话根目录（{@link #sessionsRoot(Path)}）
	 * @param sessionId 会话身份
	 * @return {@code <sessionsRoot>/<session-id>}
	 * @throws IllegalArgumentException sessionId 非法（见 {@link #isValidSessionId})
	 */
	public static Path sessionDir(Path sessionsRoot, String sessionId) {
		requireValidSessionId(sessionId);
		return sessionsRoot.resolve(sessionId);
	}

	/**
	 * 工作记忆文件路径。
	 *
	 * @param sessionsRoot 会话根目录
	 * @param sessionId 会话身份
	 * @return {@code <sessionDir>/memory.json}
	 */
	public static Path memoryFile(Path sessionsRoot, String sessionId) {
		return sessionDir(sessionsRoot, sessionId).resolve(MEMORY_FILE);
	}

	/**
	 * 待办清单文件路径。
	 *
	 * @param sessionsRoot 会话根目录
	 * @param sessionId 会话身份
	 * @return {@code <sessionDir>/todo.json}
	 */
	public static Path todoFile(Path sessionsRoot, String sessionId) {
		return sessionDir(sessionsRoot, sessionId).resolve(TODO_FILE);
	}

	/**
	 * 会话历史（对话状态）文件路径。
	 *
	 * @param sessionsRoot 会话根目录
	 * @param sessionId 会话身份
	 * @return {@code <sessionDir>/__anon__/<session-id>/agent_state.json}
	 */
	public static Path historyFile(Path sessionsRoot, String sessionId) {
		return sessionDir(sessionsRoot, sessionId)
			.resolve(ANONYMOUS_USER_DIR)
			.resolve(sessionId)
			.resolve(AGENT_STATE_FILE);
	}

	/**
	 * session-id 是否可安全地当作目录名。
	 * <p>
	 * 规则（全部是「拒绝」而非「清洗」）：
	 * <ul>
	 *   <li>非空白，长度 {@code 1..}{@value #MAX_SESSION_ID_LENGTH}；</li>
	 *   <li>字符集 {@code [A-Za-z0-9._-]}——与 AgentScope 的
	 *       {@code JsonFileAgentStateStore} 的「文件系统安全」规则一致，
	 *       因此磁盘上的目录段名与本库的 session-id 逐字相同（不会出现
	 *       「日志里叫 A、磁盘上叫 Base64(A)」的困惑）；</li>
	 *   <li>不是 {@code "."} 或 {@code ".."}（这两个会被解析成目录自身与父目录）；</li>
	 *   <li>不是 Windows 保留设备名。</li>
	 * </ul>
	 * 注意字符集里<b>没有</b> {@code "/"}、{@code "\"}、{@code ":"}，因此路径穿越
	 * （{@code "../evil"}）与绝对路径（{@code "C:\x"}）在第一步就被挡下。
	 *
	 * @param sessionId 会话身份；可为 null
	 * @return 合法返回 true
	 */
	public static boolean isValidSessionId(@Nullable String sessionId) {
		if (sessionId == null || sessionId.isEmpty() || sessionId.length() > MAX_SESSION_ID_LENGTH) {
			return false;
		}
		if (sessionId.equals(".") || sessionId.equals("..")) {
			return false;
		}
		for (int i = 0; i < sessionId.length(); i++) {
			char c = sessionId.charAt(i);
			boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
			if (!allowed) {
				return false;
			}
		}
		return !WINDOWS_RESERVED_NAMES.contains(sessionId.toLowerCase(Locale.ROOT));
	}

	/**
	 * 校验 session-id，非法即抛异常。
	 *
	 * @param sessionId 会话身份
	 * @throws IllegalArgumentException sessionId 非法
	 */
	public static void requireValidSessionId(@Nullable String sessionId) {
		if (!isValidSessionId(sessionId)) {
			throw new IllegalArgumentException(
				"invalid session id (expected 1.." + MAX_SESSION_ID_LENGTH
					+ " chars of [A-Za-z0-9._-], not \".\"/\"..\"/a reserved device name): '"
					+ sessionId + "'");
		}
	}
}
