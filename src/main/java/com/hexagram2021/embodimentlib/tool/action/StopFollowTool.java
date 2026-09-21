package com.hexagram2021.embodimentlib.tool.action;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code action.stop_follow}（PLAN WP-7 #19，PRD §4.5 #19）。
 * <p>
 * 停止当前跟随行为（清掉 {@link FollowService} 里挂在该实体身上的 goal）。
 * 无参数、幂等；结果恒为 {@code "stopped"}，理由见 {@link StopFollowLogic}。
 *
 * <h2>为什么不需要「Mob」以外的判定</h2>
 * 非 {@code Mob} 的实体根本不可能处于跟随状态，直接返回 {@code "stopped"} 就是正确答复——
 * 这与 {@code "pathfinding not supported"} 不同：后者是「做不到」，这里是「已经是你要的状态」。
 *
 * @author liudongyu
 */
public final class StopFollowTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public StopFollowTool() {
		super(
			"action.stop_follow",
			"Stop the current following behaviour started by action.follow_entity. "
				+ "Idempotent: always reports \"stopped\".",
			ToolResults.objectSchema(Map.of(), List.of()),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Mob mob = ctx.asMob();
		if (mob != null) {
			FollowService.stop(mob);
		}
		return StopFollowLogic.STOPPED;
	}
}
