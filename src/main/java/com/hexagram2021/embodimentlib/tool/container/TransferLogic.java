package com.hexagram2021.embodimentlib.tool.container;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * {@code container.transfer} 的纯逻辑层（PLAN WP-7 #21，无 Minecraft 依赖）。
 * <p>
 * 世界交互面（取两个容器、真正读写槽位）在 {@link TransferContainerTool} 中完成；
 * 本类负责<b>「到底能搬走几个」的算术与全部失败文本</b>——这恰恰是容器类工具最容易
 * 写错、也最值得单测的地方（目标槽已有同种物品要合并、不同物品要拒绝、
 * 目标容量上限、源数量不足、部分搬运）。
 *
 * <h2>槽位的抽象</h2>
 * 用 {@link SlotView}（物品 id + 数量，空槽 id 为 null）描述一个槽，
 * 于是全部搬运规则都变成对两个不可变小对象做判断，无需任何 Minecraft 类型。
 *
 * <h2>与 PLAN schema 的一处刻意增补：{@code direction}</h2>
 * PLAN WP-7 #21 给的参数是 {@code {"pos", "from_slot", "to_slot", "count"}}，
 * 但只有<b>一个</b> {@code pos} 和两个槽号时，「从容器搬到身上」还是「从身上搬进容器」
 * 在参数上无法区分——同一个调用可以有两种完全相反的解读，而选错会真的搬错方向。
 * 因此本实现增加一个可选参数 {@code direction}（{@code "deposit"} 默认 / {@code "withdraw"}），
 * 让方向由模型明说而不是由库猜。原有四个参数的语义不变
 * （{@code from_slot} 是<b>来源</b>侧槽号，{@code to_slot} 是<b>目标</b>侧槽号）。
 * 该增补已记入 PLAN 偏差记录。
 *
 * @author liudongyu
 */
public final class TransferLogic {
	/** 搬运成功。 */
	public static final String TRANSFERRED = "transferred";
	/** 来源槽数量不足（含来源槽为空）时的 observation。 */
	public static final String NOT_ENOUGH_ITEMS = "not enough items";
	/** 目标槽已占用（不同物品，或已满）时的 observation。 */
	public static final String TARGET_SLOT_OCCUPIED = "target slot occupied";
	/** {@code direction} 非法时的 observation。 */
	public static final String INVALID_DIRECTION =
		"invalid input: direction must be \"deposit\" or \"withdraw\"";
	/** {@code direction} 参数名。 */
	public static final String DIRECTION_KEY = "direction";
	/** 方向：从绑定实体库存搬进方块容器（默认）。 */
	public static final String DEPOSIT = "deposit";
	/** 方向：从方块容器搬进绑定实体库存。 */
	public static final String WITHDRAW = "withdraw";

	/** 一个槽位的抽象视图；{@code itemId} 为 null 表示空槽。 */
	public record SlotView(@Nullable String itemId, int count) {
		/**
		 * 该槽是否为空。
		 *
		 * @return 空（无物品或数量非正）返回 true
		 */
		public boolean isEmpty() {
			return this.itemId == null || this.count <= 0;
		}
	}

	/** 搬运方案；{@code error} 非空时 {@code count} 无意义。 */
	public record Decision(int count, @Nullable String error) {
		/**
		 * 构造一个可执行的搬运方案。
		 *
		 * @param count 实际搬运数量
		 * @return 方案
		 */
		public static Decision of(int count) {
			return new Decision(count, null);
		}

		/**
		 * 构造一个失败结果。
		 *
		 * @param error 应回喂模型的错误文本
		 * @return 失败结果
		 */
		public static Decision rejected(String error) {
			return new Decision(0, error);
		}
	}

	private TransferLogic() {
	}

	/**
	 * 解析 {@code direction} 参数（大小写不敏感）。
	 *
	 * @param raw 原始参数；可为 null（表示缺省 {@link #DEPOSIT}）
	 * @return 方向常量；非法时为 null
	 */
	public static @Nullable String parseDirection(@Nullable Object raw) {
		if (raw == null) {
			return DEPOSIT;
		}
		String direction = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
		if (DEPOSIT.equals(direction) || WITHDRAW.equals(direction)) {
			return direction;
		}
		return null;
	}

	/**
	 * 规划一次搬运。
	 * <p>
	 * 判定顺序与理由：
	 * <ol>
	 *   <li>来源为空/数量不足 → {@link #NOT_ENOUGH_ITEMS}（PLAN 明确要求把
	 *       {@code count} 超过现有数量的情形报为「数量不足」，而不是像
	 *       {@code action.drop_item} 那样截断——搬运是双向写入，静默少搬会让模型
	 *       以为库存已经对上了）；</li>
	 *   <li>目标槽有<b>不同</b>物品 → {@link #TARGET_SLOT_OCCUPIED}；</li>
	 *   <li>目标槽已满 → {@link #TARGET_SLOT_OCCUPIED}；</li>
	 *   <li>其余：实际搬运量取「请求量」与「目标剩余容量」的较小值。目标装不下时的
	 *       <b>部分搬运</b>是原版行为（shift 点击就是能塞多少塞多少），
	 *       故不报错，也已在文档中说明。</li>
	 * </ol>
	 *
	 * @param source 来源槽视图
	 * @param target 目标槽视图
	 * @param requested 请求数量；{@code null} 表示「整叠」
	 * @param targetLimit 目标槽的单槽容量上限
	 * @return 搬运方案
	 */
	public static Decision plan(SlotView source, SlotView target, @Nullable Integer requested, int targetLimit) {
		if (source.isEmpty()) {
			return Decision.rejected(NOT_ENOUGH_ITEMS);
		}
		boolean targetEmpty = target.isEmpty();
		if (!targetEmpty && !source.itemId().equals(target.itemId())) {
			return Decision.rejected(TARGET_SLOT_OCCUPIED);
		}
		int space = targetLimit - target.count();
		if (targetEmpty) {
			// 空槽的「已占数量」即使被实现方塞了脏数据也不参与计算。
			space = targetLimit;
		}
		if (space <= 0) {
			return Decision.rejected(TARGET_SLOT_OCCUPIED);
		}
		int wanted = requested == null ? source.count() : requested;
		if (wanted > source.count()) {
			return Decision.rejected(NOT_ENOUGH_ITEMS);
		}
		return Decision.of(Math.min(wanted, space));
	}
}
