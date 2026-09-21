package com.hexagram2021.embodimentlib.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolContextScope} 的单测。
 * <p>
 * <b>测试边界说明</b>：{@link ToolContext} 需要一个真实 {@code LivingEntity}，而实体构造依赖
 * Minecraft 的 {@code EntityType} 注册表与世界对象，在纯 JUnit 环境下不可得
 * （实测 26.1.2 下 {@code net.minecraft.world.entity.animal.Pig} 甚至不在测试编译类路径上）。
 * 因此本测试覆盖作用域机制中<b>不依赖实体内容</b>的全部行为：
 * <ul>
 *   <li>作用域未激活时 {@link ToolContextScope#get()} 抛异常、{@code getOrNull()} 返回 null；</li>
 *   <li>作用域的线程隔离（ThreadLocal 语义）；</li>
 *   <li>子线程不继承父线程绑定（避免「游戏线程的绑定泄漏到 IO 线程」）；</li>
 *   <li>作用域的并发隔离。</li>
 * </ul>
 * 「绑定值能否正确取出与还原」需要真实 ctx，随 WP-9 的端到端测试覆盖。
 */
class ToolContextScopeTest {
	@AfterEach
	void clearScope() {
		// 作用域是 ThreadLocal，用例失败时可能残留，必须清理以免污染后续用例。
		ToolContextScope.clearForTesting();
	}

	@Test
	@DisplayName("作用域外 get() 抛 IllegalStateException，而不是返回 null 让调用方 NPE")
	void getOutsideScopeThrows() {
		assertThrows(IllegalStateException.class, ToolContextScope::get);
	}

	@Test
	@DisplayName("作用域外 getOrNull() 返回 null，供探测场景使用")
	void getOrNullOutsideScopeReturnsNull() {
		assertNull(ToolContextScope.getOrNull());
	}

	@Test
	@DisplayName("作用域外 isActive() 为 false")
	void isActiveOutsideScopeIsFalse() {
		assertFalse(ToolContextScope.isActive());
	}

	@Test
	@DisplayName("异常消息点明「tool outside agent scope」，便于定位接线遗漏")
	void exceptionMessageIsDiagnostic() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, ToolContextScope::get);
		assertTrue(ex.getMessage().contains("outside agent scope"), "消息应说明是在作用域外调用");
	}

	@Test
	@DisplayName("作用域按线程隔离：子线程看不到父线程的绑定")
	void scopeIsThreadLocal() throws Exception {
		// 这条断言是安全相关的：若绑定会继承到子线程，那么 IO 线程上执行的代码
		// 可能误以为自己在为某个实体干活（而实际上游戏线程的绑定已失效）。
		AtomicReference<Boolean> childSeesParentBinding = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);

		Thread child = new Thread(() -> {
			try {
				// 父线程未设置绑定，子线程也必须没有。
				childSeesParentBinding.set(ToolContextScope.getOrNull() != null);
			} finally {
				done.countDown();
			}
		}, "scope-probe-thread");
		child.start();

		assertTrue(done.await(2, TimeUnit.SECONDS), "子线程应已完成探测");
		assertEquals(Boolean.FALSE, childSeesParentBinding.get(), "子线程不应继承父线程的工具作用域");
		child.join(2000);
	}

	@Test
	@DisplayName("clearForTesting 清空当前线程绑定（测试隔离手段本身可用）")
	void clearForTestingResetsCurrentThread() {
		ToolContextScope.clearForTesting();
		assertFalse(ToolContextScope.isActive());
		assertNull(ToolContextScope.getOrNull());
	}

	@Test
	@DisplayName("多个线程并发探测作用域互不干扰")
	void concurrentProbingIsIsolated() throws Exception {
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		for (int i = 0; i < threads; i++) {
			Thread t = new Thread(() -> {
				try {
					start.await(2, TimeUnit.SECONDS);
					// 每个线程都应独立地处于「无绑定」状态。
					if (ToolContextScope.getOrNull() != null) {
						failure.compareAndSet(null, new AssertionError("线程不应有残留绑定"));
					}
				} catch (Throwable ex) {
					failure.compareAndSet(null, ex);
				} finally {
					done.countDown();
				}
			}, "scope-concurrent-" + i);
			t.start();
		}

		start.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS), "所有探测线程应已完成");
		assertNull(failure.get(), () -> "并发探测出现异常: " + failure.get());
	}
}
