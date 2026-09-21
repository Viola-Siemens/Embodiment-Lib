package com.hexagram2021.embodimentlib.tool.action;

/**
 * 方块交互的纯逻辑层（PLAN WP-7 #14/#15 共用，无 Minecraft 依赖）。
 * <p>
 * {@code action.use_item_on} 与 {@code action.interact_with_block} 是同一件事的两个入口：
 * 前者「拿着物品去用」（可退化为空手交互），后者「空手去交互」。它们共享全部失败文本，
 * 也共享同一套「原版结果 → observation」的规约，因此放在一个纯逻辑类里，
 * 而不是复制两份必然漂移的实现。
 *
 * <h2>26.1.2 的硬约束：交互必须有 {@code Player}</h2>
 * 原版的两条交互入口——
 * {@code BlockState#useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult)}
 * 与 {@code BlockState#useWithoutItem(Level, Player, BlockHitResult)}——都<b>强制要求非空
 * {@code Player}</b>（方块实现会解引用它：{@code player.getDirection()}、
 * {@code player.isSecondaryUseActive()}、{@code player.openMenu(...)}…）。
 * 而本库作用于任意 {@code LivingEntity}（村民、自定义怪、演示实体…）。
 * 因此 0.1 的裁决是：
 * <ul>
 *   <li>绑定实体<b>是</b> {@code Player} → 走原版交互，语义完整；</li>
 *   <li>绑定实体<b>不是</b> {@code Player} → 返回 {@value #REQUIRES_PLAYER}，
 *       明确告诉模型「这个身体不具备玩家式交互能力」，而不是伪造一个玩家替身。</li>
 * </ul>
 * <b>为什么不伪造 {@code FakePlayer}</b>：那会把交互归因到一个并不存在于世界的玩家上，
 * 触发玩家侧副作用（统计、成就、菜单包、按玩家判定的领地保护），
 * 而 mob 的真实身份、位置、朝向、视线全被替换掉——成功与否都无从解释。
 * 宁可少两个工具的覆盖面，也不要一个「看起来能用但语义是假的」实现。
 * 该取舍已记入 PLAN 偏差记录，WP-9 可评估「按方块类型走 mob 自己的原版路径」
 * （例如门/活板门用 {@code DoorBlock#setOpen}，它接受 {@code @Nullable Entity}）。
 *
 * <h2>原版结果的三态 → 文本</h2>
 * 26.1.2 的 {@code InteractionResult} 是 sealed interface，有
 * {@code Success}/{@code Fail}/{@code Pass}/{@code TryEmptyHandInteraction} 四种。
 * 对模型有意义的只有三种（见 {@link Outcome}），
 * 第四种（{@code TryEmptyHandInteraction}）是「用物品没成，请试试空手」的信号，
 * 原版据此再调一次 {@code useWithoutItem}——本库在 {@link #describeItemUse} 里
 * 完整复刻这条回退链，因为「拿种子点耕地」正是靠它才成立的。
 *
 * @author liudongyu
 */
public final class BlockInteractionLogic {
	/** 绑定实体不是 {@code Player}、无法执行原版方块交互时的 observation。 */
	public static final String REQUIRES_PLAYER = "interaction requires a player body";
	/** 目标方块超出手臂可及范围时的 observation。 */
	public static final String OUT_OF_REACH = "out of reach";
	/** 目标坐标在世界外或区块未加载时的 observation。 */
	public static final String OUT_OF_WORLD = "target out of world";
	/** 交互没有产生任何效果时的 observation（原版 {@code PASS}）。 */
	public static final String NO_EFFECT = "no effect";
	/** 交互被明确拒绝/失败时的 observation（原版 {@code FAIL}）。 */
	public static final String INTERACTION_FAILED = "interaction failed";
	/** 空手交互成功时的 observation。 */
	public static final String INTERACTED = "interacted";
	/** 空手交互对方块毫无作用时的 observation（PRD #15 约定文本）。 */
	public static final String NOT_INTERACTABLE = "not interactable";

	/**
	 * 原版交互结果对模型有意义的三态 + 一个控制信号。
	 */
	public enum Outcome {
		/** 交互被接受并产生了效果（原版 {@code Success}）。 */
		SUCCESS,
		/** 交互被明确拒绝/失败（原版 {@code Fail}）。 */
		FAILURE,
		/** 什么也没发生（原版 {@code Pass}）。 */
		NO_EFFECT,
		/** 手里的物品不接管这次交互，原版会退化为空手交互。 */
		TRY_WITH_EMPTY_HAND
	}

	private BlockInteractionLogic() {
	}

	/**
	 * 规约「用物品点方块」的结果（含原版的空手回退链）。
	 *
	 * @param itemId 手持物品完整资源标识符
	 * @param blockId 目标方块完整资源标识符
	 * @param itemOutcome 原版 {@code useItemOn} 的结果
	 * @param emptyHandOutcome 空手回退的结果；未发生回退时传 {@link Outcome#NO_EFFECT}
	 * @return observation 文本
	 */
	public static String describeItemUse(String itemId, String blockId,
										 Outcome itemOutcome, Outcome emptyHandOutcome) {
		return switch (itemOutcome) {
			case SUCCESS -> "used " + itemId + " on " + blockId;
			case FAILURE -> INTERACTION_FAILED;
			case TRY_WITH_EMPTY_HAND -> describeBlockUse(emptyHandOutcome);
			case NO_EFFECT -> NO_EFFECT;
		};
	}

	/**
	 * 规约「空手点方块」的结果。
	 *
	 * @param outcome 原版 {@code useWithoutItem} 的结果
	 * @return observation 文本
	 */
	public static String describeBlockUse(Outcome outcome) {
		return switch (outcome) {
			case SUCCESS -> INTERACTED;
			case FAILURE -> INTERACTION_FAILED;
			case NO_EFFECT, TRY_WITH_EMPTY_HAND -> NOT_INTERACTABLE;
		};
	}
}
