package com.hexagram2021.embodimentlib.memory;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionData} 的单测（PLAN WP-4 ②③）。
 * <p>
 * 覆盖：工作记忆与待办的增删语义、<b>写穿时机</b>（真正改动才写盘）、
 * 只读视图不可变、恢复不触发写盘、以及 {@code toString} 不泄露内容。
 * 落盘动作用一个记录调用的桩承接，因此本类不碰文件系统。
 */
class SessionDataTest {
	/** 记录写穿次数的桩（PLAN WP-4：把副作用挪到边界之外，数据层才能纯测）。 */
	private static final class RecordingSink implements SessionSink {
		private final List<String> savedSessions = Lists.newArrayList();

		@Override
		public void save(String sessionId, SessionData data) {
			this.savedSessions.add(sessionId);
		}

		int saveCount() {
			return this.savedSessions.size();
		}
	}

	private final RecordingSink sink = new RecordingSink();
	private final SessionData data = new SessionData("session-1", this.sink);

	@Test
	@DisplayName("初始为空：isEmpty 为 true、size 为 0、两个视图都是空集合")
	void startsEmpty() {
		assertTrue(this.data.isEmpty());
		assertEquals(0, this.data.size());
		assertTrue(this.data.workingMemory().isEmpty());
		assertTrue(this.data.todo().isEmpty());
		assertNull(this.data.get("missing"));
		assertEquals("session-1", this.data.sessionId());
	}

	@Test
	@DisplayName("remember：写入后写穿一次，且能读回")
	void rememberWritesThrough() {
		this.data.remember("home", "12,64,-3");
		assertEquals("12,64,-3", this.data.get("home"));
		assertEquals(1, this.sink.saveCount());
		assertEquals(List.of("session-1"), this.sink.savedSessions);
	}

	@Test
	@DisplayName("remember：值没变不写盘，值变了才写盘")
	void rememberOnlyPersistsOnChange() {
		this.data.remember("k", "v");
		assertEquals(1, this.sink.saveCount());

		this.data.remember("k", "v");
		assertEquals(1, this.sink.saveCount(), "重复写入同值不应触发落盘");

		this.data.remember("k", "v2");
		assertEquals(2, this.sink.saveCount());
		assertEquals("v2", this.data.get("k"));
	}

	@Test
	@DisplayName("forget：删掉存在的键写盘一次；删不存在的键返回 false 且不写盘")
	void forgetSemantics() {
		this.data.remember("k", "v");
		int baseline = this.sink.saveCount();

		assertFalse(this.data.forget("missing"));
		assertEquals(baseline, this.sink.saveCount(), "删除不存在的键不应写盘");

		assertTrue(this.data.forget("k"));
		assertEquals(baseline + 1, this.sink.saveCount());
		assertNull(this.data.get("k"));
	}

	@Test
	@DisplayName("addTodo：新条目写盘；重复条目返回 false 且不写盘（不产生重复待办）")
	void addTodoSemantics() {
		assertTrue(this.data.addTodo("mine the iron"));
		assertEquals(1, this.sink.saveCount());

		assertFalse(this.data.addTodo("mine the iron"));
		assertEquals(1, this.sink.saveCount());
		assertEquals(List.of("mine the iron"), this.data.todo());
	}

	@Test
	@DisplayName("completeTodo：移除存在的条目写盘；移除不存在的返回 false")
	void completeTodoSemantics() {
		this.data.addTodo("a");
		this.data.addTodo("b");
		int baseline = this.sink.saveCount();

		assertTrue(this.data.completeTodo("a"));
		assertEquals(baseline + 1, this.sink.saveCount());
		assertEquals(List.of("b"), this.data.todo());

		assertFalse(this.data.completeTodo("a"));
		assertEquals(baseline + 1, this.sink.saveCount());
	}

	@Test
	@DisplayName("clear：非空时清空并写盘；已空时返回 false 且不写盘")
	void clearSemantics() {
		assertFalse(this.data.clear());
		assertEquals(0, this.sink.saveCount());

		this.data.remember("k", "v");
		this.data.addTodo("t");
		int baseline = this.sink.saveCount();

		assertTrue(this.data.clear());
		assertEquals(baseline + 1, this.sink.saveCount());
		assertTrue(this.data.isEmpty());
		assertEquals(0, this.data.size());
	}

	@Test
	@DisplayName("视图保序：工作记忆按插入顺序、待办按添加顺序")
	void viewsPreserveInsertionOrder() {
		this.data.remember("b", "1");
		this.data.remember("a", "2");
		this.data.addTodo("first");
		this.data.addTodo("second");

		assertEquals(List.of("b", "a"), List.copyOf(this.data.workingMemory().keySet()));
		assertEquals(List.of("first", "second"), this.data.todo());
		assertEquals(4, this.data.size());
	}

	@Test
	@DisplayName("只读视图不可变：外部修改抛 UnsupportedOperationException")
	void viewsAreUnmodifiable() {
		this.data.remember("k", "v");
		this.data.addTodo("t");

		Map<String, String> memory = this.data.workingMemory();
		List<String> todo = this.data.todo();
		assertThrows(UnsupportedOperationException.class, () -> memory.put("x", "y"));
		assertThrows(UnsupportedOperationException.class, () -> todo.add("x"));
		// 视图是快照：拿到之后再改原对象，视图内容不变（避免并发遍历时的意外）。
		this.data.remember("k2", "v2");
		assertFalse(memory.containsKey("k2"));
	}

	@Test
	@DisplayName("restore：加载不是变更，不触发写穿")
	void restoreDoesNotPersist() {
		this.data.restore(Map.of("k", "v"), List.of("t"));
		assertEquals(0, this.sink.saveCount(), "恢复内容不应写盘");
		assertEquals("v", this.data.get("k"));
		assertEquals(List.of("t"), this.data.todo());
	}

	@Test
	@DisplayName("restore：覆盖而非追加（同一对象被复用时不会残留旧内容）")
	void restoreReplacesContent() {
		this.data.remember("old", "1");
		this.data.addTodo("old-todo");
		this.data.restore(Map.of("new", "2"), List.of("new-todo"));

		assertNull(this.data.get("old"));
		assertNull(this.data.get("old-todo"));
		assertEquals("2", this.data.get("new"));
		assertEquals(List.of("new-todo"), this.data.todo());
	}

	@Test
	@DisplayName("toString：只输出计数，绝不输出记忆内容（隐私）")
	void toStringHidesContent() {
		this.data.remember("api_key", "sk-super-secret");
		this.data.addTodo("secret task");

		String text = this.data.toString();
		assertNotNull(text);
		assertTrue(text.contains("session-1"), text);
		assertTrue(text.contains("memory=1") && text.contains("todo=1"), text);
		assertFalse(text.contains("sk-super-secret"), "toString 不得泄露记忆值");
		assertFalse(text.contains("secret task"), "toString 不得泄露待办内容");
	}
}
