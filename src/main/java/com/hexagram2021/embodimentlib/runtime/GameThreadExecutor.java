package com.hexagram2021.embodimentlib.runtime;

/**
 * 「把任务排到本端游戏线程」的执行器端口（PLAN WP-3 ② 的测试缝）。
 * <p>
 * 抽成接口的理由与 WP-2 的 {@code AttachmentTarget} 相同：真实实现需要
 * {@code MinecraftServer#execute} / {@code Minecraft#execute}，二者都无法在无游戏进程的
 * 表单下实例化；而 WP-3 需要被断言的核心语义只有两条——
 * <ul>
 *   <li><b>方向正确</b>：SERVER 任务必须投递给服务器线程执行器，CLIENT 任务必须投递给客户端执行器，
 *       绝不交叉（PRD §6.3 双端隔离）；</li>
 *   <li><b>零 park</b>：提交方（IO 线程）只登记任务并立即返回，绝不等游戏线程。</li>
 * </ul>
 * 单测用记录型桩即可精确断言这两点，无需启动服务器。
 *
 * @see ThreadBridge
 *
 * @author liudongyu
 */
public interface GameThreadExecutor {
	/**
	 * 把一个任务排到本端游戏线程执行。
	 * <p>
	 * <b>实现契约</b>：
	 * <ul>
	 *   <li><b>必须立即返回</b>，不得等待任务执行完成（否则 IO 线程会退化为同步等待，
	 *       并且一旦调用方本身就是游戏线程就会死锁）；</li>
	 *   <li>任务最终应在游戏线程上执行，且执行顺序与提交顺序一致；</li>
	 *   <li>任务抛出的异常不得逃逸到游戏线程的事件循环——由提交方（{@link ThreadBridge}）
	 *       在任务体内兜底捕获并转为 observation 文本。</li>
	 * </ul>
	 *
	 * @param task 待执行任务
	 */
	void execute(Runnable task);
}
