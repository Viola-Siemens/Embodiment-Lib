package com.hexagram2021.embodimentlib.tool.perceive;

import com.hexagram2021.embodimentlib.tool.EmbodiedToolBase;
import com.hexagram2021.embodimentlib.tool.ToolContext;
import com.hexagram2021.embodimentlib.tool.ToolResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 内置工具 {@code perceive.block_state_at}（PLAN WP-6 #2，PRD §4.5 #2）。
 * <p>
 * 读取指定坐标的方块状态，输出标准序列化串（含朝向/含水/红石强度等属性）。
 * 纯逻辑（序列化、界内判定）见 {@link BlockStateLogic}；本类做世界侧适配。
 *
 * <h2>坐标解析</h2>
 * 输入 {@code pos} 为 {@code [x, y, z]} 三整数数组；缺失/非数组/非数字 →
 * {@code "invalid input: pos must be [x, y, z] of integers"}（工具契约的失败文本形态）。
 *
 * @author liudongyu
 */
public final class BlockStateAtTool extends EmbodiedToolBase {
	private static final String INVALID_POS = "invalid input: pos must be [x, y, z] of integers";

	/**
	 * 构造工具。
	 */
	public BlockStateAtTool() {
		super(
			"perceive.block_state_at",
			"Read the block state at a world coordinate, e.g. \"minecraft:oak_door[half=lower,facing=east]\". "
				+ "Reports \"air\" for empty space, \"void\" outside the world.",
			ToolResults.objectSchema(Map.of(
				"pos", ToolResults.prop("array", "Block coordinate [x, y, z]")), List.of("pos")),
			true);
	}

	@Override
	public String run(ToolContext ctx, Map<String, Object> input) {
		// 解析 [x,y,z]
		List<?> raw = parsePos(input);
		if (raw == null) {
			return INVALID_POS;
		}
		Integer x = toInt(raw.get(0));
		Integer y = toInt(raw.get(1));
		Integer z = toInt(raw.get(2));
		if (x == null || y == null || z == null) {
			return INVALID_POS;
		}

		Level level = ctx.level();
		boolean inWorld = BlockStateLogic.isInBuildHeight(y, level.getMinY(), level.getMaxY())
			&& level.isLoaded(new BlockPos(x, y, z));
		if (!inWorld) {
			return BlockStateLogic.VOID;
		}

		BlockState state = level.getBlockState(new BlockPos(x, y, z));
		if (state.isAir()) {
			return BlockStateLogic.AIR;
		}

		// 属性表：Property.isValidValue? 直接读 state.getValue；用 TreeMap 保证 key 升序。
		Map<String, String> properties = new TreeMap<>();
		for (Property<?> property : state.getProperties()) {
			properties.put(property.getName(), propertyValueName(property, state));
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return BlockStateLogic.serialize(id.getNamespace() + ":" + id.getPath(), properties);
	}

	/**
	 * 取属性当前值的名称。
	 * <p>
	 * {@code state.getValue(Property<T>)} 的通配捕获在 Java 泛型下无法直接在
	 * 同一个签名里喂给 {@code Property.getName(T)}（CAP 无法统一），故在此用
	 * 无检查转型拆开——属性值只是被序列化成字符串，转型损失为零。
	 *
	 * @param property 属性
	 * @param state 方块状态
	 * @return 属性值的名称（如 {@code "lower"}）
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String propertyValueName(Property property, BlockState state) {
		return property.getName(state.getValue(property));
	}

	/**
	 * 解析 {@code pos} 参数为长度 3 的列表。
	 *
	 * @param input 模型参数
	 * @return 长度 3 的列表；缺失/非列表返回 null
	 */
	@Nullable
	static List<?> parsePos(Map<String, Object> input) {
		Object raw = input.get("pos");
		if (raw instanceof List<?> list && list.size() == 3) {
			return list;
		}
		return null;
	}

	/**
	 * 把参数值转为整数。
	 *
	 * @param value 参数值
	 * @return 整数；非数字返回 null
	 */
	@Nullable
	static Integer toInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.strip());
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}