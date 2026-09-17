package com.hexagram2021.embodimentlib.attach;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jspecify.annotations.Nullable;

/**
 * 附着承载对象的最小读写契约（PLAN WP-2 ③ 的测试缝）。
 * <p>
 * 存在的唯一理由：让 {@link AgentAttachment} 的解析逻辑可以在 <b>不构造任何
 * Minecraft 对象</b> 的前提下被 JUnit 单测覆盖。{@code Entity} 的构造会连锁触发
 * {@code EntityType} 注册表查询、{@code SynchedEntityData} 定义表构建、属性表解析
 * 与 NeoForge 事件总线投递，无法在无游戏进程的表单下实例化；而库真正关心的语义只有
 * 「读一个字符串 / 写一个字符串 / 空串算未附着」。
 * <p>
 * {@link Entity} 经由 {@link EntityAttachmentTarget} 适配；单测用内存实现即可。
 *
 * @author liudongyu
 */
public interface AttachmentTarget {
	/**
	 * 写入一个附着值。
	 *
	 * @param type 附着类型
	 * @param value 非空附着值
	 * @param <T> 附着值类型
	 */
	<T> void setAttachment(AttachmentType<T> type, T value);

	/**
	 * 读取一个已存在的附着值，<b>不产生副作用</b>。
	 * <p>
	 * 与 {@code IAttachmentHolder#getData} 的关键差异：键缺失时返回 {@code null}，
	 * 而不是把默认值写入承载对象。
	 *
	 * @param type 附着类型
	 * @param <T> 附着值类型
	 * @return 已写入的值；键缺失时为 {@code null}
	 */
	<T> @Nullable T getExistingAttachment(AttachmentType<T> type);
}
