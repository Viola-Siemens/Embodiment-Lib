package com.hexagram2021.embodimentlib.attach;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.Nullable;

/**
 * {@link Entity} 到 {@link AttachmentTarget} 的适配器。
 * <p>
 * 只做转发，不含任何逻辑；所有「空串算未附着」等语义都留在 {@link AgentAttachment} 里，
 * 以便单测用内存实现替换本适配器时行为完全一致。
 *
 * @param entity 被适配的实体（{@code Entity} 继承 {@code AttachmentHolder}，天然是 {@link IAttachmentHolder}）
 */
public record EntityAttachmentTarget(Entity entity) implements AttachmentTarget {
	@Override
	public <T> void setAttachment(AttachmentType<T> type, T value) {
		this.entity.setData(type, value);
	}

	@Override
	public <T> @Nullable T getExistingAttachment(AttachmentType<T> type) {
		// 必须用 getExistingDataOrNull：getData 会在键缺失时把默认值写回实体，
		// 使「扫描一遍世界」变成「给每个实体都挂上附着」。
		return this.entity.getExistingDataOrNull(type);
	}
}
