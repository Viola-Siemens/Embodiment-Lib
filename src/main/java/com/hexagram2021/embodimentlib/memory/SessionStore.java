package com.hexagram2021.embodimentlib.memory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hexagram2021.embodimentlib.api.AgentHostSide;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按 {@code (host-side, session-id)} 读写会话数据（PLAN WP-4 ③）。
 * <p>
 * 对外只暴露两件事实：<b>一个会话有哪些文件</b>、<b>怎么读写它们</b>。
 * 目录布局与 id 合法性全部委托给 {@link SessionPaths}（纯逻辑、已被单测覆盖），
 * 本类只负责「根目录从哪来」「怎么落盘」「缓存谁」。
 *
 * <h2>根目录（PRD §4.4 / §6.4）</h2>
 * <ul>
 *   <li><b>SERVER</b>：{@code ServerLifecycleHooks.getCurrentServer().getWorldPath(LevelResource.ROOT)}
 *       —— 会话随存档走。服务器没起时该值不可得；</li>
 *   <li><b>CLIENT</b>：{@code FMLPaths.CONFIGDIR.get()} —— 只在做推理的这台机器上。
 *       <b>两棵树永不合并</b>：{@link #forSide} 返回的两份单例各自只认自己的 base。</li>
 * </ul>
 * 根目录<b>每次调用都重新解析</b>，不做缓存：单人存档「退回标题再进另一个世界」时
 * JVM 不重启，缓存下来的旧世界路径会把新会话写进上一个存档里。
 *
 * <h2>降级（本类最重要的行为约定）</h2>
 * 根目录不可用时（服务器还没世界、客户端还没就绪）：
 * <ul>
 *   <li>{@link #load} 仍然返回<b>可用的内存态</b>，智能体照常运行；</li>
 *   <li>落盘变成 no-op，且<b>只告警一次</b>——每次改记忆都刷一条 WARN 会把日志淹掉；</li>
 *   <li>路径类查询（{@link #sessionDir}/{@link #historyPath}）抛 {@code IllegalStateException}：
 *       调用方明确要一个路径，而此刻没有路径可给，含糊地返回 null 会让下一步
 *       {@code Files.write} 抛 NPE 而丢失因果关系。</li>
 * </ul>
 *
 * <h2>会话历史</h2>
 * 0.1 的历史由 AgentScope 的 {@link JsonFileAgentStateStore} 负责（键 {@code agent_state}，
 * 内含对话缓冲）。本库<b>不</b>自己写 {@code history.json}：两套历史只会带来
 * 「哪份是权威」的争吵。{@link #openStateStore(String)} 把那个存储的根指向会话目录，
 * {@link #historyPath(String)} / {@link #hasHistory(String)} 供展示层（WP-8）查询。
 *
 * @author liudongyu
 */
public final class SessionStore implements SessionSink {
	/** 会话根目录的 base 解析器：返回 null 表示本端当前没有可用根。 */
	private static final Supplier<@Nullable Path> SERVER_BASE = SessionStore::resolveServerBase;
	private static final Supplier<@Nullable Path> CLIENT_BASE = SessionStore::resolveClientBase;

	/** 服务端会话存储（世界目录维度）。 */
	private static final SessionStore SERVER = new SessionStore(AgentHostSide.SERVER, SERVER_BASE);
	/** 客户端会话存储（本地配置目录维度）。 */
	private static final SessionStore CLIENT = new SessionStore(AgentHostSide.CLIENT, CLIENT_BASE);

	private static final Logger LOGGER = LoggerFactory.getLogger("embodimentlib.memory");
	/** Gson 实例线程安全，复用一份即可。 */
	private static final Gson GSON = new Gson();
	private static final Type MEMORY_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
	}.getType();
	private static final Type TODO_TYPE = new TypeToken<List<String>>() {
	}.getType();

	private final AgentHostSide side;
	private final Supplier<@Nullable Path> baseSupplier;
	/** 会话缓存：一个 session-id 在进程内只有一份活对象，避免「两份记忆互相覆盖」。 */
	private final Map<String, SessionData> cache = new ConcurrentHashMap<>();
	private final AtomicBoolean unavailableWarned = new AtomicBoolean();

	private SessionStore(AgentHostSide side, Supplier<@Nullable Path> baseSupplier) {
		this.side = side;
		this.baseSupplier = baseSupplier;
	}

	/**
	 * 本端会话存储单例。
	 *
	 * @param side 宿主侧
	 * @return 该侧的唯一会话存储
	 */
	public static SessionStore forSide(AgentHostSide side) {
		return switch (Objects.requireNonNull(side, "side")) {
			case SERVER -> SERVER;
			case CLIENT -> CLIENT;
		};
	}

	/**
	 * 以显式本端根目录创建存储（单测与嵌入式使用）。
	 * <p>
	 * 生产代码应走 {@link #forSide}：根目录是「一个宿主侧一份」的全局设施。
	 * 单测必须能指定临时目录才能真正验证读写与隔离，否则用例会写到真实的存档目录里。
	 * <p>
	 * 传入的是<b>本端根</b>（服务端 = 世界目录，客户端 = 配置目录），
	 * 而不是会话根：会话根由 {@link SessionPaths#sessionsRoot(Path)} 统一追加，
	 * 避免调用方与库各自拼一次 {@code embodimentlib/sessions} 而拼出双层目录。
	 *
	 * @param side 宿主侧
	 * @param baseDir 本端根目录（服务端 = 世界目录，客户端 = 配置目录）
	 * @return 全新的存储实例
	 */
	public static SessionStore forRoot(AgentHostSide side, Path baseDir) {
		Objects.requireNonNull(baseDir, "baseDir");
		return new SessionStore(Objects.requireNonNull(side, "side"), () -> baseDir);
	}

	/** @return 本存储的宿主侧 */
	public AgentHostSide side() {
		return this.side;
	}

	/**
	 * 本端会话根目录。
	 *
	 * @return 根目录；本端尚无可用 base（无世界 / 配置未就绪）时为 null
	 */
	public @Nullable Path root() {
		Path base = this.baseSupplier.get();
		return base == null ? null : SessionPaths.sessionsRoot(base);
	}

	/** @return 当前是否有可落盘的根目录 */
	public boolean isAvailable() {
		return root() != null;
	}

	/**
	 * 某个会话的目录。
	 *
	 * @param sessionId 会话身份
	 * @return 会话目录
	 * @throws IllegalArgumentException sessionId 非法
	 * @throws IllegalStateException 本端无可用根目录
	 */
	public Path sessionDir(String sessionId) {
		return SessionPaths.sessionDir(requireRoot(), sessionId);
	}

	/**
	 * 工作记忆文件路径。
	 *
	 * @param sessionId 会话身份
	 * @return {@code memory.json} 路径
	 */
	public Path memoryFile(String sessionId) {
		return SessionPaths.memoryFile(requireRoot(), sessionId);
	}

	/**
	 * 待办清单文件路径。
	 *
	 * @param sessionId 会话身份
	 * @return {@code todo.json} 路径
	 */
	public Path todoFile(String sessionId) {
		return SessionPaths.todoFile(requireRoot(), sessionId);
	}

	/**
	 * 会话历史文件路径。
	 *
	 * @param sessionId 会话身份
	 * @return {@code agent_state.json} 路径
	 */
	public Path historyPath(String sessionId) {
		return SessionPaths.historyFile(requireRoot(), sessionId);
	}

	/**
	 * 会话历史是否已落盘。
	 *
	 * @param sessionId 会话身份
	 * @return 存在且是普通文件返回 true；无可用根时返回 false
	 */
	public boolean hasHistory(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		Path root = root();
		return root != null && Files.isRegularFile(SessionPaths.historyFile(root, sessionId));
	}

	/**
	 * 会话历史文件的字节数（供 {@code /inspect} 一类的展示）。
	 *
	 * @param sessionId 会话身份
	 * @return 字节数；不存在或不可读时返回 -1
	 */
	public long historySizeBytes(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		Path root = root();
		if (root == null) {
			return -1L;
		}
		Path file = SessionPaths.historyFile(root, sessionId);
		try {
			return Files.isRegularFile(file) ? Files.size(file) : -1L;
		} catch (IOException ex) {
			LOGGER.warn("Failed to stat history file for session {}", sessionId, ex);
			return -1L;
		}
	}

	/**
	 * 打开该会话的 AgentScope 状态存储（会话历史的落盘载体）。
	 * <p>
	 * 调用方（WP-9/WP-10 的门面）拿到后交给 {@code EmbodiedAgent} 构建期即可：
	 * AgentScope 会在每次推理后把对话状态写进会话目录，重启后按
	 * {@code (userId=null, sessionId)} 自动续上。
	 *
	 * @param sessionId 会话身份
	 * @return 状态存储；目录创建失败或无可用根时为 null（agent 仍可创建，只是不落盘）
	 */
	public @Nullable AgentStateStore openStateStore(String sessionId) {
		Path root = root();
		if (root == null) {
			warnUnavailable("openStateStore");
			return null;
		}
		Path dir = SessionPaths.sessionDir(root, sessionId);
		try {
			// JsonFileAgentStateStore 的构造器会创建根目录，失败时抛 RuntimeException。
			return new JsonFileAgentStateStore(dir);
		} catch (RuntimeException ex) {
			LOGGER.warn("Failed to open agent state store under {}; session history will not persist",
				dir, ex);
			return null;
		}
	}

	/**
	 * 读取会话数据；不存在则返回空数据（不创建目录、不报错）。
	 *
	 * @param sessionId 会话身份
	 * @return 会话数据（同一 session-id 在进程内是同一对象）
	 * @throws IllegalArgumentException sessionId 非法
	 */
	public SessionData load(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		SessionData cached = this.cache.get(sessionId);
		if (cached != null) {
			return cached;
		}
		SessionData data = new SessionData(sessionId, this);
		Path root = root();
		if (root != null) {
			data.restore(readMemory(SessionPaths.memoryFile(root, sessionId)),
				readTodo(SessionPaths.todoFile(root, sessionId)));
		} else {
			warnUnavailable("load");
		}
		// 即便当前不可落盘也放进缓存：内存态对本次会话仍然有效，
		// 且根目录一旦可用（世界加载完成），后续写穿就会自动生效。
		this.cache.put(sessionId, data);
		return data;
	}

	/**
	 * 写入会话数据（{@link SessionData} 的写穿入口，也可显式调用）。
	 *
	 * @param sessionId 会话身份
	 * @param data 会话数据
	 * @throws IllegalArgumentException sessionId 非法
	 */
	@Override
	public void save(String sessionId, SessionData data) {
		Objects.requireNonNull(data, "data");
		SessionPaths.requireValidSessionId(sessionId);
		this.cache.put(sessionId, data);
		writeToDisk(sessionId, data);
	}

	/**
	 * 把缓存的会话写盘（不存在于缓存则什么都不做，返回 false）。
	 *
	 * @param sessionId 会话身份
	 * @return 确实写盘返回 true
	 * @throws IllegalArgumentException sessionId 非法
	 */
	public boolean flush(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		SessionData data = this.cache.get(sessionId);
		if (data == null) {
			return false;
		}
		writeToDisk(sessionId, data);
		return true;
	}

	/**
	 * 写盘并摘除缓存（实体卸载/死亡时的清理路径，PLAN WP-4 验收 ③）。
	 * <p>
	 * 顺序是「先写后摘」：缓存里可能有不落盘就无法恢复的改动，
	 * 先摘缓存再写会让异常路径直接丢数据。
	 *
	 * @param sessionId 会话身份
	 * @return 之前确实缓存过返回 true
	 * @throws IllegalArgumentException sessionId 非法
	 */
	public boolean evict(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		SessionData data = this.cache.remove(sessionId);
		if (data == null) {
			return false;
		}
		writeToDisk(sessionId, data);
		return true;
	}

	/**
	 * 把当前缓存中的全部会话写盘（存档 / 停服 / 客户端断开时的兜底）。
	 * <p>
	 * <b>无条件重写</b>：本方法不跟踪「脏」标记，返回的是<b>处理过的缓存会话数</b>，
	 * 而不是「改变了几个文件」。理由是写穿（每次改动立即落盘）已经保证了磁盘是新的，
	 * 这里只是兜底；为省几次小文件写入而引入脏标记状态，反而多一份必须同步维护的真相。
	 * 因此连续两次调用返回同一个数，这是预期行为（幂等重写）。
	 *
	 * @return 被写盘的会话数（= 当前缓存会话数）
	 */
	public int flushAll() {
		int count = 0;
		for (Map.Entry<String, SessionData> entry : this.cache.entrySet()) {
			writeToDisk(entry.getKey(), entry.getValue());
			count++;
		}
		if (count > 0) {
			LOGGER.debug("Flushed {} cached session(s) on {}", count, this.side);
		}
		return count;
	}

	/**
	 * 删除一个会话的全部落盘内容（含历史）。
	 *
	 * @param sessionId 会话身份
	 * @return 确实删掉了目录返回 true
	 * @throws IllegalArgumentException sessionId 非法
	 */
	public boolean delete(String sessionId) {
		SessionPaths.requireValidSessionId(sessionId);
		this.cache.remove(sessionId);
		Path root = root();
		if (root == null) {
			warnUnavailable("delete");
			return false;
		}
		Path dir = SessionPaths.sessionDir(root, sessionId);
		if (!Files.exists(dir)) {
			return false;
		}
		try {
			deleteRecursively(dir);
			return true;
		} catch (IOException ex) {
			LOGGER.warn("Failed to delete session directory {}", dir, ex);
			return false;
		}
	}

	/** @return 当前缓存中的会话数 */
	public int cachedCount() {
		return this.cache.size();
	}

	/**
	 * 清空缓存（服务器停止时用；不写盘，写盘请先 {@link #flushAll()}）。
	 * <p>
	 * 单人存档切换世界时必须清：JVM 不重启，残留的 session-id 会把新会话的
	 * 内存态与旧存档的内容混在一起。
	 *
	 * @return 被清掉的会话数
	 */
	public int clearCache() {
		int count = this.cache.size();
		this.cache.clear();
		return count;
	}

	private Path requireRoot() {
		Path root = root();
		if (root == null) {
			throw new IllegalStateException(
				"session store root is unavailable on " + this.side
					+ " (no world loaded / config not ready yet)");
		}
		return root;
	}

	private void writeToDisk(String sessionId, SessionData data) {
		Path root = root();
		if (root == null) {
			warnUnavailable("save");
			return;
		}
		writeJson(SessionPaths.memoryFile(root, sessionId), data.workingMemory(), sessionId);
		writeJson(SessionPaths.todoFile(root, sessionId), data.todo(), sessionId);
	}

	private void writeJson(Path file, Object value, String sessionId) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException _) {
				// 少数文件系统不支持原子改名：退化为普通替换，仍然避免「写到一半」。
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			// 落盘失败不能让工具调用炸掉：内存态仍然可用，只是这次没留下痕迹。
			LOGGER.warn("Failed to write session file {} for session {}", file, sessionId, ex);
		}
	}

	private Map<String, String> readMemory(Path file) {
		String json = readString(file);
		if (json == null) {
			return Map.of();
		}
		try {
			Map<String, String> parsed = GSON.fromJson(json, MEMORY_TYPE);
			return parsed == null ? Map.of() : Maps.newLinkedHashMap(parsed);
		} catch (JsonParseException ex) {
			LOGGER.warn("Ignoring corrupt session memory file {} ({}); starting with empty memory",
				file, ex.getMessage());
			return Map.of();
		}
	}

	private List<String> readTodo(Path file) {
		String json = readString(file);
		if (json == null) {
			return List.of();
		}
		try {
			List<String> parsed = GSON.fromJson(json, TODO_TYPE);
			return parsed == null ? List.of() : List.copyOf(parsed);
		} catch (JsonParseException ex) {
			LOGGER.warn("Ignoring corrupt session todo file {} ({}); starting with empty todo",
				file, ex.getMessage());
			return List.of();
		}
	}

	private @Nullable String readString(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOGGER.warn("Failed to read session file {}; treating as empty", file, ex);
			return null;
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException ex)
					throws IOException {
				if (ex != null) {
					throw ex;
				}
				Files.deleteIfExists(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 根目录不可用时只告警一次：每次记忆变更都刷 WARN 会把日志淹掉。 */
	private void warnUnavailable(String operation) {
		if (this.unavailableWarned.compareAndSet(false, true)) {
			LOGGER.warn("Session store on {} has no usable root yet; {} will not persist "
				+ "until a world/config directory is available (further notices suppressed)",
				this.side, operation);
		}
	}

	private static @Nullable Path resolveServerBase() {
		try {
			MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
			return server == null ? null : server.getWorldPath(LevelResource.ROOT);
		} catch (RuntimeException | LinkageError ex) {
			// 纯 JUnit / FML 尚未引导等环境：拿不到根目录是正常状态，降级即可。
			LOGGER.debug("Server session base unavailable: {}", ex.toString());
			return null;
		}
	}

	private static @Nullable Path resolveClientBase() {
		// FMLPaths 只在 FML 启动后才可用；纯 JUnit / 专用服务器环境下取不到（也不该取）。
		try {
			return FMLPaths.CONFIGDIR.get();
		} catch (RuntimeException | LinkageError ex) {
			LOGGER.debug("Client session base unavailable: {}", ex.toString());
			return null;
		}
	}

	/**
	 * 单行描述（宿主侧、缓存会话数、当前根目录）。
	 * <p>
	 * 刻意<b>不</b>输出任何会话内容：会话记忆里可能有 addon 写入的任意上下文，
	 * 让它出现在日志/命令输出里既无必要、也可能踩到「内容不得外泄」的红线。
	 */
	@Override
	public String toString() {
		return "SessionStore[" + this.side + " cached=" + this.cache.size() + " root=" + root() + "]";
	}
}
