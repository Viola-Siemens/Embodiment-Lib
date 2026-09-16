package com.hexagram2021.embodimentlib.gametest;

import java.util.List;

import com.hexagram2021.embodimentlib.EmbodimentLib;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.BuiltinTestFunctions;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * WP-1 的 GameTest：在完整服务端生命周期内验证 mod 侧的 GameTest 注册/发现/执行链。
 * <p>
 * 26.1 注册链：{@code RegisterGameTestsEvent}（Mod 总线）→ 注册
 * {@code FunctionGameTestInstance(函数键, TestData(环境, 结构, 时长, ...))}；
 * 运行时 {@code FunctionGameTestInstance#run} 从 TEST_FUNCTION 注册表取函数执行。
 * <p>
 * <b>已知限制（PLAN 决策记录 R-2）</b>：26.1 的 TEST_FUNCTION 是 simple registry，
 * 其 bootstrap（{@code runLoaders}）在 mod 构造前已执行，mod 无法在运行时注册自定义测试函数；
 * 因此这里使用 vanilla 内置函数键 {@code minecraft:always_pass} 走通端到端链路，
 * 配置本身的行为断言由 JUnit（EmbodimentConfigTest）与冒烟日志检查点承担。
 */
public final class ConfigGameTests {
	private ConfigGameTests() {
	}

	/**
	 * 注册游戏测试
	 * @param event 游戏测试注册事件
	 */
	public static void onRegisterGameTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
				Identifier.fromNamespaceAndPath(EmbodimentLib.MODID, "default"),
				new TestEnvironmentDefinition.AllOf(List.of())
		);
		TestData<Holder<TestEnvironmentDefinition<?>>> testData = new TestData<>(
				environment, Identifier.withDefaultNamespace("empty"), 120, 0, true
		);
		event.registerTest(
				Identifier.fromNamespaceAndPath(EmbodimentLib.MODID, "wiring_smoke"),
				new FunctionGameTestInstance(BuiltinTestFunctions.ALWAYS_PASS, testData)
		);
	}
}
