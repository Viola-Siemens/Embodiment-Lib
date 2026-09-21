package com.hexagram2021.embodimentlib.tool.loco;

import java.util.List;
import java.util.Map;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * 内置工具 {@code loco.move_to}（PLAN WP-6 #7，PRD §4.5 #7）。
 * <p>
 * 用原版寻路把绑定实体带向目标坐标。仅对实现了 {@code PathNavigation} 的
 * {@link Mob} 可用；其它实体返回 {@code "pathfinding not supported"}。
 * 纯逻辑（结果裁决）见 {@link MoveToLogic}。
 *
 * <h2>一次调用 = 启动寻路 + 报告快照</h2>
 * 本工具<b>不阻塞等待到达</b>：调用后实体沿寻路移动，到达检测由后续调用收敛
 * （每次调用重新评估距离）。这是刻意的——工具执行不允许 park 游戏线程，
 * 「走到再回话」是多步推理的事，不是单次工具调用的事。
 *
 * <h2>坐标</h2>
 * 输入 {@code x/y/z} 为目标方块中心坐标；{@code reach} 为到达阈值（默认 2 格）。
 *
 * @author liudongyu
 */
public final class MoveToTool extends EmbodiedToolBase {
	private static final String INVALID_COORDS = "invalid input: x, y, z must be numbers";
	private static final double DEFAULT_REACH = 2.0;
	private static final double SPEED = 1.0;

	/**
	 * 构造工具。
	 */
	public MoveToTool() {
		super(
				"loco.move_to",
				"Path toward a target position using vanilla pathfinding. The entity will start "
						+ "walking; call again to check convergence. Reports \"arrived\", \"path blocked\", "
						+ "\"distance D remaining\", or \"pathfinding not supported\".",
				ToolResults.objectSchema(
						Map.of(
								"x", ToolResults.prop("number", "Target X"),
								"y", ToolResults.prop("number", "Target Y"),
								"z", ToolResults.prop("number", "Target Z"),
								"reach", ToolResults.prop("number", "Arrival threshold in blocks (default 2)")),
						List.of("x", "y", "z")
				),
				false
		);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		Double x = toDouble(input.get("x"));
		Double y = toDouble(input.get("y"));
		Double z = toDouble(input.get("z"));
		if (x == null || y == null || z == null) {
			return INVALID_COORDS;
		}
		double reach = ToolResults.doubleParam(input, "reach", DEFAULT_REACH);
		if (reach <= 0) {
			reach = DEFAULT_REACH;
		}

		Mob mob = ctx.asMob();
		if (mob == null) {
			return MoveToLogic.NOT_SUPPORTED;
		}
		boolean pathFound = mob.getNavigation().moveTo(x, y, z, SPEED);
		double distance = Math.sqrt(mob.distanceToSqr(x, y, z));
		return MoveToLogic.describe(true, distance, reach, pathFound);
	}

	@Nullable
	private static Double toDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}