package com.hexagram2021.embodimentlib.tool.loco;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;

import java.util.List;
import java.util.Map;

/**
 * 内置工具 {@code loco.jump}（PLAN WP-6 #9，PRD §4.5 #9）。
 * <p>
 * 让绑定实体原地起跳（用于模型让实体跳过障碍物）。无参数，恒返回
 * {@link JumpLogic#JUMPED}（PRD #9：Always "jumped"）。
 * <p>
 * 跳跃由 MCP 调用 {@code LivingEntity#jumpFromGround()} 完成——对水面/空中实体
 * 无效果但也不报错（一次动作请求而已，是否达成取决于实体的物理状态，模型据此
 * 自行判断是否需要再跳）。
 *
 * @author liudongyu
 */
public final class JumpTool extends EmbodiedToolBase {
	/**
	 * 构造工具。
	 */
	public JumpTool() {
		super(
			"loco.jump",
			"Make the entity jump once (used to clear small obstacles). Always reports \"jumped\".",
			ToolResults.objectSchema(Map.of(), List.of()),
			false);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		ctx.entity().jumpFromGround();
		return JumpLogic.JUMPED;
	}
}