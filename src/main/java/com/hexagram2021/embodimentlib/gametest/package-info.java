/**
 * 26.1 GameTest 适配层：mod 测试函数的装载与测试实例注册。
 * <br/>
 * 负责：
 * <br/>
 * <ol>
 *   <li>通过 {@code TestFunctionLoader} 把测试函数写入 {@code TEST_FUNCTION} 注册表</li>
 *   <li>通过 {@code RegisterGameTestsEvent}（Mod 总线）注册测试实例</li>
 *   <li>仅在 {@code runGameTestServer} / 开发环境生效，生产环境零开销</li>
 * </ol>
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.gametest;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;