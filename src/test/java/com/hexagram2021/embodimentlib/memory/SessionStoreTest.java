package com.hexagram2021.embodimentlib.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionStore} 的单测（PLAN WP-4 全部四条验收标准）。
 * <p>
 * 覆盖：不存在目录的空加载、落盘后的目录/文件结构、路径穿越拒绝、
 * SERVER/CLIENT 根分离、{@code unregister} 路径（{@code evict}）触发写盘、
 * 同类型不同 session-id 互不可见，以及根目录不可用时的降级行为。
 */
class SessionStoreTest {
	private static final String SESSION_A = "8b1c1e2a-3f4d-4a5b-8c9d-0e1f2a3b4c5d";
	private static final String SESSION_B = "11111111-2222-3333-4444-555555555555";

	private static SessionStore serverStore(Path base) {
		return SessionStore.forRoot(AgentHostSide.SERVER, base);
	}

	private static SessionStore clientStore(Path base) {
		return SessionStore.forRoot(AgentHostSide.CLIENT, base);
	}

	@Test
	@DisplayName("验收①②：load 不存在的会话返回空数据，且不创建目录（读操作不应有副作用）")
	void loadMissingSessionReturnsEmptyWithoutCreatingFiles(@TempDir Path base) {
		SessionStore store = serverStore(base);
		SessionData data = store.load(SESSION_A);

		assertTrue(data.isEmpty());
		assertEquals(SESSION_A, data.sessionId());
		assertTrue(store.isAvailable());
		assertFalse(Files.exists(store.sessionDir(SESSION_A)), "只读加载不得创建会话目录");
	}

	@Test
	@DisplayName("验收②：写穿后目录与文件结构正确（memory.json / todo.json 的形态由 PLAN 固定）")
	void saveCreatesExpectedLayout(@TempDir Path base) throws IOException {
		SessionStore store = serverStore(base);
		SessionData data = store.load(SESSION_A);
		data.remember("home", "12,64,-3");
		data.remember("owner", "Steve");
		data.addTodo("mine 10 iron ore");

		Path dir = store.sessionDir(SESSION_A);
		assertTrue(Files.isDirectory(dir), "会话目录应被创建");
		assertEquals("{\"home\":\"12,64,-3\",\"owner\":\"Steve\"}",
			Files.readString(store.memoryFile(SESSION_A), StandardCharsets.UTF_8));
		assertEquals("[\"mine 10 iron ore\"]",
			Files.readString(store.todoFile(SESSION_A), StandardCharsets.UTF_8));
		// 落盘路径必须真的在会话目录之内（PRD §6.4：会话随存档走）
		assertTrue(store.memoryFile(SESSION_A).startsWith(dir));
		assertTrue(store.todoFile(SESSION_A).startsWith(dir));
	}

	@Test
	@DisplayName("验收②：重新 load（新实例、空缓存）读回内容一致")
	void reloadReadsBackSameContent(@TempDir Path base) {
		SessionStore first = serverStore(base);
		SessionData data = first.load(SESSION_A);
		data.remember("k", "v");
		data.addTodo("t");

		// 新实例 = 新进程语义（缓存为空，只能靠磁盘）
		SessionStore second = serverStore(base);
		SessionData reloaded = second.load(SESSION_A);
		assertNotSame(data, reloaded);
		assertEquals("v", reloaded.get("k"));
		assertEquals(List.of("t"), reloaded.todo());
	}

	@Test
	@DisplayName("load 幂等：同一 session-id 在进程内始终是同一对象（避免两份记忆互相覆盖）")
	void loadIsCachedPerSession(@TempDir Path base) {
		SessionStore store = serverStore(base);
		assertSame(store.load(SESSION_A), store.load(SESSION_A));
		assertEquals(1, store.cachedCount());
	}

	@Test
	@DisplayName("验收④：不同 session-id 的数据互不可见（同 agent-type 同存档也一样）")
	void sessionsAreIsolatedFromEachOther(@TempDir Path base) {
		SessionStore store = serverStore(base);
		SessionData a = store.load(SESSION_A);
		SessionData b = store.load(SESSION_B);

		a.remember("shared-key", "value-of-a");
		b.addTodo("todo-of-b");

		assertNull(b.get("shared-key"), "B 不应看到 A 的记忆");
		assertTrue(b.todo().contains("todo-of-b"));
		assertFalse(a.todo().contains("todo-of-b"), "A 不应看到 B 的待办");

		// 从磁盘重新读也一样互不可见
		SessionStore fresh = serverStore(base);
		assertEquals("value-of-a", fresh.load(SESSION_A).get("shared-key"));
		assertNull(fresh.load(SESSION_B).get("shared-key"));
		assertTrue(fresh.load(SESSION_A).todo().isEmpty());
	}

	@Test
	@DisplayName("验收③：SERVER 根与 CLIENT 根分离且互不包含")
	void serverAndClientRootsAreSeparate(@TempDir Path serverBase, @TempDir Path clientBase) {
		SessionStore server = serverStore(serverBase);
		SessionStore client = clientStore(clientBase);

		assertEquals(AgentHostSide.SERVER, server.side());
		assertEquals(AgentHostSide.CLIENT, client.side());
		assertNotEquals(server.sessionDir(SESSION_A), client.sessionDir(SESSION_A));
		assertFalse(client.sessionDir(SESSION_A).startsWith(server.sessionDir(SESSION_A)));
		assertFalse(server.sessionDir(SESSION_A).startsWith(client.sessionDir(SESSION_A)));
	}

	@Test
	@DisplayName("验收③（端隔离）：在 SERVER 写入的会话，CLIENT 存储读不到")
	void sideStoresDoNotShareData(@TempDir Path serverBase, @TempDir Path clientBase) {
		SessionStore server = serverStore(serverBase);
		SessionStore client = clientStore(clientBase);
		server.load(SESSION_A).remember("k", "server-value");

		assertTrue(client.load(SESSION_A).isEmpty(), "客户端会话树不得读到服务端内容");
		assertFalse(Files.exists(client.sessionDir(SESSION_A)));
	}

	@Test
	@DisplayName("验收①：路径穿越 session-id 被所有入口拒绝，且绝不创建越界目录")
	void rejectsPathTraversalEverywhere(@TempDir Path base) {
		SessionStore store = serverStore(base);
		String evil = "../evil";
		SessionData data = store.load(SESSION_A);

		assertThrows(IllegalArgumentException.class, () -> store.sessionDir(evil));
		assertThrows(IllegalArgumentException.class, () -> store.memoryFile(evil));
		assertThrows(IllegalArgumentException.class, () -> store.todoFile(evil));
		assertThrows(IllegalArgumentException.class, () -> store.historyPath(evil));
		assertThrows(IllegalArgumentException.class, () -> store.load(evil));
		assertThrows(IllegalArgumentException.class, () -> store.save(evil, data));
		assertThrows(IllegalArgumentException.class, () -> store.flush(evil));
		assertThrows(IllegalArgumentException.class, () -> store.evict(evil));
		assertThrows(IllegalArgumentException.class, () -> store.delete(evil));
		assertThrows(IllegalArgumentException.class, () -> store.openStateStore(evil));
		assertThrows(IllegalArgumentException.class, () -> store.hasHistory(evil));
		assertThrows(IllegalArgumentException.class, () -> store.historySizeBytes(evil));

		// 越界路径（会话根的上一级）下不得出现任何东西
		Path sessionsRoot = SessionPaths.sessionsRoot(base);
		assertFalse(Files.exists(sessionsRoot.getParent().resolve("evil")),
			"不得在会话根之外创建目录");
		assertFalse(Files.exists(base.resolve("evil")));
	}

	@Test
	@DisplayName("验收③：evict（实体卸载路径）先写盘再摘缓存——被外部改脏的文件会被内存态覆盖")
	void evictFlushesThenDropsCache(@TempDir Path base) throws IOException {
		SessionStore store = serverStore(base);
		SessionData data = store.load(SESSION_A);
		data.remember("k", "v");
		assertEquals(1, store.cachedCount());

		// 模拟「内存态比磁盘新」（例如上一次写入被外部/崩溃打断）
		Files.writeString(store.memoryFile(SESSION_A), "{\"k\":\"stale\"}", StandardCharsets.UTF_8);
		assertEquals("{\"k\":\"stale\"}",
			Files.readString(store.memoryFile(SESSION_A), StandardCharsets.UTF_8));

		assertTrue(store.evict(SESSION_A), "evict 应报告确实处理了一个缓存会话");
		assertEquals("{\"k\":\"v\"}",
			Files.readString(store.memoryFile(SESSION_A), StandardCharsets.UTF_8),
			"unregister 路径必须把内存态写回磁盘");
		assertEquals(0, store.cachedCount(), "evict 之后缓存应被摘除");
		assertFalse(store.evict(SESSION_A), "重复 evict 不应再报告处理过");
	}

	@Test
	@DisplayName("flush：未缓存的会话返回 false；缓存的会话写盘并返回 true")
	void flushOnlyWritesCachedSessions(@TempDir Path base) {
		SessionStore store = serverStore(base);
		assertFalse(store.flush(SESSION_A), "没有缓存就没有可写的内容");

		store.load(SESSION_A).remember("k", "v");
		assertTrue(store.flush(SESSION_A));
		assertTrue(Files.isRegularFile(store.memoryFile(SESSION_A)));
	}

	@Test
	@DisplayName("flushAll + clearCache：存档/停服兜底把所有缓存会话落盘，再清空缓存")
	void flushAllThenClearCache(@TempDir Path base) {
		SessionStore store = serverStore(base);
		store.load(SESSION_A).remember("a", "1");
		store.load(SESSION_B).remember("b", "2");

		assertEquals(2, store.flushAll());
		assertEquals(2, store.flushAll(), "flushAll 是无条件重写：连续调用返回同一缓存会话数");
		assertTrue(Files.isRegularFile(store.memoryFile(SESSION_A)));
		assertTrue(Files.isRegularFile(store.memoryFile(SESSION_B)));

		assertEquals(2, store.clearCache());
		assertEquals(0, store.cachedCount());
	}

	@Test
	@DisplayName("delete：删除会话目录与缓存；对不存在的会话返回 false")
	void deleteRemovesDirectoryAndCache(@TempDir Path base) {
		SessionStore store = serverStore(base);
		store.load(SESSION_A).remember("k", "v");
		assertTrue(Files.isDirectory(store.sessionDir(SESSION_A)));

		assertTrue(store.delete(SESSION_A));
		assertFalse(Files.exists(store.sessionDir(SESSION_A)));
		assertEquals(0, store.cachedCount());
		assertFalse(store.delete(SESSION_A));

		// 删除后重新加载：空的（不是残留内容）
		assertTrue(store.load(SESSION_A).isEmpty());
	}

	@Test
	@DisplayName("delete：连同会话历史（AgentScope 状态存储的嵌套目录）一起删除")
	void deleteRemovesNestedHistoryDirectory(@TempDir Path base) {
		SessionStore store = serverStore(base);
		AgentStateStore stateStore = store.openStateStore(SESSION_A);
		assertNotNull(stateStore);
		stateStore.save(null, SESSION_A, "agent_state", AgentState.builder().sessionId(SESSION_A).build());
		assertTrue(store.hasHistory(SESSION_A));

		assertTrue(store.delete(SESSION_A));
		assertFalse(Files.exists(store.sessionDir(SESSION_A)), "整个会话目录（含历史）都应被删除");
	}

	@Test
	@DisplayName("损坏的 memory.json 不抛异常：以空记忆开始，todo.json 仍能独立读出")
	void corruptMemoryFileDegradesGracefully(@TempDir Path base) throws IOException {
		SessionStore store = serverStore(base);
		store.load(SESSION_A).addTodo("keep me");

		Files.writeString(store.memoryFile(SESSION_A), "[1,2]", StandardCharsets.UTF_8);
		SessionStore fresh = serverStore(base);
		SessionData loaded = fresh.load(SESSION_A);

		assertTrue(loaded.workingMemory().isEmpty(), "损坏的记忆应按空处理");
		assertEquals(List.of("keep me"), loaded.todo(), "另一个文件不应被牵连");
	}

	@Test
	@DisplayName("根目录不可用时的降级：内存态可用、路径查询抛 ISE、不产生任何文件")
	void degradesWhenRootUnavailable() {
		// 纯 JUnit 环境里没有服务端世界，故 forSide(SERVER) 无可用根。
		SessionStore store = SessionStore.forSide(AgentHostSide.SERVER);
		assertFalse(store.isAvailable());
		assertNull(store.root());

		SessionData data = store.load(SESSION_A);
		assertNotNull(data);
		data.remember("k", "v");
		assertEquals("v", data.get("k"), "无根时内存态仍然有效");
		assertTrue(store.flush(SESSION_A), "缓存仍在，flush 报告已处理");
		assertFalse(store.hasHistory(SESSION_A), "无根时不存在历史");
		assertEquals(-1L, store.historySizeBytes(SESSION_A));
		assertNull(store.openStateStore(SESSION_A), "无根时不应给出状态存储");

		// 路径类查询必须响亮失败，而不是返回 null 让调用方在下一步踩 NPE
		assertThrows(IllegalStateException.class, () -> store.sessionDir(SESSION_A));
		assertThrows(IllegalStateException.class, () -> store.historyPath(SESSION_A));
		assertFalse(store.delete(SESSION_A));
	}

	@Test
	@DisplayName("openStateStore：状态存储落在会话目录内，写进去的 agent_state 能被 hasHistory 看到")
	void stateStoreLivesInsideSessionDirectory(@TempDir Path base) {
		SessionStore store = serverStore(base);
		AgentStateStore stateStore = store.openStateStore(SESSION_A);
		assertNotNull(stateStore);
		assertFalse(store.hasHistory(SESSION_A), "还没写历史时应为 false");
		assertEquals(-1L, store.historySizeBytes(SESSION_A));

		// AgentScope 用的是 (userId=null, sessionId) 两段键；本库不引入用户概念。
		stateStore.save(null, SESSION_A, "agent_state",
			AgentState.builder().sessionId(SESSION_A).build());

		Path history = store.historyPath(SESSION_A);
		assertTrue(store.hasHistory(SESSION_A), () -> "历史文件未落在预期路径: " + history);
		assertTrue(history.startsWith(store.sessionDir(SESSION_A)),
			"历史必须位于会话目录之内（PRD §6.4：会话随存档走）");
		assertTrue(store.historySizeBytes(SESSION_A) > 0L);
	}

	@Test
	@DisplayName("sessionDir/memoryFile/todoFile 与 SessionPaths 的约定逐字一致")
	void pathsMatchSessionPathsContract(@TempDir Path base) {
		SessionStore store = serverStore(base);
		Path root = SessionPaths.sessionsRoot(base);
		assertEquals(SessionPaths.sessionDir(root, SESSION_A), store.sessionDir(SESSION_A));
		assertEquals(SessionPaths.memoryFile(root, SESSION_A), store.memoryFile(SESSION_A));
		assertEquals(SessionPaths.todoFile(root, SESSION_A), store.todoFile(SESSION_A));
		assertEquals(SessionPaths.historyFile(root, SESSION_A), store.historyPath(SESSION_A));
	}

	@Test
	@DisplayName("toString：只输出宿主侧/缓存数/根目录，不含会话内容")
	void toStringHasNoSessionContent(@TempDir Path base) {
		SessionStore store = serverStore(base);
		store.load(SESSION_A).remember("api_key", "sk-secret");

		String text = store.toString();
		assertTrue(text.contains("SERVER"), text);
		assertTrue(text.contains("cached=1"), text);
		assertFalse(text.contains("sk-secret"), "toString 不得泄露会话内容");
	}
}
