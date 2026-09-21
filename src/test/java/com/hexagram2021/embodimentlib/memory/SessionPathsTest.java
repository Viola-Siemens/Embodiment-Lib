package com.hexagram2021.embodimentlib.memory;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionPaths} 的单测（PLAN WP-4 ①②，PRD §6.4 的目录硬约束）。
 * <p>
 * 覆盖：目录布局与文件名、两棵树互不包含、以及 session-id 的<b>全部拒绝规则</b>
 * （路径穿越、绝对路径、分隔符、保留设备名、超长）。这些都是安全相关的判定，
 * 每一条都必须有独立用例。
 */
class SessionPathsTest {
	private static final Path SERVER_BASE = Path.of("server-world");
	private static final Path CLIENT_BASE = Path.of("config");

	@Test
	@DisplayName("sessionsRoot：base 下追加 embodimentlib/sessions 两级")
	void sessionsRootLayout() {
		assertEquals(Path.of("server-world", "embodimentlib", "sessions"),
				SessionPaths.sessionsRoot(SERVER_BASE));
		assertEquals(Path.of("config", "embodimentlib", "sessions"),
				SessionPaths.sessionsRoot(CLIENT_BASE));
	}

	@Test
	@DisplayName("会话目录与三个文件名：memory.json / todo.json / __anon__/<id>/agent_state.json")
	void sessionFileLayout() {
		Path root = SessionPaths.sessionsRoot(SERVER_BASE);
		Path dir = SessionPaths.sessionDir(root, "abc-123");
		assertEquals(root.resolve("abc-123"), dir);
		assertEquals(dir.resolve("memory.json"), SessionPaths.memoryFile(root, "abc-123"));
		assertEquals(dir.resolve("todo.json"), SessionPaths.todoFile(root, "abc-123"));
		assertEquals(dir.resolve("__anon__").resolve("abc-123").resolve("agent_state.json"),
				SessionPaths.historyFile(root, "abc-123"));
		// 历史文件必须落在会话目录<b>之内</b>：删除会话 = 删除一个目录。
		assertTrue(SessionPaths.historyFile(root, "abc-123").startsWith(dir));
	}

	@Test
	@DisplayName("两棵树永不合并：SERVER 根与 CLIENT 根不同，且互不包含")
	void serverAndClientRootsAreSeparate() {
		Path server = SessionPaths.sessionsRoot(SERVER_BASE);
		Path client = SessionPaths.sessionsRoot(CLIENT_BASE);
		assertNotEquals(server, client);
		assertFalse(client.startsWith(server), "CLIENT 根不得位于 SERVER 根之下");
		assertFalse(server.startsWith(client), "SERVER 根不得位于 CLIENT 根之下");
	}

	@Test
	@DisplayName("isValidSessionId：UUID、带点的命名空间式 id、长度上限内都合法")
	void acceptsWellFormedSessionIds() {
		assertTrue(SessionPaths.isValidSessionId("a"));
		assertTrue(SessionPaths.isValidSessionId("8b1c1e2a-3f4d-4a5b-8c9d-0e1f2a3b4c5d"));
		assertTrue(SessionPaths.isValidSessionId("village_npc.1"));
		assertTrue(SessionPaths.isValidSessionId("A-B_c.D"));
		assertTrue(SessionPaths.isValidSessionId("0"));
		assertTrue(SessionPaths.isValidSessionId("a".repeat(SessionPaths.MAX_SESSION_ID_LENGTH)));
	}

	@Test
	@DisplayName("isValidSessionId：拒绝路径穿越、绝对路径与各类分隔符")
	void rejectsTraversalAndSeparators() {
		assertFalse(SessionPaths.isValidSessionId("../evil"));
		assertFalse(SessionPaths.isValidSessionId(".."));
		assertFalse(SessionPaths.isValidSessionId("."));
		assertFalse(SessionPaths.isValidSessionId("a/b"));
		assertFalse(SessionPaths.isValidSessionId("a\\b"));
		assertFalse(SessionPaths.isValidSessionId("C:\\x"));
		assertFalse(SessionPaths.isValidSessionId("/etc/passwd"));
		assertFalse(SessionPaths.isValidSessionId("a:b"));
		assertFalse(SessionPaths.isValidSessionId("a b"));
		assertFalse(SessionPaths.isValidSessionId("a%2e%2e"));
		assertFalse(SessionPaths.isValidSessionId("a\u0000b"));
		assertFalse(SessionPaths.isValidSessionId("会话"));
	}

	@Test
	@DisplayName("isValidSessionId：拒绝空白、null 与超长 id")
	void rejectsBlankAndTooLong() {
		assertFalse(SessionPaths.isValidSessionId(null));
		assertFalse(SessionPaths.isValidSessionId(""));
		assertFalse(SessionPaths.isValidSessionId(
			"a".repeat(SessionPaths.MAX_SESSION_ID_LENGTH + 1)));
	}

	@Test
	@DisplayName("isValidSessionId：拒绝 Windows 保留设备名（否则会写到设备而不是文件）")
	void rejectsWindowsReservedNames() {
		assertFalse(SessionPaths.isValidSessionId("con"));
		assertFalse(SessionPaths.isValidSessionId("CON"));
		assertFalse(SessionPaths.isValidSessionId("Nul"));
		assertFalse(SessionPaths.isValidSessionId("com1"));
		assertFalse(SessionPaths.isValidSessionId("LPT9"));
		// 只是<b>以</b>保留名开头不算：那是合法文件名。
		assertTrue(SessionPaths.isValidSessionId("console"));
		assertTrue(SessionPaths.isValidSessionId("com10"));
	}

	@Test
	@DisplayName("requireValidSessionId：非法时抛 IAE 且在消息里点名那个 id")
	void requireValidSessionIdThrows() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> SessionPaths.requireValidSessionId("../evil"));
		assertTrue(ex.getMessage().contains("../evil"), () -> "异常应指明非法值: " + ex.getMessage());
		assertTrue(ex.getMessage().contains("session id"));
	}

	@Test
	@DisplayName("sessionDir：非法 id 一律拒绝，绝不「清洗后放行」")
	void sessionDirRejectsInvalidId() {
		Path root = SessionPaths.sessionsRoot(CLIENT_BASE);
		assertThrows(IllegalArgumentException.class, () -> SessionPaths.sessionDir(root, "../evil"));
		assertThrows(IllegalArgumentException.class, () -> SessionPaths.memoryFile(root, ".."));
		assertThrows(IllegalArgumentException.class, () -> SessionPaths.todoFile(root, "a/b"));
		assertThrows(IllegalArgumentException.class, () -> SessionPaths.historyFile(root, "nul"));
	}

	@Test
	@DisplayName("路径字符集与 AgentScope 的文件安全规则一致：磁盘段名就是 session-id")
	void sessionIdEqualsOnDiskSegmentName() {
		// AgentScope 的 JsonFileAgentStateStore 对匹配 [a-zA-Z0-9_.-]+ 的键<b>原样</b>用作目录名，
		// 本库的字符集与它相同，因此 historyFile 里可以直接用 sessionId 拼路径（无需 Base64）。
		String sessionId = "village_npc.1";
		Path history = SessionPaths.historyFile(SessionPaths.sessionsRoot(SERVER_BASE), sessionId);
		assertTrue(history.endsWith(Path.of(SessionPaths.ANONYMOUS_USER_DIR, sessionId,
			SessionPaths.AGENT_STATE_FILE)), () -> "历史路径布局不符合约定: " + history);
	}
}
