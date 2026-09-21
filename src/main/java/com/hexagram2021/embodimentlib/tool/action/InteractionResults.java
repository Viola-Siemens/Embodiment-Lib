package com.hexagram2021.embodimentlib.tool.action;

import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

/**
 * 26.1.2 {@code InteractionResult} → 纯逻辑 {@link BlockInteractionLogic.Outcome} 的唯一映射点。
 * <p>
 * 把映射单独放一个类，是为了让「文本怎么判」完全留在纯逻辑层
 * （{@link BlockInteractionLogic}，可在无游戏进程下单测），而适配器只负责这一层
 * 无判断的翻译。若把映射塞进工具类，两个交互工具就会各写一份
 * {@code instanceof} 链，迟早对同一种结果给出不同文本。
 * <p>
 * 26.1.2 的 {@code InteractionResult} 是 sealed interface：
 * {@code Success} / {@code Fail} / {@code Pass} / {@code TryEmptyHandInteraction}。
 * 其中 {@code Success#consumesAction()} 为 true，其余为 false——这正是原版判断
 * 「交互是否生效」的唯一依据（见 {@code ServerPlayerGameMode#useItemOn}）。
 *
 * @author liudongyu
 */
final class InteractionResults {
	private InteractionResults() {
	}

	/**
	 * 把原版交互结果映射为纯逻辑三态。
	 *
	 * @param result 原版结果；理论非空，为空时按「无效果」处理
	 * @return 映射结果
	 */
	static BlockInteractionLogic.Outcome of(@Nullable InteractionResult result) {
		return switch (result) {
			case null ->
				// 极少见：某些方块实现可能返回 null。按「什么也没发生」处理，
				// 绝不让一次交互把工具推进异常路径（PRD §4.5：失败转 observation）。
					BlockInteractionLogic.Outcome.NO_EFFECT;
			case InteractionResult.Success _ -> BlockInteractionLogic.Outcome.SUCCESS;
			case InteractionResult.Fail _ -> BlockInteractionLogic.Outcome.FAILURE;
			case InteractionResult.TryEmptyHandInteraction _ ->
					BlockInteractionLogic.Outcome.TRY_WITH_EMPTY_HAND;
			default -> BlockInteractionLogic.Outcome.NO_EFFECT;
		};
	}
}
