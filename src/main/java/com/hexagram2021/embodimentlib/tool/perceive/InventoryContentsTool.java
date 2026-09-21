package com.hexagram2021.embodimentlib.tool.perceive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 内置工具 {@code perceive.inventory_contents}（PLAN WP-6 #3，PRD §4.5 #3）。
 * <p>
 * 列出绑定实体的库存：槽位、物品、数量、耐久。实体没有库存（未实现
 * {@link Container}）→ {@code "inventory not supported on this entity"}。
 * 纯逻辑（槽行规约、文本拼接）见 {@link InventoryLogic}。
 *
 * <h2>只列非空槽</h2>
 * 逐槽输出会包含大量 {@code "empty"} 噪声（玩家 36 槽、马 15 槽…），
 * 对 LLM 上下文是浪费。因此本工具只输出<b>非空槽</b>，且每行带真实槽位索引——
 * 模型据此知道「slot 5 有 3x 石头」，与空槽语义不冲突。
 *
 * @author liudongyu
 */
public final class InventoryContentsTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public InventoryContentsTool() {
		super(
			"perceive.inventory_contents",
			"List the executing entity's inventory: slot index, item type, count, durability. "
				+ "Reports \"inventory not supported on this entity\" if the body has no inventory.",
			ToolResults.objectSchema(Map.of(), List.of()),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Container container = resolveContainer(ctx);
		if (container == null) {
			return InventoryLogic.NOT_SUPPORTED;
		}
		List<String> lines = new ArrayList<>();
		int size = container.getContainerSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + ":"
				+ BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (stack.isDamageableItem()) {
				lines.add(InventoryLogic.slotLineDurability(slot, itemId, stack.getCount(),
					stack.getDamageValue(), stack.getMaxDamage()));
			} else {
				lines.add(InventoryLogic.slotLine(slot, itemId, stack.getCount()));
			}
		}
		return InventoryLogic.join(lines);
	}

	/**
	 * 解析实体的库存容器。
	 * <p>
	 * {@code Player} 的库存不是 {@code Container}（它实现 {@code ContainerUser}，库存是
	 * {@code Inventory}），需要经 {@code getInventory()} 解包；其余实体直接看是否实现
	 * {@code Container}（带箱子的 mob、容器矿车等）。
	 *
	 * @param ctx 工具上下文
	 * @return 库存容器；实体没有库存时返回 null
	 */
	@Nullable
	private static Container resolveContainer(ToolContext ctx) {
		if (ctx.entity() instanceof Player player) {
			return player.getInventory();
		}
		return ctx.entity() instanceof Container container ? container : null;
	}
}