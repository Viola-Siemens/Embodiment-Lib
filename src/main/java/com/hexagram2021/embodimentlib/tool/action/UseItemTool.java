package com.hexagram2021.embodimentlib.tool.action;

import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 内置工具 {@code action.use_item}（PLAN WP-6 #13，PRD §4.5 #13）。
 * <p>
 * 使用手持物品：吃、拉弓、掷药水等。通过 {@code LivingEntity#startUsingItem}
 * 启动物品的使用流程（由原版 tick 驱动完成实际效果）；本工具只做「开始使用」，
 * 文本如实报告{itemId + 是否可消耗}。
 * 纯逻辑（手别解析、文本规约）见 {@link UseItemLogic}。
 *
 * <h2>非玩家实体</h2>
 * {@code ItemStack.use} 的完整语义需要 {@code Player}（右击交互），而本库作用于任意
 * {@code LivingEntity}（村民、猪…）。0.1 的折中：调用 {@code startUsingItem}——
 * 对可消耗物品（食物/药水）启动正确的使用 tick，对其它物品是安全的空启动
 * （{@code getUseDuration == 0} 时不会触发动画/消耗）。
 *
 * @author liudongyu
 */
public final class UseItemTool extends EmbodiedToolBase {
	private static final String INVALID_HAND = "invalid input: hand must be \"main\" or \"off\"";

	/**
	 * 构造工具。
	 */
	public UseItemTool() {
		super(
			"action.use_item",
			"Right-click/use the held item in the air (eat, draw bow, throw potion). "
				+ "Reports \"used <item>\", \"no item in hand\", or invalid-input text.",
			ToolResults.objectSchema(Map.of(
				"hand", ToolResults.enumProp("Which hand to use (default \"main\")",
					List.of(UseItemLogic.DEFAULT_HAND, UseItemLogic.OFF_HAND))),
				List.of()),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		String handName = UseItemLogic.parseHand(input);
		if (handName == null) {
			return INVALID_HAND;
		}
		InteractionHand hand = UseItemLogic.OFF_HAND.equals(handName)
			? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack stack = ctx.entity().getItemInHand(hand);
		if (stack.isEmpty()) {
			return UseItemLogic.NO_ITEM_IN_HAND;
		}
		ctx.entity().startUsingItem(hand);
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
			+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean consumable = stack.get(DataComponents.CONSUMABLE) != null;
		return UseItemLogic.describe(itemId, consumable);
	}
}