/**
 * WP-1 配置系统。
 * <br/>
 * 负责：
 * <br/>
 * <ol>
 *   <li>双端独立 ModConfigSpec（server.toml / client.toml，互不读取）</li>
 *   <li>Profile 路由：default + routing 表 + 命名 profile 表</li>
 *   <li>配置加载后的路由引用校验（WARN 级日志，不抛异常）</li>
 * </ol>
 */
@NullMarked
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package com.hexagram2021.embodimentlib.config;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.jspecify.annotations.NullMarked;

import javax.annotation.ParametersAreNonnullByDefault;
