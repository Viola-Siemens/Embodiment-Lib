package com.hexagram2021.embodimentlib.runtime;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link GameThreadExecutor} 的记录型测试桩（PLAN WP-3 ② 验收）。
 * <p>
 * 提供两种驱动方式，分别对应两类断言：
 * <ul>
 *   <li><b>同步模式</b>（{@link #synchronous()}）：{@code execute} 里立即跑任务，
 *       用于断言桥接的「结果/异常/空值」语义——这些与线程无关；</li>
 *   <li><b>手动模式</b>（{@link #manual()}）：任务只入队不执行，
 *       由测试显式 {@link #drain()}。用于断言 <b>零 park</b>：
 *       在 {@code drain()} 之前，IO 线程必须仍在等待，说明游戏线程没有被占用；
 *       也用于模拟「游戏线程很慢」从而触发超时。</li>
 * </ul>
 */
final class RecordingExecutor implements GameThreadExecutor {
	private final List<Runnable> pending = new CopyOnWriteArrayList<>();
	private final List<String> executedThreadNames = new CopyOnWriteArrayList<>();
	private final AtomicInteger submitCount = new AtomicInteger();
	private final boolean autoRun;
	private final CountDownLatch submitted;

	private RecordingExecutor(boolean autoRun, int expectedSubmits) {
		this.autoRun = autoRun;
		this.submitted = new CountDownLatch(expectedSubmits);
	}

	/** 立即执行模式。 */
	static RecordingExecutor synchronous() {
		return new RecordingExecutor(true, 1);
	}

	/** 手动执行模式：任务入队，等待 {@link #drain()}。 */
	static RecordingExecutor manual() {
		return new RecordingExecutor(false, 1);
	}

	@Override
	public void execute(Runnable task) {
		this.submitCount.incrementAndGet();
		this.submitted.countDown();
		if (this.autoRun) {
			// 记录的是「谁执行了任务」，用于断言任务确实跑在执行器线程上。
			this.executedThreadNames.add(Thread.currentThread().getName());
			task.run();
		} else {
			this.pending.add(task);
		}
	}

	/**
	 * 执行所有已排队的任务（模拟游戏线程开始干活）。
	 *
	 * @return 本次执行的任务数
	 */
	int drain() {
		List<Runnable> batch = Lists.newArrayList(this.pending);
		this.pending.clear();
		batch.forEach(task -> {
			this.executedThreadNames.add(Thread.currentThread().getName());
			task.run();
		});
		return batch.size();
	}

	/** @return 尚未执行的任务数 */
	int pendingCount() {
		return this.pending.size();
	}

	/** @return {@code execute} 被调用的总次数 */
	int submitCount() {
		return this.submitCount.get();
	}

	/** @return 执行任务时所在的线程名列表 */
	List<String> executedThreadNames() {
		return List.copyOf(this.executedThreadNames);
	}

	/**
	 * 等待至少一次提交发生，用于「派发是异步的」这类断言前的同步。
	 *
	 * @param timeout 等待上限
	 * @return 是否在超时前观察到提交
	 * @throws InterruptedException 等待被中断
	 */
	boolean awaitSubmit(long timeout) throws InterruptedException {
		return this.submitted.await(timeout, TimeUnit.MILLISECONDS);
	}
}
