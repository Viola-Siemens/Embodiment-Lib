# Embodiment Lib — 执行计划（Sub-Agent 可直接执行的 Spec）

| Field | Value |
|---|---|
| Project | Embodiment Lib (`embodimentlib`) |
| Based on | `docs/v0.1.0/PRD.md` v0.1（Draft for alignment） |
| Version | 0.1-plan |
| Owner | Liu Dongyu |
| Last updated | 2026-09-15 |
| 适用读者 | 执行本项目的子 Agent（每个工作包独立可执行） |

---

## 0. 子 Agent 阅读与执行规则

1. 本文件是 PRD 的**可执行分解**。PRD 是需求唯一权威来源；本文件解决 PRD 中标注「开放决策延后到实现」的所有决策（见 §8），并给出代码级设计。若本文件与 PRD 冲突，以 PRD 为准并回报差异。
2. 每个工作包（WP）**独立可执行**：先读 §4 对应 WP 小节，检查其「前置依赖」是否已合并；未合并则按该 WP 给出的**桩类契约**自行建立最小桩，不与上游阻塞。
3. 执行顺序建议见 §5（依赖图）。先做 P0 链（§5.1 Phase 1），再做 P1。
4. 每个 WP 完成 = 「验收标准」全部勾选 + 「验证命令」通过 + 更新 §4 该 WP 的状态行（`✅ 已完成 / 🚧 进行中 / ⬜ 未开始`）。
5. 不确定的第三方 API 签名（主要是 `agentscope-harness 2.0.1`）在 WP 内以 ⚠️ 标注；实现时必须解包依赖 jar / 读 JavaDoc 核对后再落码，禁止臆造签名。
6. 代码风格：Tabs 缩进（与现有 `EmbodimentLib.java` 一致）、UTF-8、JDK 25；公共 API 加 Javadoc；日志统一走 `SLF4J`，logger 名空间 `embodimentlib`（见 §3.7）。

---

## 1. 目标与范围

### 1.1 一句话目标

交付一个 NeoForge **库模组**：给任意 `LivingEntity` 挂载 LLM 智能体（brain），内置 AgentScope Java 运行时、TOML 模型路由、23 个原子工具、会话记忆、调试命令与演示实体；让 addon 作者几十行代码做出「会听、会想、会走、会挖、会答」的实体。

### 1.2 MECE 分解（PRD → 工作包全映射）

| WP | 工作包 | 覆盖 PRD 章节 | 优先级 |
|---|---|---|---|
| WP-0 | 构建基线、JarInJar 打包与元数据 ✅ | §6.1, §6.2(打包), §6.6 | P0 |
| WP-1 | 配置系统：`AgentHostSide` + 双端 TOML + Profile 路由 ✅ | §4.1.1, §4.3, §6.4(配置) | P0 |
| WP-2 | 实体附着（Attachment）+ 双端 Agent 注册表 ✅ | §4.1.2, §4.1.3, §6.3(注册表), §6.4(附着) | P0 |
| WP-3 | 代理运行时包装（HarnessAgent 包装 + 双环 + 线程桥接）✅ | §4.2, §6.2(运行时), §6.3(线程) | P0 |
| WP-4 | 会话与记忆持久化（session-id 维度） | §4.4, §6.4(会话) | P0 |
| WP-5 | 工具契约与基础设施（含权限钩子、Griefing 集成）✅ | §4.5(契约), §4.6(权限), §4.9, §6.2(工具) | P0（权限钩子 P1） |
| WP-6 | P0 内置工具（12 个） | §4.5 #1,2,3,6,7,9,10,11,13,16,22,23；§5 P0 | P0 |
| WP-7 | P1 内置工具（11 个） | §4.5 #4,5,8,12,14,15,17,18,19,20,21；§5 P1 | P1 |
| WP-8 | 调试命令与可观测性（`/embodimentlib inspect`） | §4.7 | P0 |
| WP-9 | 演示实体 `demo_agent`（召唤 + 端到端循环） | §4.8 | P0 |
| WP-10 | Addon 扩展面与库分发验证（公开 API + 示例 addon） | §4.6(注册), §6.5, §6.6 | P1 |

Persona/Story 覆盖：§2.1（Addon Modder）→ WP-2/3/5/6/7/10；§2.2（Server Owner）→ WP-1/8；§2.3（End Player）→ 不直接触达，由 WP-9 演示保证。

### 1.3 范围外（本计划不实现，PRD §1.4「Out of scope」）

玩家 UI/聊天交互系统、第三方领地保护集成、token 排队/背压、Fabric/Quilt/Forge、视觉多模态、语音、MCP 服务端、跨版本迁移。本计划不安排任何 WP 覆盖上述内容。

---

## 2. 基线与环境（当前仓库实测状态）

| 项 | 现状 |
|---|---|
| 仓库根 | `C:\Projects\Embodiment-Lib` |
| 构建 | Gradle + `net.neoforged.moddev` `2.0.141`；`java-library` + `maven-publish` + `idea` 已配 |
| JDK | Java 25 工具链（`JavaLanguageVersion.of(25)`），Liberica 25.0.4.1+ |
| MC / NeoForge | `26.1.2` / `26.1.2.71`（`gradle.properties`）；版本范围 `minecraft_version_range=[26.1, 26.2)`、`neo_version_range=[26,)`、`loader_version_range=[4,)`；runs：client/server/gameTestServer/data 已配 |
| 现有代码 | 仅 `src/main/java/com/hexagram2021/embodimentlib/EmbodimentLib.java` |
| ⚠️ 已知缺陷 | 主类 `MODID = "girlfriends"`（模板残留），必须改为 `"embodimentlib"`（WP-0） |
| 缺失 | `src/main/templates/`（build.gradle 引用了它，不存在）、`src/main/resources/`、`pack.mcmeta`、AgentScope 依赖、JarInJar 配置 |
| 发布 | `publishing` 已配到本地文件仓库 `repo/`（file://${projectDir}/repo），`maven-publish` 可用 |
| License | Artistic-2.0（根目录 LICENSE）；PRD 声明 AgentScope 为 Apache-2.0 bundled |

**构建/验证统一命令**（Windows，仓库根执行）：
- 单元测试（**验收唯一标准**）：`.\gradlew.bat test`
- 编译：`.\gradlew.bat build`
- 游戏测试 / 冒烟（**仅作启动冒烟，不承载行为断言、不计入验收**）：`.\gradlew.bat runGameTestServer`——启动测试服务器，走完服务端**完整生命周期**，自动运行 `neoforge.enabledGameTestNamespaces=embodimentlib` 下的测试实例，运行结束后**自动终止**；26.1 起无 `@GameTest` 注解且 mod 无法注册自定义测试函数（§8 决策 10），故该命令只验证「mod 能正常起停 + 注册链通畅」
- 发布到本地仓库：`.\gradlew.bat publish`
- 打开游戏（手动验证）：`.\gradlew.bat runClient`；交互式服务器（不会自动退出）：`.\gradlew.bat runServer`

---

## 3. 统一约定（所有 WP 共同遵守）

### 3.1 包结构（总布局）

```
com.hexagram2021.embodimentlib
├── EmbodimentLib.java        # 主类，MODID="embodimentlib"（WP-0 修复）
├── api/                      # 公开 Addon API（WP-10），其余包实现可随实现调整
│   ├── AgentHostSide.java    # 枚举 SERVER/CLIENT（WP-1 定义，全项目唯一来源）
│   ├── AgentProfile.java     # 模型 Profile 值对象（WP-1）
│   ├── AgentTypeRegistration.java
│   ├── EmbodimentLibAPI.java # 门面（WP-10）
│   ├── event/AgentSayEvent.java
│   └── tool/ToolPermissionChecker.java
├── config/                   # WP-1：TOML 加载、校验、路由
├── attach/                   # WP-2：AttachmentTypes、AgentAttachment、AgentRegistry、RegistryEntry、AgentLifecycle(/Plan)
├── runtime/                  # WP-3：EmbodiedAgent（HarnessAgent 包装）、双环、ThreadBridge
├── memory/                   # WP-4：SessionStore、SessionData
├── tool/                     # WP-5/6/7
│   ├── base/    # EmbodiedToolBase、ToolContext、ToolContextScope、Griefing
│   ├── perceive/ loco/ action/ container/ meta/
├── command/                  # WP-8
├── entity/                   # WP-9：DemoAgentEntity、ModEntities
└── util/                     # 通用工具（JSON、日志常量等）
```

### 3.2 工具 ID 与类名

- 工具 ID 为 `域.动词` 小写 snake_case（如 `action.mine_block`），全库唯一（PRD §4.5 表格为准）。
- 每个工具一个类：`tool/<category>/<PascalCase>Tool.java`（如 `action/MineBlockTool.java`）。
- 工具 ID ↔ 类名映射表在 WP-6/WP-7 中逐一给出。

### 3.3 工具输出契约（PRD §4.5 契约）

- 输入：工具自身定义的 JSON 对象。
- 输出：**纯文本 observation 字符串**，回喂给 LLM；成功与失败都用文本表达，**禁止向 agent 循环抛异常**。
- 失败（异常、超时、无有效目标、被权限/griefing 拒绝）→ 统一转为文本 observation。
- 破坏性动作（挖、放、攻击、灭火、拉杆等）执行前必须 post 相应 NeoForge 事件（WP-5 统一封装）。

### 3.4 线程规则（PRD §4.2 / §6.3）

- LLM HTTP 调用：**永远**在游戏线程之外的 IO 池（AgentScope reactive `Mono` 的订阅线程）。
- 工具体：**必须**桥接到本端游戏线程执行——SERVER 端 → 服务器线程（`ServerLevel.getServer().execute(...)`）；CLIENT 端 → 客户端线程（`Minecraft.getInstance().execute(...)`）。
- 工具体内部对世界/实体/容器的读写都发生在游戏线程，天然安全。
- `meta.wait` 以**非阻塞调度延迟**实现，绝不 park 游戏线程（WP-3 实现细节）。
- 任何工具不得持有跨调用长生命周期世界引用缓存。

### 3.5 双端隔离（PRD §4.1 / §6.3，验收硬约束）

- SERVER 端只读 `config/embodimentlib/server.toml`；CLIENT 端只读 `config/embodimentlib/client.toml`。两文件永不互读、永不自动同步。
- SERVER 端 API key 永不进入网络包、永不写入客户端可见 attachment；CLIENT 端 key 永不上送服务器。
- 会话目录：SERVER 端在**服务器世界目录**下 `embodimentlib/sessions/<session-id>/`；CLIENT 端在**客户端本地** `config/embodimentlib/sessions/<session-id>/`。两棵目录树永不合并。
- SERVER 端 agent 需要“出现”给玩家时，只发窄事件（`AgentSayEvent` 等），**绝不**发送 key、profile 名、会话历史、工具调用日志到客户端。

### 3.6 session-id 与 agent-type

- `agent-type`：字符类型名（如 `"village_npc"`），用于路由 profile + 系统提示词（按类型注册）。
- `session-id`：个体身份（0.1 默认 = 实体 UUID 字符串），用于会话/记忆隔离；推荐 1:1 每物理实体。
- 工具永远绑定**执行时所在实体**（`ToolContext`），LLM 不需要传实体；禁止从 session 反推“谁在行动”。

### 3.7 日志

- 统一 logger 名空间 `embodimentlib`；按子域分 logger（`embodimentlib.config`、`embodimentlib.runtime`、`embodimentlib.tool`、`embodimentlib.command`…），包作者可 grep。
- 关键事件（agent 创建/关闭、profile 路由解析、工具调用、griefing 拒绝、会话加载/保存）至少 DEBUG 级。

### 3.8 测试约定

- **验收唯一标准 = `.\gradlew.bat test` 单测全绿（JUnit 5，已配置 `junit-jupiter:5.13.4`）。GameTest 不作为验收依据。**
- 26.1 起**没有 `@GameTest` 注解**，且 `TEST_FUNCTION` 为 simple registry、其 bootstrap（`runLoaders`）早于 mod 构造，**mod 无法注册自定义测试函数**（§8 决策 10 已实测）。因此 GameTest 无法承载真实行为断言，只保留「注册链/生命周期」冒烟价值。
- 所有行为断言一律写成 JUnit 单测：世界内行为（工具、附着、命令、实体）通过**纯逻辑抽取 + 依赖注入/桩对象**使其可在无游戏进程的表单下断言——需要世界交互的部分，把逻辑拆为纯函数（如 `InspectReportBuilder`、路由解析、序列化）后单测，需要实体/世界对象的接口以桩实现替代。
- 现有 `embodimentlib:wiring_smoke`（vanilla `minecraft:always_pass`）**仅作启动冒烟**，不计入验收；`runGameTestServer` 只用于确认 mod 能正常起停。
- **测试中不得发起真实 LLM 调用**：工具单测直接以 `ToolContext` 驱动工具；循环测试用桩模型。WP-3 已提供 `runtime/FakeModel`（脚本化 `Model`：`replyWith` / `callTool` / `failWith`）与 `runtime/RecordingExecutor`（手动 drain 的游戏线程桩）两个测试钩子，后续 WP 直接复用。
- **测试缝约定**：凡是构造需要真实运行时/网络/游戏进程的组件，都把纯逻辑抽成可独立构造的类并以端口注入（WP-2 `AttachmentTarget`、WP-3 `ModelFactory`/`GameThreadExecutor`/`ExecutionGuard`）。薄编排层（如 `EmbodiedAgent.create`）不写单测，改由被抽取的类覆盖。
- 每个 WP 的验收标准含具体测试用例清单。

---

## 4. 工作包详述

> 状态约定：`⬜ 未开始` / `🚧 进行中` / `✅ 已完成`

---

### WP-0 构建基线、JarInJar 打包与元数据

- **状态**：✅ 已完成（2026-09-15）
- **PRD 映射**：§6.1 平台基线、§6.2 嵌入 AgentScope（打包部分）、§6.6 分发
- **目标**：仓库能干净编译 + 打包出带 AgentScope 运行时、元数据正确的 mod jar，并能发布到本地 Maven 仓库。
- **范围（内）**：修复 MODID；补齐模板/资源；引入 `agentscope-harness:2.0.1` 并用 JarInJar 打入 mod；publish 到 `repo/`。
- **范围（外）**：AgentScope 的 API 使用（WP-3/5）；Modrinth/CurseForge 实际上传（仅保留 publishing 配置）。

#### 关键设计

**① 修复主类 MODID（必须）**
`EmbodimentLib.java`：`MODID = "girlfriends"` → `"embodimentlib"`，并与 `gradle.properties` 的 `mod_id=embodimentlib` 一致。

**② 新建缺失文件**
```
src/main/templates/META-INF/neoforge.mods.toml   # 由 generateModMetadata 展开（见 build.gradle）
src/main/resources/pack.mcmeta                   # schema 1-4，pack_format 与 26.1 对应（实现时核对）
src/main/resources/META-INF/accesstransformer.cfg # 如工具需要访问受限方法时再加，0.1 尽量不依赖 AT
```
`neoforge.mods.toml` 模板内容（占位符由 build.gradle 的 `generateModMetadata` 注入）：
```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"
[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="${mod_authors}"
description='''${mod_description}'''
[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="${neo_version_range}"
ordering="NONE"
side="BOTH"
```

**③ 引入 AgentScope 并 JarInJar（PRD §6.2）**
```gradle
// build.gradle dependencies 块追加：
dependencies {
    // 编译 + 运行时都可见（库自身代码使用）
    implementation "io.agentscope:agentscope-harness:2.0.1"
    // 打进 mod jar：下游 addon 与玩家无需单独装 AgentScope
    jarJar("io.agentscope:agentscope-harness:2.0.1") {
        jarJar.ranged(it, "[2.0.1,)")
    }
}
```
⚠️ 若 moddev 2.0.141 的 `jarJar` 配置/`ranged` API 有差异，以 NeoForge 官方依赖文档为准（等价的另一写法是 `implementation(...) { jarJar.ranged(it, "[2.0.1,)") }` 或 `jarJar("io.agentscope:agentscope-harness:2.0.1")` 直接声明）。验证标准：`build/libs/*.jar` 内存在 `META-INF/jarjar/io.agentscope*.jar`。
- ⚠️ 版本核对：PRD 写 `2.0.1`；搜索确认存在 `2.0.0-RC2` 及 `2.0.0` 线。实现时确认 `2.0.1` 在 Maven Central 可用；若不存在则取同线最新稳定版并**在 PRD 决议区记录替换**。`agentscope-harness` 会自动带入 `agentscope-core`，一并打包。

**④ 发布验证**
`.\gradlew.bat publish` 后 `repo/com/hexagram2021/embodimentlib/embodimentlib/...` 出现 pom + jar + sourcesJar（`sourceJar` 任务已配并加入 publication）。坐标：`com.hexagram2021.embodimentlib:embodimentlib:<mod_version>`（group 取 `gradle.properties` 的 `mod_group_id`，与 PRD §6.1 一致；`mod_version=0.1.0+mc26.1.2`）。

#### 验收标准（全部勾选才算完成）
- [x] `MODID` 已改为 `embodimentlib`，主类编译通过
- [x] `src/main/templates/META-INF/neoforge.mods.toml` 与 `src/main/resources/pack.mcmeta` 存在且内容正确
- [x] `.\gradlew.bat build` 成功，产物 jar 内 `META-INF/jarjar/` 含 `agentscope-harness`（及 core）jar
- [x] `.\gradlew.bat publish` 成功，本地 `repo/` 出现完整坐标目录（pom + jar + sources）
- [x] `.\gradlew.bat runClient` 能进主菜单（已人工验证通过；首版另用 `runServer` 冒烟：Mod List 含 Embodiment Lib、JarInJar 2 依赖被发现、`Done` 无 ERROR；后续冒烟统一用 `runGameTestServer`，见 §2）
- [x] `.\gradlew.bat test` 通过（无测试时也须 0 失败）

> 实现备注：`pack.mcmeta` 按 26.1 新 schema 含 `min_format`/`max_format`（pack_format=84）；修复了 publish 的畸形 file URI（改 `uri(...)`）；`settings.gradle` 补 `rootProject.name='embodimentlib'` 使 Maven 坐标为 `com.hexagram2021.embodimentlib:embodimentlib`；`sourceJar` 已加入 publication。首轮发布残留的 `repo/.../Embodiment-Lib/` 旧坐标目录无害，可留待清理。

#### 前置依赖
无（仓库当前可直接构建）。

#### 风险 / 备注
- JarInJar 版本范围语法是唯一不确定点，按官方文档核对。
- `pack_format` 与 MC 26.1.2 对应值实现时核对，错误只会告警不影响加载，但必须修到 0 warning。

---

### WP-1 配置系统：`AgentHostSide` + 双端 TOML + Profile 路由

- **状态**：✅ 已完成（2026-09-15）
- **PRD 映射**：§4.1.1（`AgentHostSide`）、§4.3（配置与路由）、§6.4（配置文件）
- **目标**：每端读取自己的 TOML，能解析出「默认 profile + 按 agent-type 路由」；加载期给出清晰校验；`AgentHostSide` 成为全项目枚举唯一来源。
- **范围（内）**：`AgentHostSide` 枚举；`AgentProfile` 值对象；双端 ModConfigSpec（server.toml / client.toml）；路由解析 `agent_type → profile`；加载期校验与 WARN。
- **范围（外）**：运行时热重载（重启生效）。NeoForge 配置屏由 ModConfigSpec **免费附带**（见 §8 决策 2 更新）。

#### 关键设计

**① `AgentHostSide`（api 包，全项目唯一来源）**
```java
public enum AgentHostSide {
    SERVER, CLIENT;
    public static final String CONFIG_SUBDIR = "embodimentlib";
    public String configFileName() { return name().toLowerCase() + ".toml"; }   // server.toml / client.toml
    public Path configFile(Path configDir) { return configDir.resolve(CONFIG_SUBDIR).resolve(configFileName()); }
}
```
不依赖 FML（Path 重载），纯 JUnit 可测；`sessionRoot` 延后到 WP-4（会话根目录语义随 WP-4 决策）。

**② TOML schema（PRD 开放决策 1，经 26.1 实测修正——见 §8 决策 11）**——`routing` / `profiles` 不使用嵌套表（ModConfigSpec 会清空嵌套表数据），改用可无损往返的列表类型：
```toml
# config/embodimentlib/server.toml   （客户端同名 client.toml，schema 完全一致，互不读取）
# routing / profiles 必须位于根级（[default] 之前），否则会被 TOML 归入 [default] 表
routing = ["village_npc=quest_giver"]     # 每个元素 "agentType=profileName"；缺省或 "default" 回落 [default]

profiles = ['{"name":"quest_giver","protocol":"anthropic","base_url":"https://api.anthropic.com","api_key":"","model_name":"claude-sonnet-4-5"}']

[default]
protocol = "openai"            # "openai" | "anthropic"
base_url = "https://api.openai.com/v1"
api_key = ""                   # 留空视为未配置；SERVER 端 key 永不下发客户端
model_name = "gpt-4o-mini"
```
- `profiles` 每个元素是一个 JSON 对象字符串，字段：`name`（路由引用键，必填）、`protocol`、`base_url`、`api_key`、`model_name`（后四者与 `[default]` 同构）。
- 用 Minecraft 自带的 Gson 解析；非法 JSON / 缺 name / 字段非法 → WARN + 跳过该 profile（回落默认）。

**③ `AgentProfile` 值对象 + 校验**（构造即校验，非法抛 `IllegalArgumentException`）
```java
public record AgentProfile(String protocol, String baseUrl, String apiKey, String modelName) {
    public boolean isOpenAI()    { return PROTOCOL_OPENAI.equals(protocol); }
    public boolean isAnthropic() { return PROTOCOL_ANTHROPIC.equals(protocol); }
    // protocol ∈ {openai, anthropic}；base_url/model_name 非空；api_key 允许为空（本地 endpoint 如 Ollama）
}
```

**④ 配置实现（config 包）——ModConfigSpec（用户指令：参考 GirlfriendsCommonConfig 写法，不用 tomlj；用户裁决保留 ModConfigSpec，routing/profiles 用 List 模拟，见 §8 决策 11）**
- `AgentProfileConfig`：`[default]` 段的 4 字段（push / comment / define / pop，Girlfriends 风格）；非法值由 ModConfigSpec 修正回默认并 WARN。
- `HostConfig`：`[default]` 段 + `routing`/`profiles` 两个 `defineList` 值。
  - `routing`：`defineList("routing", List.of(), e -> e instanceof String)`；`parseRouting` 解析 `"agentType=profileName"`（畸形条目 WARN + 跳过）。
  - `profiles`：`defineList("profiles", List.of(), e -> e instanceof String)`；`parseProfile` 用 Gson 解析 JSON 对象（非法 JSON / 缺 name / 字段非法 → WARN + 跳过）。
  - `resolveProfile`：命中命名 profile → 返回；未命中 / `"default"` / 悬空引用 / 字段非法 → **WARN + 回落默认**（不抛异常，永不返回 null）。
- `EmbodimentConfig`：静态持有 `SERVER_SPEC/SERVER` 与 `CLIENT_SPEC/CLIENT`（各自独立 builder 构建），`forSide(AgentHostSide)` 选择。
- 注册：主类构造器 `modContainer.registerConfig(SERVER, SERVER_SPEC, "embodimentlib/server.toml")` + CLIENT；`ModConfigEvent.Loading` 中调 `validateRouting()` 并 INFO 日志（`embodimentlib.config` logger）。
- 冒烟实测：真实条目（routing 两条 + profiles 两个 JSON）启动后**原样保留**，无 "is not correct" WARN、无 ERROR。

**⑤ 隔离（硬约束）**
- 两个 spec 各自独立实例与文件；SERVER 侧不读 client.toml，CLIENT 侧不读 server.toml；API key 仅存在于做推理的那一端。

**⑥ GameTest 冒烟（26.1 新机制，见 §8 决策 10；**仅冒烟，不计入验收**）**
- `RegisterGameTestsEvent`（Mod 总线）注册 `FunctionGameTestInstance(always_pass, TestData(自注册环境, "minecraft:empty", 120, 0, true))`，测试 id `embodimentlib:wiring_smoke`。
- 26.1 起无 `@GameTest` 注解；TEST_FUNCTION 为 simple registry，其 bootstrap（`runLoaders`）早于 mod 构造，**mod 无法在运行时注册自定义测试函数**——故用 vanilla 内置函数键走通端到端注册/发现/执行链；**配置行为断言全部由 JUnit 单测承担**（§3.8），冒烟日志仅作辅助观察。

#### 验收标准（全部勾选）
- [x] 单测：`default` 缺字段 / `protocol` 非法 / routing 指向不存在的 profile / profile JSON 非法或字段非法 → 解析安全回落默认，无异常泄漏（`AgentProfile` 构造抛带原因异常，`HostConfig` 捕获转 WARN）
- [x] 单测：`routing` 命中命名 profile、未命中回落 default、显式 `"default"` 三种情况路由结果正确
- [x] 单测：畸形 routing 条目（无分隔符 / 空键值 / 空串）被跳过；`parseProfile` 对合法 JSON / 缺 name / 非 JSON / 非法字段的行为正确
- [x] 单测：两个 `HostConfig` 实例（server/client 各解析一份同形文件）互不影响
- [x] 单测：防御性回退——`[default]` 段字段非法时 `defaultProfile()` 捕获异常并回退内置默认 profile，不抛异常
- [x] `AgentHostSide.SERVER.configFile()` 指向 `config/embodimentlib/server.toml`；`CLIENT` 同理（单测断言路径，含 `Locale.ROOT` 区域无关性用例）
- [x] GameTest 冒烟：`runGameTestServer` 下 `embodimentlib:wiring_smoke` 注册、被发现并运行通过（**仅冒烟项**；配置加载的行为断言由单测承担）
- [x] 日志：配置加载成功在 `embodimentlib.config` / 主 logger INFO 可见（"Server config loaded: embodimentlib/server.toml"）
- [x] 冒烟：`runGameTestServer` 退出码 0，`run/config/embodimentlib/server.toml` 自动生成（根级 `routing = []` / `profiles = []` + `[default]`），**写入真实条目后启动原样保留**，无 ERROR/FATAL、无 "is not correct" WARN

#### 前置依赖
WP-0（构建基线）。

#### 风险 / 备注
- 26.1 GameTest 函数注册限制为实测结论（见 §8 决策 10）；**后续 WP 的验收断言一律用 JUnit 单测（§3.8）**，GameTest 仅保留启动冒烟。
- **26.1 ModConfigSpec 嵌套表清空缺陷（见 §8 决策 11）**：routing/profiles 禁用嵌套表与 `Map` 默认值；列表 + JSON 方案已实测无损往返。
- PRD §4.3「配置屏」由 ModConfigSpec 免费获得（§8 决策 2 更新）。

---

### WP-2 实体附着（Attachment）+ 双端 Agent 注册表

- **状态**：✅ 已完成（2026-09-15）
- **PRD 映射**：§4.1.2（server 端附着）、§4.1.3（agent-type vs session-id）、§6.3（注册表）、§6.4（附着存储）
- **目标**：`LivingEntity` 可挂载 brain 元数据（agent-type + session-id）；`(host-side, session-id)` 维度维护运行期注册表，管理 `HarnessAgent` 与 `Toolkit` 生命周期。
- **范围（内）**：两个 `AttachmentType`（`agent_type`、`session_id`，均为字符串）；`AgentRegistry`（SERVER/CLIENT 各一份）；`RegistryEntry`（agent 引用、toolkit、状态、最近调用缓存）；实体加入/移除生命周期钩子。
- **范围（外）**：`HarnessAgent` 具体构建（WP-3）；会话文件（WP-4）；addon 注册 API（WP-10）。

#### 关键设计（实现定稿）

> 以下为实际落码形态，与本节最初的示意代码有 4 处偏差，均已记录理由（见 ①⑤ 与「实现备注」）。

**① Attachment 定义（NeoForge 数据附着，`attach` 包）**
```java
// 实际 API（26.1.2 实测）：附着类型注册表在 NeoForge 侧，而非 vanilla Registries
public static final DeferredRegister<AttachmentType<?>> REGISTER =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EmbodimentLib.MODID);

public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> AGENT_TYPE =
        REGISTER.register("agent_type", () -> AttachmentType.builder(() -> "").build());
public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SESSION_ID =
        REGISTER.register("session_id", () -> AttachmentType.builder(() -> "").build());
```
- ⚠️ **偏差 1（API 修正）**：示意代码写 `Registries.ATTACHMENT_TYPE`，实际 26.1.2 中 vanilla `Registries` **没有**该键；附着类型注册于 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`（`neoforge:attachment_types`），见 `AttachmentType` 类文档与 `NeoForgeRegistries#ATTACHMENT_TYPES`。
- ⚠️ **偏差 2（默认值）**：默认值取**空串**而非 `"embodimentlib:unknown"`。理由：`IAttachmentHolder#getData` 在键缺失时会**把默认值写入实体**，若默认值为非空串，则任何被 `getData` 触碰过的普通实体都会被误判为「已附着的 unknown 类型智能体」；空串则与 `AgentAttachment.isAttached` 的「空白视为未附着」判定天然一致。
- **不持久化、不自动同步**：只调用 `AttachmentType.builder(...)`，既不 `serialize(...)` 也不 `sync(...)`。理由：(a) 环境重载会重建实体，附着的权威来源是 addon 的构造期写入，库若持久化会与 addon 写入形成双写冲突；(b) PRD §4.1.1 / §6.3 要求 agent-type 与 session-id **永不下发客户端**，默认为不同步即天然满足。
- **无副作用读取**：解析统一走 `IAttachmentHolder#getExistingDataOrNull`（经 `AttachmentTarget` 抽象），**禁止**在扫描路径上用 `getData`，否则「扫描一遍世界」会变成「给每个实体都挂上附着」。
- 附着只存**两个短字符串**；重量级 `HarnessAgent` 不存 attachment（PRD §6.4），由注册表持有。
- CLIENT 端不写服务器 attachment（PRD §4.1.1）；库在 CLIENT 端提供注册表即可（WP-10 的 `attachClient` 使用本地注册表）。

**② 运行期注册表 `AgentRegistry`（`attach` 包）**
```java
public final class AgentRegistry {
    private final Map<String, RegistryEntry> bySession = new ConcurrentHashMap<>();

    public static AgentRegistry server();                 // SERVER 单例（集成服务器共用）
    public static AgentRegistry client();                 // CLIENT 单例（仅本玩家）
    public static AgentRegistry forSide(AgentHostSide side);
    static AgentRegistry isolated(AgentHostSide side);    // 仅单测：独立实例，避免全局单例跨用例污染

    public @Nullable RegistryEntry get(@Nullable String sessionId);
    public RegistryEntry register(RegistryEntry entry);   // 重复则关闭旧的（防泄漏）
    public @Nullable RegistryEntry unregister(@Nullable String sessionId);
    public void clear();
    public Collection<RegistryEntry> all();
}
```
- **替换的原子性**：`register` 用 `ConcurrentHashMap#compute` 完成「查重 + 关闭旧句柄 + 写入新条目」。若拆成 `get`/`put` 两步，并发注册会出现两个线程都认为自己替换成功，导致旧句柄被重复关闭或漏关（已有并发单测：8 线程并发注册同一 session，断言恰好 7 次关闭、1 个存活）。
- **跨端注册直接拒绝**：`register` 校验 `entry.side() == this.side()`，不一致抛 `IllegalArgumentException`。隔离约束被破坏时必须立刻失败，而不是静默接受。
- **关闭失败不阻断流程**：句柄 `close()` 抛异常时记录 ERROR 并继续，否则一个坏句柄会让该 session 永远无法被替换或清理，泄漏面反而扩大。
- `RegistryEntry` 字段：`agentType`、`sessionId`、`AgentHostSide`、`EmbodiedAgentHandle`、`Toolkit`（暂为 `Object`，WP-5 合并后收窄）、`AgentState`、`Deque<ToolCallRecord>`（容量 8，新的在前）。
- ⚠️ **偏差 3（抽象）**：`EmbodiedAgent` 抽为接口 `EmbodiedAgentHandle`（`state()` / `recentToolCalls()` / `close()`）。理由：让 WP-2 的注册表生命周期能在**没有 AgentScope 运行时**的前提下被单测完整覆盖；WP-3 的真实类型直接实现该接口，无额外成本。
- ⚠️ **偏差 4（新增）**：`registry/routing` 之外的 `AgentAttachment` + `AttachmentTarget` 抽象：把「读附着 + 空串判定」与 Minecraft 类型解耦，使验收标准第 1 条的断言无需实例化 `Entity`（`Entity` 构造会连锁触发 `EntityType` 注册表查询、`SynchedEntityData` 定义表与事件总线投递，无游戏进程无法实例化）。

**③ 生命周期钩子（服务器端，`AgentLifecycle` + `AgentLifecyclePlan`）**
- **定稿：库不做隐式兜底注册。** 示意代码原写「监听 `EntityJoinLevelEvent`，有 attachment 就确保注册表有条目」。实现时否决：向注册表写入条目必须先构造 `EmbodiedAgentHandle`，而这需要模型 profile、系统提示词、工具集——三者都只有 addon 知道；库代其决定会用 `[default]` 模型**悄悄发起真实计费请求**。故构造智能体是 addon / WP-10 门面的职责，`onJoin(...)` 恒返回 `NONE`（该决策有显式单测覆盖）。
- **监听的事件**：
  - `EntityLeaveLevelEvent`（服务端侧）→ `unregister`（PRD §4.4「卸载的实体 agent 不运行，直接关闭」）；
  - `LivingDeathEvent` → `unregister`。死亡后实体往往要到 chunk 卸载才触发 leave，期间玩家仍可能对话到已死实体；
  - `ServerTickEvent.Post`（每 600 tick）→ 兜底扫描「注册表有条目但世界里已无该 session 实体」的泄漏条目；
  - `ServerStoppedEvent` → `clear()` 整个服务端注册表。
- **关键判定**：卸载路径依据「**注册表里有没有条目**」而非「实体当前是否还带附着」。addon 可能在实体离开前先清掉附着（例如把村民转回普通村民），此时条目仍必须被关闭，否则句柄泄漏。
- **客户端过滤**：所有服务端监听器先判 `!level.isClientSide()`。单人游戏客户端也会收到 leave 事件，不过滤会把服务端 agent 关掉。
- `entity → (agent_type, session_id)` 的解析：`AgentAttachment.getType(entity)` / `getSessionId(entity)` 静态工具；两字符串任一为空/空白视为未附着。
- `AgentLifecycle` 自身只做事件转发，全部**决策**落在纯函数类 `AgentLifecyclePlan`（`onJoin` / `onRemove` / `onSweep`）中被单测覆盖。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [x] 单测：对任意 `LivingEntity` 设/取 `AGENT_TYPE`、`SESSION_ID` 的读写与「空字符串视为未附着」判定正确（以 `AttachmentTarget` 桩实现驱动；含「只写单字段算未附着」「纯空白算未附着」「纯读取不写入附着」用例）——`AgentAttachmentTest` 8 用例
- [x] 单测：注册表 register/get/unregister/重复 register 关闭旧条目（含 `unregister` 幂等空操作、不同 session 互不干扰、`all()` 不可变快照、`clear()` 全关、跨端注册被拒、句柄 close 抛异常不阻断替换、8 线程并发注册恰好关闭 N-1 次）——`AgentRegistryTest` 17 用例
- [x] 单测：实体卸载/移除后注册表条目被清理（以桩事件驱动 `unregister` 路径；含 death 与 sweep 两条清理路径、客户端事件不得关闭服务端句柄、已清附着但条目仍在时仍须注销、服务器停止清空）——`AgentLifecycleTest` 8 用例
- [x] 单测：服务器端 `AgentRegistry.server()` 与客户端 `AgentRegistry.client()` 为不同实例（`!=`），互不共享（含「服务端条目在客户端查不到」「同名 session 双端并存且注销一端不误关另一端」）——`AgentSideIsolationTest` 7 用例
- [x] 单测：附着空实体（无 attachment）时注册兜底不抛异常（含「已附着实体同样不被隐式注册」）——`AgentSideIsolationTest` 覆盖
- [x] 附加：`ToolCallRecord` 强制截断合约（PRD §4.1.1 不得下发超长文本）——`ToolCallRecordTest` 6 用例
- [x] `.\gradlew.bat test` 全绿：**68 用例 0 失败**（WP-2 新增 46 例）
- [x] 冒烟：`runGameTestServer` 正常起停，日志无 ERROR/FATAL；`Embodiment Lib initialized: dual-side configs, attachments and lifecycle hooks registered.` 出现，附着注册表无 `must be registered` 报错

#### 实现备注
- 验收断言的有效性用**变异测试**抽查过：注掉 `register` 里的 `closeQuietly(previous)` → 3 个用例失败；注掉 `onRemove` 的宿主侧过滤 → 1 个用例失败。确认断言非恒真。
- 单测全程不实例化任何 Minecraft 对象（`Entity` / `Level` / `AttachmentType` 实例化都需注册表就绪），世界交互面通过 `AttachmentTarget` 桩 + 纯决策函数 `AgentLifecyclePlan` 隔离；这是 §3.8「行为断言一律 JUnit 化」在 WP-2 的落地方式。
- `AgentRegistry.server()` / `client()` 是全局单例，涉及它们的用例在 `finally` 中恢复注册表，避免跨用例污染。

#### 前置依赖
WP-0（编译）、WP-1（`AgentHostSide`）——均已合并。

#### 风险 / 备注
- ~~NeoForge 26.x 数据附着为 `AttachmentType`；若 API 有变（`Registries.ATTACHMENT_TYPE` 位置），按 26.1.2 实际 API 调整。~~ → **已处置**：确认注册表键在 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`（偏差 1）。
- `AgentState` 与 `ToolCallRecord` 的字段在 WP-3/WP-6 细化，WP-2 定义最小形态（`IDLE`/`REASONING`/`WAITING_TOOL` + `toolName`/`input`/`observation`/`timestamp`），避免返工。
- `RegistryEntry.toolkit` 目前是 `Object`：WP-5 的 `Toolkit` 合并后应收窄为具体类型并补类型断言单测。

---

### WP-3 代理运行时包装（HarnessAgent 包装 + 双环 + 线程桥接）

- **状态**：✅ 已完成（2026-09-16）
- **PRD 映射**：§4.2（包装、双环、线程桥接、失败→observation）、§6.2（AgentScope 用法）、§6.3（线程与隔离）
- **目标**：一个 `EmbodiedAgent` 包装类：按 profile 建模型客户端、按实体绑定 toolkit、支持 **ReAct 逐步环**（P0）与 **批量工具调用环**（P1）、把工具执行桥到游戏线程、把工具失败转成文本 observation。
- **范围（内）**：`EmbodiedAgent`（构建 + `reply()`）；`AgentLoop`（两种循环策略）；`ThreadBridge`（Mono ↔ 游戏线程）；失败/超时/无目标 → observation；测试桩模型。
- **范围（外）**：具体工具（WP-5/6/7）；会话文件（WP-4，但 `EmbodiedAgent` 要能接 session 目录）；addon 触发方式（库不规定何时调用 `reply`）。

#### 关键设计

> **与原文的偏差（实现期裁决，已按实源码核对）**
>
> **偏差 1：`OpenAIChatModel` / `AnthropicChatModel` 不在 core/harness 内，需额外的 extension 模块。**
> 实测 `agentscope-core-2.0.1.jar` 与 `agentscope-harness-2.0.1.jar` 中**不含任何** openai/anthropic 实现类（按类型名检索，0 命中）；官方把它们拆在 `agentscope-extensions-model-openai` / `-anthropic` 两个独立 artifact 中，经 SPI（`io.agentscope.core.model.spi.ModelProvider`）发现。因此 `build.gradle` 已补上这两个依赖并一并 `jarJar`（见 §4 技术基线）。**这是原文 `new OpenAIChatModel(baseUrl, apiKey, modelName)` 写法无法编译的根本原因**——两者都只有 builder，没有该三参构造器。
>
> **偏差 2：`HarnessAgent.Builder` 的方法名与原文不同。** 实源码中为 `sysPrompt(String)`（非 `systemPrompt`）与 `toolkit(Toolkit)`（非 `tools`）；`maxIters(int)` 而非 `maxIterations`。
>
> **偏差 3：`EmbodiedAgent` 实现 `EmbodiedAgentHandle` 接口而非 `AutoCloseable`。** 沿用 WP-2 §8 决策 14 的同一测试缝：接口让注册表生命周期可在无 AgentScope 运行时下单测。`HarnessAgent.close()` 确实存在（`HarnessAgent implements Agent, AutoCloseable`），故 `close()` 内部转发给它。
>
> **偏差 4：`dispatch` 的返回语义由「void」改为带返回值的等待。** 原文 `ThreadBridge.dispatch(side, Runnable)` 无法把工具结果送回 IO 线程。实现为 `ThreadBridge.call(executor, task, timeout) → T`：派发**不含等待**，等待发生在 IO 线程侧（`CompletableFuture#get(timeout)`），游戏线程零 park。同时抽出 `GameThreadExecutor` 端口以支持线程断言。
>
> **偏差 5：`ThreadLocal` 工具上下文见 WP-5。** 本 WP 只提供 `ToolBridge` 把「工具体」桥到游戏线程并把失败转文本；`ToolContextScope.runWith` 的包裹点在 WP-5 的 `EmbodiedToolBase` 调用处。**WP-5 已落码**：`EmbodiedToolBase.executeOnGameThread(ToolBridge, ToolContext, Map)` 正是该包裹点，WP-3 的 `ToolBridge` 无需改动。
>
> **偏差 6：`meta.wait` 的 resumer 由 WP-6 的工具体实现，不在本 WP。** 本 WP 只保证桥接本身零 park（已单测断言），`meta.wait` 的 N-tick 恢复登记属于工具体职责。

**① 构建（PRD §6.2；模型客户端按 protocol 选择）**

```java
// runtime/EmbodiedAgent.java（已落码实际形态）
public final class EmbodiedAgent implements EmbodiedAgentHandle {
	private final AgentHostSide side;
	private final String agentType;
	private final String sessionId;
	private final HarnessAgent delegate;
	private final AgentLoop loop;
	private final ExecutionGuard guard = new ExecutionGuard();

	public static EmbodiedAgent create(AgentHostSide side, String agentType, String sessionId,
			AgentProfile profile, String systemPrompt, @Nullable Toolkit toolkit,
			@Nullable Path sessionDir, AgentLoop loop, ModelFactory modelFactory) {
		Model model = modelFactory.create(profile);              // 端口注入：单测用桩模型
		HarnessAgent.Builder builder = HarnessAgent.builder()
			.name("embodimentlib-" + agentType)
			.model(model)
			.sysPrompt(systemPrompt == null ? "" : systemPrompt)
			.maxIters(loop.delegateMaxIters());
		if (toolkit != null) { builder.toolkit(toolkit); }
		if (sessionDir != null) { builder.workspace(sessionDir); }
		// 关闭与 Minecraft 场景无关的 harness 能力（文件系统/Shell/子 agent/技能/记忆工具）
		builder.disableSubagents().disableDynamicSubagents().disableFilesystemTools()
			.disableShellTool().skillFilter(SkillFilter.none()).disableMemoryTools();
		return new EmbodiedAgent(side, agentType, sessionId, builder.build(), loop);
	}

	public Mono<String> reply(String userText) { /* 见② */ }
	@Override public void close() { delegate.close(); }   // 幂等
}
```

- **`ModelFactory` 是测试缝**：`ModelFactory.defaultFactory()` 按 `profile.isOpenAI()` 分派到
  `OpenAIChatModel.builder().apiKey().baseUrl().modelName().stream(false).build()`，
  否则 `AnthropicChatModel.builder()` 同形。二者 `build()` 都不校验 apiKey——
  密钥存在性由 WP-1 配置层负责（PRD §4.3）。
- **统一 `stream(false)`**：0.1 的 `reply()` 只消费最终答案文本，无需流式增量。

**② 循环与线程桥接（本 WP 核心）**

`reply(text)` 返回 `Mono<String>`。实现要点（均已落码并被单测覆盖）：

1. 循环整体在 IO 线程（AgentScope 的 reactive 调度）执行；**游戏线程绝不 park**。
2. `ThreadBridge.call(executor, task, timeout)`：
   - 创建 `CompletableFuture`，把「执行 task 并 complete」**派发**到游戏线程后**立即返回**；
   - **IO 线程**在 future 上等待（带超时），此时游戏线程空闲；
   - 超时 → 返回 `null` → `ToolBridge` 转 observation `"tool timeout after Xms"`。
3. 工具抛异常 / 返回空 / 无有效目标 → `ToolBridge.execute(...)` 统一转文本 observation，**永不抛异常**。
   异常 → `"tool error: <msg>"`；空/空白 → `"tool returned no result"`；超时 → `"tool timeout after Xms"`。
   - **null 的二义性已消除**：工具的 `null` 返回值与「超时」原本都是 `null`，会误报成超时；
     实现用引用比较的哨兵常量区分（见 `ToolBridge.NULL_RESULT_SENTINEL`），并有单测锁定。
4. `ThreadBridge` / `GameThreadExecutor`（runtime 包）：把「SERVER → 服务器线程 / CLIENT → 客户端线程」
   抽成端口，`executorFor(side, server, client)` 是唯一映射点，避免各处 `switch` 写错方向。
5. **ReAct 逐步环（P0）**：默认 `Mode.REACT_STEP`、`maxIterations = 12`。逐步环下模型若越权返回多个
   工具调用，`AgentLoop.selectInvocations` 只取第一个（否则两种模式就失去区分）。
6. **批量工具调用环（P1）**：`Mode.BATCH_TOOLS`，`selectInvocations` 保留全部；
   `delegateMaxIters()` 给一倍余量，避免预算耗尽导致「工具调用后、观测回喂前」被截断。
7. `meta.wait` 特例：见「偏差 6」，resumer 属 WP-6 工具体；本 WP 已用单测证明桥接零 park。

**③ 状态与检查（供 WP-8）**
`EmbodiedAgent.state()` 返回 `AgentState`（IDLE/REASONING/WAITING_TOOL）；`recentToolCalls()` 返回最近 8 条
`ToolCallRecord`；`conversationPreview(maxChars)` 截断会话预览（**不含 api_key**）。
- **`ExecutionGuard` 单次执行守卫**：忙碌时**拒绝而非排队**（PLAN WP-9 ③ 的 `"agent busy"` 语义）。
  用 `AtomicReference#compareAndSet` 而非 `ReentrantLock#tryLock()`——后者对**同一线程**可重入，
  会把「同线程连续两次 reply」误判为空闲，而那恰是最常见场景（命令处理器在 tick 线程连续触发）。
  用 CAS 还使 `exit()` 可在任意线程调用（Reactor 的 `doFinally` 不保证与订阅同线程）。

**④ 测试桩 `FakeModel` 与 `GameThreadExecutor` 记录桩**
- `runtime/FakeModel`：脚本化 `Model`（`replyWith` / `callTool` / `failWith`），记录每轮收到的消息数，
  用于断言「观测确实被回喂」；测试中**绝不**真调 LLM。
- `runtime/RecordingExecutor`：`synchronous()` 立即执行 / `manual()` 手动 `drain()`，
  后者用于断言「派发不含等待」（drain 前任务仍在队列、IO 线程已在等）。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [x] 单测（FakeModel 桩就绪）：`FakeModel` 按脚本走通「工具调用→观察→最终答案」的响应序列，并可断言每轮回喂的消息数
- [x] 单测：工具抛异常 / 超时 / 返回空 → 循环不崩，observation 文本回喂（断言结果文本）
- [x] 单测（P1）：批量工具调用环一次处理多个工具调用
- [x] 单测：SERVER 模式下工具体被投递到「服务器线程执行器」——以桩执行器记录调用线程/调度目标，断言桥接方向正确（不启动真实服务器）
- [x] 单测：`meta.wait` 场景下不阻塞调用线程（埋点断言派发立即返回、任务仍留在队列），证明零 park
- [x] 单测：`close()` 后注册表条目被释放；同一 agent 并发 reply 被拒绝而非排队

> **验收说明（②的实现边界）**：`EmbodiedAgent.create(...)` 会真实构造 `HarnessAgent`（创建 HTTP 传输层），
> 不满足「单测不联网」约束，故**未**在单测中实例化它。改为把其中全部纯逻辑抽成可独立构造的类
> （`ExecutionGuard` / `AgentLoop` / `ThreadBridge` / `ToolBridge` / `ModelFactory`）并逐一覆盖，
> `EmbodiedAgent` 只保留薄编排层。因此验收 ①（真实 ReAct 环走通）由 `FakeModel` 的脚本能力 +
> `AgentLoop` 的调度语义共同保证；**真实模型往返留待 WP-9 端到端**（P2，需真实 key）。
> 同理 `close()` 释放注册表项以 `AgentRegistryRuntimeIntegrationTest` 的桩句柄验证。

#### 前置依赖
WP-0、WP-1（`AgentProfile`）、WP-2（`AgentRegistry`/`EmbodiedAgentHandle`）✅；WP-5（`Toolkit`/`ToolContextScope`）✅。
本 WP 通过 `@Nullable Toolkit` 参数与 `ToolBridge` 端口解耦，**经确认无需改动本 WP 代码**：
`ToolContextScope.runWith` 的包裹点落在 WP-5 的 `EmbodiedToolBase.executeOnGameThread` 内，
WP-3 只负责「把工具体桥到游戏线程并把失败转文本」。addon 侧传入真实 `Toolkit` 即可（WP-9/10 接线）。
✅ 2.0.1 API 已按 `../Sources-26.1.2/` 实源码核对（**未解包 jar**）：`HarnessAgent.builder()` 方法名、
`OpenAIChatModel/AnthropicChatModel` builder、`Toolkit` 形态、`ToolBase` 签名、`Mono<ToolResultBlock>` 形态、
`RuntimeContext.builder().sessionId(...)`。

#### 风险 / 备注
- **workspace 是否按 session-id 隔离**：`HarnessAgent` 有 `workspace(Path)` 与 `defaultSessionId(String)` 两个
  独立入口，配合 `stateStore(AgentStateStore)`（默认 `JsonFileAgentStateStore`）。本 WP 传 `sessionDir`，
  由调用方（WP-4）决定按 session 分目录。**若 WP-4 发现不隔离，回退方案**：库自管 `history.json`，
  在 reply 前后注入/提取上下文——该回退点保持在 WP-4，本 WP 的接口不变。
- 线程桥接是并发正确性核心，验收必须含线程断言（已含：方向、零 park、拒绝而非排队、非重入、跨线程释放）。
- 三个关键不变量已做**变异测试**验证断言非空转：守卫 CAS 恒真 → 4 个测试失败；`ToolBridge` 去掉 null 哨兵
  → 1 个失败；`AgentLoop` 忽略逐步约束 → 1 个失败。测试对时间敏感处已去除竞态断言（`await` 后不再断言 `isAlive`），
  连续 3 次 `--rerun-tasks` 全绿。

---

### WP-4 会话与记忆持久化（session-id 维度）

- **状态**：⬜
- **PRD 映射**：§4.4（会话记忆）、§6.4（会话文件位置）
- **目标**：按 `(host-side, session-id)` 持久化会话历史、工作记忆、待办清单；目录隔离；生命周期与实体卸载同步。
- **范围（内）**：`SessionStore`（按 side+session-id 定位目录）；`SessionData`（working memory、todo、历史访问）；加载/保存钩子。
- **范围（外）**：向量库/长期记忆摘要（PRD 明确 0.1 不做）；跨实体共享会话（PRD §4.1.3 明确 addon 层职责）。

#### 关键设计

**① 目录（PRD §6.4 硬约束）**
```
SERVER: <world dir>/embodimentlib/sessions/<session-id>/
CLIENT: <config dir>/embodimentlib/sessions/<session-id>/
```
- 世界根：`ServerLevel.getServer().getWorldPath(LevelResource.ROOT)`。
- 客户端根：`FMLPaths.CONFIGDIR.get()`。
- 两棵树**永不合并**（各 `SessionStore` 只认自己的 side 根）。

**② `SessionData` 与文件**
```java
public final class SessionData {
    private final Map<String, String> workingMemory = new LinkedHashMap<>(); // memory.json
    private final List<String> todo = new ArrayList<>();                     // todo.json
    // 会话历史：0.1 委托 HarnessAgent workspace（WP-3 决策）；若回退，则存 history.json
}
```
- 序列化：Gson（MC 自带）。`memory.json`：`{"key": "value"}`；`todo.json`：`["item1", ...]`。
- 历史：优先走 AgentScope workspace 持久化（构建时把 `workspace` 指向该 session 目录）；`SessionStore` 提供 `historyPath()` 供 WP-3 使用。

**③ `SessionStore`**
```java
public final class SessionStore {
    public static SessionStore forSide(AgentHostSide side);  // 单例
    public SessionData load(String sessionId);   // 目录不存在 → 空 SessionData（不报错）
    public void save(String sessionId, SessionData data);
    public void delete(String sessionId);        // 用于注册表 unregister 清理（可选）
    public Path sessionDir(String sessionId);
}
```
- 保存时机：`SessionData` 变更后立即落盘（写穿）+ 服务器 `ServerLifecycleHooks` 保存事件兜底 flush；客户端在会话关闭/退出时 flush。
- session-id 非法字符（`../` 等路径穿越）必须清洗/拒绝——**安全要求**。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [ ] 单测：`load` 不存在目录返回空数据；`save` 后目录/文件结构正确（含路径穿越用例：`sessionId = "../evil"` 被拒绝）
- [ ] 单测：SERVER 根与 CLIENT 根分离（`forSide(SERVER).sessionDir(id)` 与 `forSide(CLIENT).sessionDir(id)` 不同且互不包含）
- [ ] 单测：`unregister` 路径触发 `save`（以桩/临时目录断言文件存在）；重新 `load` 内容一致
- [ ] 单测：两实体同 agent-type 不同 session-id → 数据互不可见（PRD §4.4）

#### 前置依赖
WP-1（side 判定）、WP-2（session-id 来源）、WP-3（历史委托接口）。

#### 风险 / 备注
- 与 WP-3 的 workspace 委托联动：若 AgentScope 自管历史，则 `memory.json/todo.json` 由库自管，二者目录同级不冲突。

---

### WP-5 工具契约与基础设施（含权限钩子、Griefing 集成）

- **状态**：✅ 已完成（2026-09-18）
- **PRD 映射**：§4.5（契约：JSON 输入 / 文本输出 / 失败转观察）、§4.6（权限钩子，P1）、§4.9（Griefing）、§6.2（ToolBase/Toolkit）
- **目标**：定义所有内置工具共享的基类、上下文与执行管线；统一 post 破坏性事件；暴露权限否决钩子。
- **范围（内）**：`EmbodiedToolBase`、`ToolContext`、`ToolContextScope`（实体绑定机制）、`Griefing` 助手、`Toolkit` 组装工厂、`ToolPermissionChecker`（P1 验收）。
- **范围（外）**：具体工具（WP-6/7）；addon 注册 API（WP-10，但基类属公开 API，需可被子类化）。

#### 实现记录（与下方原设计的偏差）

实际落码为 **8 个类**（原设计 5 个），新增 `ToolResults`，并把 `ToolContextScope` 提为顶层类：

| 类 | 职责 |
|---|---|
| `tool/ToolContext` | record：`(AgentHostSide side, LivingEntity entity, String agentType, String sessionId)` |
| `tool/ToolContextScope` | `ThreadLocal` 作用域：`get`/`getOrNull`/`isActive`/`runWith`（可嵌套） |
| `tool/EmbodiedToolBase` | 继承 AgentScope `ToolBase`；`callAsync` → `executeSafely` → `run` |
| `tool/ToolResults` | **新增**：参数解析与 observation 规约的纯逻辑层（无 Minecraft 依赖） |
| `tool/Griefing` | 破坏性动作判定，复用 `EventHooks.canEntityGrief` |
| `tool/BuiltinToolkit` | 23 工具目录 + 装配/裁剪/去重/覆盖度校验 |
| `tool/ToolPermissionChecker` | `@FunctionalInterface`：否决钩子 + `and` 组合 + `guarded` |
| `tool/package-info.java` | 包级注解与不变式说明 |

**偏差 1：`ToolContext` 字段与原设计不同。**
原设计 `(side, entity, AgentTypeRegistration type)`；实际为
`(side, entity, String agentType, String sessionId)`。理由：`AgentTypeRegistration` 是 WP-1 配置层的类型，
而工具上下文需要的是「身份字符串」（用于审计记录回填），持有注册对象会把配置层耦合进工具层；
`sessionId` 则是 `ToolCallRecord` 审计所必需。

**偏差 2：`ToolContextScope` 提为顶层类而非 `ToolContext.Scope` 内部类。**
理由：作用域是**线程语义**的载体，与 record 的数据语义正交；独立成类后可单独撰写
线程隔离/清理语义的 Javadoc，并避免 `ToolContext`（纯数据）被迫承载静态可变状态。

**偏差 3：新增 `ToolResults` 纯逻辑层（重要）。**
原因见下节「测试边界的实测结论」。所有「参数解析」「observation 规约」「schema 构造」
从基类下沉到无 Minecraft 依赖的 `ToolResults`，基类只留转发。

**偏差 4：`Griefing` 复用 `EventHooks.canEntityGrief`，不再自行 post 事件。**
原设计示意 `new EntityMobGriefingEvent(entity, pos)` + `post(...).isCanceled()` + `canGrief()`。
核对 26.1.2 源码后确认该写法**不成立**：
- 构造器为 `EntityMobGriefingEvent(ServerLevel level, Entity entity)`——**无 BlockPos 参数**；
- `EntityEvent extends Event`，**非** `ICancellableEvent`，故不存在 `isCanceled()`；
- 判定唯一依据是 `canGrief()`，且构造时已纳入 `GameRules.MOB_GRIEFING` 初值。

NeoForge 已提供规范入口 `EventHooks.canEntityGrief(ServerLevel, Entity)`，其实现正是
`post(new EntityMobGriefingEvent(level, entity)).canGrief()`。**直接复用它**，
好处是 NeoForge 若调整该事件语义，本库自动跟随。详见 §8 决策 17。

**偏差 5：`Griefing.denied` 对非 `ServerLevel` 一律返回 `true`（保守拒绝）。**
原设计未涉及 CLIENT 侧。该事件需要 `ServerLevel` 才能读游戏规则，而客户端本就不应产生
权威世界变更（PRD §4.1.1），故保守拒绝比放行安全——宁可让工具回一句 denied，
也不能让客户端改了本地世界造成双端不一致。

**偏差 6：`BuiltinToolkit.create()` 当前返回空 Toolkit。**
这是**刻意的中间状态**而非遗漏：23 个工具由 WP-6/WP-7 实现，若此处硬编码引用了尚不存在的类，
WP-5 无法编译。真正交付的是**装配契约**：`BUILTIN_TOOL_IDS`（23 个 ID 的唯一权威清单）、
`register`（重名拦截）、`without`（未知 ID 响亮失败）、`validateCoverage`（缺/多/重三向校验）。
WP-6/WP-7 只需补注册循环。

**偏差 9：P0/P1 口径不进入公开 API。**
原设计有 `P0_TOOL_IDS` / `P1_TOOL_IDS` 两个 public 字段，把开发分期（P0=WP-6、P1=WP-7）
暴露给了 addon——但这是**内部口径**，其它模组感知的应当只有「有没有这个工具、功能如何」。
已删除这两个字段，目录退化为单一 `BUILTIN_TOOL_IDS`；P0/P1 的分期仅保留在 PRD §4.10 / WP-6 的
自检记录与 PLAN，由 WP-6 交付时用 `validateCoverage` 对全目录核对，不作为公开 API 字段。

**偏差 7：`ToolPermissionChecker.canExecute` 更名为 `check`，参数去掉了 tool 对象。**
原设计签名 `(ToolContext, EmbodiedToolBase, JsonObject)`；实际为
`(ToolContext, String toolName, Map<String,Object>)`。理由：把工具对象传给检查器会诱导实现
去调用工具方法（越权），而检查器只需要「是谁、要做什么、参数是什么」；
用 `String` 工具名而非工具实例，也让检查器可以在工具尚未实例化时工作。

**偏差 8：空值与 `@NullMarked` 卫生。**
包级采用 `@NullMarked`（默认非空），故去掉所有对 `@NullMarked` 非空参数的多余 `requireNonNull`
与 `entity == null` 式防御（`Griefing.denied`、`EmbodiedToolBase` 构造器、
`ToolContext` 紧凑构造器、`ToolContextScope.runWith`、`BuiltinToolkit`、`ToolResults`、
`ToolPermissionChecker`）。**真正可空的边界才标 `@Nullable`**：`ToolResults.normalizeInput/normalizeObservation`
（工具返回值可为 null）、`requiredParam` 的返回值、`asMob()` 返回值、`BuiltinToolkit.toolNames(@Nullable Toolkit)`
（WP-2 的 `RegistryEntry.toolkit` 收窄前确实可空）、`ToolPermissionChecker.guarded` 的 checker。
相应删除 6 个「断言多余空值防御抛 NPE」的测试（它们测的是防御而非契约）——
空参数在 `@NullMarked` 下是调用方的编译期错误，由 null-safe 工具静态拦截，不属运行时契约。

#### 测试边界的实测结论（为何需要 `ToolResults`）

**实测（探针用例）**：纯 JUnit 环境下
- `net.minecraft.world.entity.LivingEntity` **可加载**；
- 但 `net.minecraft.world.entity.animal.Pig` **不在测试编译类路径上**，
  且 `LivingEntity(EntityType, Level)` 构造需要 `EntityType` 注册表与世界对象。

结论：**无法在单测中构造 `LivingEntity`**，因此 `ToolContext` 无法被实例化。
这把 WP-5 的可测范围切成了两半：

| 可单测（已覆盖 51 例） | 需真实游戏环境（推迟到 WP-9/10） |
|---|---|
| 参数解析、observation 规约、schema 构造（`ToolResults`） | `ToolContext` 的实体访问器（`isEntityUsable`/`level`/`asMob`） |
| 作用域未激活时的行为、线程隔离、参数校验（`ToolContextScope`） | 作用域内取值与还原（需真实 ctx） |
| 权限组合/短路/黑名单/参数校验（`ToolPermissionChecker`） | `guarded` 的放行/否决分支（要求非空 ctx） |
| 工具目录、P0/P1 切分、去重、裁剪、覆盖度校验（`BuiltinToolkit`） | `Griefing` 的实际事件判定（需 `ServerLevel`） |
| `ToolBase` 子类化与注册（`registerAgentTool` 真实调用） | 工具体的世界操作（WP-6/7） |

**这是本 WP 的已知验收缺口**，已在 §4 WP-6/7 的验收项中排入真实游戏环境验证。

#### 关键设计（原设计，已按上述偏差更新）

**① `EmbodiedToolBase`（tool 包）**
```java
public abstract class EmbodiedToolBase extends ToolBase {
    /** 子类实现：对绑定实体执行；返回 observation 文本 */
    public abstract String run(ToolContext ctx, Map<String, Object> input) throws Exception;

    /** 库内部契约：工具体在游戏线程执行，从这里取绑定实体 */
    protected final ToolContext ctx() { return ToolContextScope.get(); }

    /** 供单测/接线直接驱动；一切失败转文本 */
    public String executeSafely(@Nullable Map<String, Object> input) { ... }
}
```
- 失败契约：`executeSafely` 统一兜底 catch → `ThreadBridge.errorObservation`；
  `null`/空白返回 → `ToolResults.EMPTY_OBSERVATION`；实体不可用 → `"entity unavailable"`。
- `concurrencySafe(false)` 固定传入：Minecraft 世界操作必须在游戏线程串行，
  让 AgentScope 不把同轮多个调用并行派发。

**② `ToolContext` + `ToolContextScope`（实体绑定机制，PRD §4.1.3）**
- 机制：接线侧在把工具体排到游戏线程时 `ToolContextScope.runWith(ctx, () -> tool.run(...))`；
  工具内同步取 `ctx()`。
- **嵌套安全**：`runWith` 保存旧值并在 finally 还原（而非无脑 remove），
  使「工具内部再触发工具执行」不会丢失外层绑定。
- **线程安全**：多 agent 在同一游戏线程串行执行，ThreadLocal 语义成立；
  且刻意**不继承**到子线程（避免 IO 线程误读已失效的游戏线程绑定）。
- `entity` 已死亡/卸载 → 工具返回 `"entity unavailable"`。

**③ `Griefing` 助手（PRD §4.9，所有破坏性工具统一走这里）**
```java
public final class Griefing {
    public static final String DENIED = "griefing denied";

    public static boolean denied(LivingEntity entity, Level level) {
        if (entity == null || level == null) return true;
        if (!(level instanceof ServerLevel serverLevel)) return true;   // 保守拒绝
        if (!entity.isAlive()) return true;
        return !EventHooks.canEntityGrief(serverLevel, entity);          // 复用 NeoForge 规范入口
    }

    public static boolean denied(ToolContext ctx) { ... }
    public static boolean allowed(ToolContext ctx) { ... }
}
```

**④ `BuiltinToolkit` 组装工厂（tool 包）**
```java
public final class BuiltinToolkit {
    public static final List<String> BUILTIN_TOOL_IDS;   // 23 个，唯一权威清单

    public static Toolkit create() { ... }               // 当前为空；WP-6/7 补注册
    public static Toolkit without(String... ids) { ... } // 未知 ID 抛 IAE
    public static void register(Toolkit, AgentTool) { ... }        // 重名抛 IAE
    public static void registerAll(Toolkit, Iterable) { ... }
    public static List<String> validateCoverage(Iterable, List<String>) { ... }  // 缺/多/重
    public static List<String> toolNames(@Nullable Toolkit) { ... }
}
```

**⑤ 权限钩子 `ToolPermissionChecker`（P1）**
```java
@FunctionalInterface
public interface ToolPermissionChecker {
    String DENIED = "permission denied";

    /** 返回 false 则本次调用被否决，工具输出 "permission denied" */
    boolean check(ToolContext ctx, String toolName, Map<String, Object> input);

    default ToolPermissionChecker and(ToolPermissionChecker other) { ... }  // 短路组合
    static ToolPermissionChecker allowAll() { ... }
    static ToolPermissionChecker denyTools(String... names) { ... }
    static String guarded(@Nullable ToolPermissionChecker, ToolContext, String, Map, Supplier<String>) { ... }
}
```
- **默认必须是放行**：若设计成「只有显式允许才能执行」，addon 忘记注册检查器就会让所有工具静默失效。
- 注册点：`EmbodimentLibAPI.setGlobalPermissionChecker(...)`（WP-10 暴露）；接线侧在工具执行前调用。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [x] 单测：`ToolContextScope.runWith` 内可取值、外取值抛 IllegalStateException
      —— **部分达成**：作用域外的行为（抛异常/`getOrNull` 返回 null/`isActive` 为 false）、
      线程隔离、并发隔离、参数校验均有覆盖；**「内可取值」需真实 `ToolContext`，见上方测试边界缺口**
- [x] 单测：`Griefing.denied` 被拒/放行时行为正确，工具输出 `"griefing denied"`
      —— **部分达成**：`DENIED` 文案与保守拒绝分支已定；**实际事件判定需 `ServerLevel`，排入 WP-6**
- [x] 单测：实体死亡/卸载后工具返回 `"entity unavailable"`
      —— **部分达成**：常量与判定分支已实现并有文案断言；**需真实实体，排入 WP-6**
- [x] 单测（P1）：`ToolPermissionChecker` 返回 false → 输出 `"permission denied"` 且工具体未被调用
      —— **部分达成**：组合/短路/黑名单已覆盖；**`guarded` 的放行/否决分支需非空 ctx，排入 WP-9**
- [x] 单测：`BuiltinToolkit.create()` 注册数 == 当前已实现工具数（含 WP-6/WP-7 完成后 23）
      —— **达成**：`create()` 当前为 0（符合当前实现数），且目录/切分/校验机制全覆盖；
      WP-6/7 完成后只需把断言值改为 12 / 23

**验证命令**
```
.\gradlew.bat test --tests "com.hexagram2021.embodimentlib.tool.*"
```
**实测结果**：`BUILD SUCCESSFUL`，**43 例 0 失败**
（`ToolResultsTest` 17、`BuiltinToolkitTest` 13、`ToolContextScopeTest` 7、`ToolPermissionCheckerTest` 6）。
全量 `.\gradlew.bat test` 为 **162 例 0 失败**（WP-0..WP-3 的 119 例 + WP-5 的 43 例）。

**变异测试（证明断言非空转）**：注入 3 个变异，全部被捕获——
1. `ToolResults.normalizeObservation` 的空白分支改为返回 `""`（破坏「观测永不为空」）→ **2 例失败**；
2. `ToolResults.booleanParam` 改用 `Boolean.parseBoolean`（把 `"yes"` 静默变成 `false`）→ **1 例失败**；
3. `BuiltinToolkit.register` 去掉重名拦截 → **1 例失败**。
全部还原并复验全绿。

#### 前置依赖
WP-0（AgentScope ToolBase/Toolkit）、WP-2（实体上下文）。**均已完成**。
WP-3 的 `ThreadBridge`/`ToolBridge` 已提供 `errorObservation` 与桥接执行入口，本 WP 直接复用。

#### 风险 / 备注
- `ToolBase` 继承 + `registerAgentTool` 已用真实 `Toolkit` 实例验证（`BuiltinToolkitTest` 中的哑工具），
  API 形状确认无误。
- **已知缺口**：`ToolContext` 无法在单测中构造（见「测试边界的实测结论」），
  故实体相关分支的验证推迟到 WP-6（真实游戏环境）与 WP-9（端到端）。
  这是本 WP 唯一未完全闭合的验收项，不接受「用 mock 糊过去」的做法。
- `ToolBase.concurrencySafe(false)` 是一个**保守但重要**的选择：若未来确认某些只读工具
  可在游戏线程并发，可逐个放开；但默认不放开，因为误判会导致世界状态竞争。


---

### WP-6 P0 内置工具（12 个）

- **状态**：⬜
- **PRD 映射**：§4.5 #1,2,3,6,7,9,10,11,13,16,22,23；§5 P0
- **目标**：交付「听→走→挖→答」闭环所需的 12 个原子工具，全部符合 WP-5 契约。
- **范围（内）**：下列 12 个工具类 + 各自 JSON schema + observation 文本规范 + **单测**。
- **范围（外）**：P1 工具（WP-7）；任务级组合工具（PRD 明确不做）。

#### 工具清单与契约

| # | ID | 类名（tool/...） | 输入 schema | 输出（observation） |
|---|---|---|---|---|
| 1 | `perceive.nearest_block` | `perceive/NearestBlockTool` | `{"block":"minecraft:iron_ore","radius":8}` | `"iron_ore at (x,y,z), distance 5.2"` / `"not found"`；radius 非法 → `"invalid radius"` |
| 2 | `perceive.block_state_at` | `perceive/BlockStateAtTool` | `{"pos":[x,y,z]}` | 序列化状态串 `"minecraft:oak_door[half=lower,facing=east]"`；空中 `"air"`；世界外 `"void"` |
| 3 | `perceive.inventory_contents` | `perceive/InventoryContentsTool` | `{}` | 逐槽摘要 `"slot 0: 64x minecraft:cobblestone"`；无库存实体 → `"inventory not supported on this entity"` |
| 6 | `perceive.self_status` | `perceive/SelfStatusTool` | `{}` | 一行快照：`"pos=(x,y,z) dim=minecraft:overworld health=18.0 held=minecraft:iron_pickaxe time=6000 threats=2 nearby"` |
| 7 | `loco.move_to` | `loco/MoveToTool` | `{"x":..,"y":..,"z":..,"reach":2}` | `"arrived"` / `"path blocked"` / `"distance 3.5 remaining (timeout)"`；非 `Mob` → `"pathfinding not supported"` |
| 9 | `loco.jump` | `loco/JumpTool` | `{}` | `"jumped"` |
| 10 | `loco.look_at` | `loco/LookAtTool` | `{"pos":[x,y,z]}` 或 `{"entity_id":"uuid"}` | `"looking"` |
| 11 | `action.mine_block` | `action/MineBlockTool` | `{"pos":[x,y,z]}` | `"mined"` / `"block unbreakable"` / `"no tool"` / `Griefing.DENIED` |
| 13 | `action.use_item` | `action/UseItemTool` | `{"hand":"main"}` | 使用结果文本（消耗/效果/无） |
| 16 | `action.attack_entity` | `action/AttackEntityTool` | `{"entity_id":"uuid"}` | `"attacked"` / `"out of reach"` / `"target invalid"`；受害者在攻击范围内时 post 事件（WP-5） |
| 22 | `meta.wait` | `meta/WaitTool` | `{"ticks":20}` | `"waited"`（非阻塞调度，WP-3 机制） |
| 23 | `meta.say` | `meta/SayTool` | `{"text":"hello"}` | `"said"`，并 fire `AgentSayEvent(agentId, entity, text)` |

#### 关键实现要点

- **#1/#2/#5** 世界查询：`entity.level().getBlockState(pos)`、`BlockPos.betweenClosed` 扫描 + `Distance`；`#1` 用 `ResourceLocation` 解析 block（非法 → `"invalid block id"`）。
- **#3/#4** 库存：`entity instanceof Container`/`Inventory`（如 `Player`、`AbstractChestedMob`）→ 枚举槽；否则按契约返回 `"inventory not supported..."`。
- **#7/#8** 寻路：`entity instanceof Mob m` → `m.getNavigation().moveTo(x,y,z,speed)`；`reach` 判断用 `entity.distanceToSqr`；启动后返回，不阻塞等待到达（到达检测由工具执行时点快照 + 可重复调用收敛）；超时由 WP-3 的执行超时兜底。
- **#11** 挖掘：先 `Griefing.denied` → `"griefing denied"`；再 `level.destroyBlock(pos, true)`（掉落物）；`block unbreakable` 判定 `block.getDestroySpeed(level,pos) < 0`；`no tool`：手空且方块需要工具（`needsCorrectToolForDrops`）→ `"no tool"`。
- **#16** 攻击：**LLM 必须显式给 `entity_id`**，工具不自动选目标（PRD 强调）；范围判定 `distanceTo <= 3.0`（近战）；无效/死亡目标 → `"target invalid"`。
- **#22 wait**：不实现轮询；返回后由 WP-3 注册 tick 延迟 resumer。
- **#23 say**：`AgentSayEvent` 类在 `api/event/AgentSayEvent.java`（WP-5 定义），事件携带 agent-id（=session-id）、执行实体、文本；库**不渲染**（PRD：addon 订阅渲染）。

#### 验收标准（每个工具至少一条 JUnit 单测；**世界交互部分通过桩实体/桩世界或纯函数抽取断言**，见 §3.8）
- [ ] #1：已知方块距离正确；无匹配返回 `"not found"`；非法 block id 返回错误文本
- [ ] #2：方块状态串正确；空中/世界外正确
- [ ] #3：有库存实体逐槽正确；无库存实体返回契约文本
- [ ] #6：快照含位置/血量/手持/维度/时间
- [ ] #7：可达目标返回 `"arrived"`；不可达返回 `"path blocked"`；非 Mob 返回 `"pathfinding not supported"`
- [ ] #9：#10：返回对应文本，姿态/跳跃动作发生（断言位置/旋转变化）
- [ ] #11：正常挖掉返回 `"mined"`；基岩 `"block unbreakable"`；`mobGriefing=false` 时 `"griefing denied"` 且方块未变
- [ ] #13：手持食物使用后消耗（断言物品变化）
- [ ] #16：近处目标 `"attacked"`（血量下降）；远处 `"out of reach"`；空/无效 id `"target invalid"`；受害者也在范围内时事件被 post
- [ ] #22：返回 `"waited"`，且等待期间服务器线程未被 park（tick 埋点）
- [ ] #23：`AgentSayEvent` 被订阅者收到，携带正确 entity 与 text
- [ ] 全部工具：输入缺失字段/类型错误 → 返回 `"invalid input: ..."` 文本而非抛异常

#### 前置依赖
WP-5（基类/上下文/Griefing）。可并行推进（各工具独立）。

#### 风险 / 备注
- #11 挖掘的 `no tool` 语义与 `needsCorrectToolForDrops` 联动，测试要覆盖「空手挖石头（能挖，无掉落规则）」「空手挖铁矿石（no tool）」两种。
- 寻路类工具（#7）测试需在开阔平地模板中进行，避免模板结构干扰路径。

---

### WP-7 P1 内置工具（11 个）

- **状态**：⬜
- **PRD 映射**：§4.5 #4,5,8,12,14,15,17,18,19,20,21；§5 P1
- **目标**：补齐剩余 11 个工具，使内置目录达到 23 个（PRD §4.5 全量）。
- **范围（内）**：下列 11 个工具 + 契约 + **单测**。
- **范围（外）**：任务级工具（同 WP-6）。

#### 工具清单与契约

| # | ID | 类名（tool/...） | 输入 schema | 输出（observation） |
|---|---|---|---|---|
| 4 | `perceive.inventory_slot` | `perceive/InventorySlotTool` | `{"slot":0}` | 详细栈描述（组件/附魔/自定义名）；`"no inventory"` / `"slot empty"` |
| 5 | `perceive.nearby_entities` | `perceive/NearbyEntitiesTool` | `{"radius":16,"type_filter":"minecraft:zombie"}` | 排序列表 `"zombie (uuid=..., dist=3.2, hp=20)"`；`type_filter` 可空 |
| 8 | `loco.move_to_entity` | `loco/MoveToEntityTool` | `{"entity_id":"uuid","min_distance":3}` | `"in range"` / `"lost target"` / 原因；非 Mob → `"pathfinding not supported"` |
| 12 | `action.place_block` | `action/PlaceBlockTool` | `{"hit_pos":[x,y,z],"face":"up","hand":"main"}` | `"placed"` / `"no block in hand"` / `"target occupied"` / `"out of reach"` / `Griefing.DENIED`；落点 `inside_pos = hit_pos.adjacent(face)` |
| 14 | `action.use_item_on` | `action/UseItemOnTool` | `{"hit_pos":[x,y,z],"face":"up","hand":"main"}` | 交互结果文本（箱子打开/门切换/种作物…） |
| 15 | `action.interact_with_block` | `action/InteractWithBlockTool` | `{"pos":[x,y,z]}` | 结果 / `"not interactable"` |
| 17 | `action.drop_item` | `action/DropItemTool` | `{"slot":0,"count":8}` | `"dropped"` / `"slot empty"` |
| 18 | `action.follow_entity` | `action/FollowEntityTool` | `{"entity_id":"uuid","distance":4}` | `"following"` / `"pathfinding not supported"`；开始持续跟随直到 #19 |
| 19 | `action.stop_follow` | `action/StopFollowTool` | `{}` | `"stopped"` |
| 20 | `container.inspect` | `container/InspectContainerTool` | `{"pos":[x,y,z]}` | 逐槽内容 / `"out of reach"` / `"not a container"`（含范围/视线检查） |
| 21 | `container.transfer` | `container/TransferContainerTool` | `{"pos":[x,y,z],"from_slot":0,"to_slot":1,"count":16}` | `"transferred"` / `"not a container"` / `"out of reach"` / `"not enough items"`；目标位置每次显式传入 |

#### 关键实现要点
- **#12** 放置：`ItemStack.useOn` 语义简化版——检查手上有 `BlockItem`、落点可替换（`canBeReplaced`）、范围内；放置前 `Griefing.denied`。
- **#14/#15** 交互：调用 `Block.useWithoutItem` / `useItemOn` 的等价路径（⚠️ 26.1.2 的交互 API 签名实现时核对）；#15 对无交互行为的方块返回 `"not interactable"`。
- **#18/#19** 跟随状态：库持有 `Map<entityId, FollowTarget>`（跟随行为放 `GoalSelector` 的简单 goal 或 tick 驱动），#19 清除；跟随是持续行为，由实体 tick 驱动（注意：这是唯一带副作用的持续工具）。
- **#20/#21** 容器：目标为 `BlockEntity` 且是 `Container`/`IInventoryHolder`；范围检查（`entity.blockPosition().closerThan(pos, 6.0)`）+ 视线检查（raycast 无遮挡）；**不开玩家式 GUI**（PRD 强调 mob 无 UI 状态）。

#### 验收标准（每个工具至少一条 JUnit 单测；**世界交互部分通过桩实体/桩世界或纯函数抽取断言**，见 §3.8）
- [ ] #4：含附魔/自定义名的栈描述正确；空槽/无库存正确
- [ ] #5：半径过滤、type_filter 过滤、按距离排序正确
- [ ] #8：实体移动目标收敛为 `"in range"`；目标消失 → `"lost target"`
- [ ] #12：放置成功（断言世界方块变化）；占用/超范围/无手持/被拒四种失败文本正确
- [ ] #14：#15：对门/箱子/耕地等真实交互断言世界变化；不可交互方块返回契约文本
- [ ] #17：正确数量掉落；空槽 `"slot empty"`
- [ ] #18→#19：跟随开始后实体朝目标移动；#19 后停止（断言距离变化与状态清除）
- [ ] #20：箱子内容正确；超范围 `"out of reach"`；非容器 `"not a container"`
- [ ] #21：库存↔容器转移正确；数量不足/超范围/非容器失败文本正确
- [ ] 全部工具：输入校验失败返回 `"invalid input: ..."` 文本

#### 前置依赖
WP-5、WP-6（可复用 #7 的寻路封装）。

#### 风险 / 备注
- #18 的持续跟随是工具中唯一「有状态副作用」的，状态归属与清理（实体死亡/卸载）必须在 WP-2 的 unregister 路径上联动。

---

### WP-8 调试命令与可观测性（`/embodimentlib inspect`）

- **状态**：⬜
- **PRD 映射**：§4.7
- **目标**：运维/开发者可在运行中的服务器查询任意实体的 agent 状态、路由与最近行为。
- **范围（内）**：`/embodimentlib inspect <entity>` 命令 + `/emb` 别名；输出 5 类信息（PRD §4.7）；SLF4J 日志路由。
- **范围（外）**：热重载、远程管理、可视化面板。

#### 关键设计

**命令树（本计划解决的 PRD 开放决策 3）**
```
/embodimentlib inspect <entity>            # 主体：检查实体 agent 状态
/embodimentlib talk <entity> <text...>     # 演示触发器（WP-9 依赖；见 WP-9）
/emb                                     # 别名前缀（如 /emb inspect <entity>）
```
- 权限：`ctx.getSource().hasPermission(2)`（操作员）。
- 注册：`RegisterCommandsEvent`（`NeoForge.EVENT_BUS`）。
- `<entity>` 解析：选择器（`@e[...]`）/ UUID / 最近实体兜底（`@s` 或最近 10 格内附着实体，取其一并打印来源）。

**输出内容（PRD §4.7 全量）**
1. `agent-type` 与 `session-id`（从 attachment 读，未附着 → 明示 `"no agent attached"`）
2. 该 agent-type 当前解析到的 **profile 名 + protocol + model_name**（**不打印 api_key**）
3. 最近 N（默认 5）次工具调用：ID + 输入 + 返回 observation
4. 截断的会话预览（前 200 字符）
5. 当前状态：`IDLE / REASONING / WAITING_TOOL`

**日志**：命令执行与各 runtime 事件走 `embodimentlib.command` / `embodimentlib.runtime` logger，INFO 级。

**可测性**：命令逻辑拆出 `InspectReportBuilder(entity) → String`（纯函数，单测覆盖格式化），命令只做参数解析与输出。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [ ] 单测：`InspectReportBuilder` 对附着实体输出含 5 类信息且**不含 api_key**（桩 RegistryEntry 驱动）
- [ ] 单测：对未附着实体输出 `"no agent attached"`
- [ ] 单测：非操作员执行被拒（权限判定抽为纯函数后单测）
- [ ] 单测：`InspectReportBuilder` 对空注册表/有记录两种状态格式化正确
- [ ] 单测：别名 `/emb inspect` 的命令树解析正确

#### 前置依赖
WP-2（attachment/registry，已合并：`AgentRegistry.server()/client()`、`RegistryEntry.state()/recentToolCalls()`）、WP-3（会话预览）、WP-4（会话）、WP-6（至少 1 个工具产生记录）。未合并时用桩 RegistryEntry 单测格式化逻辑先行。

#### 风险 / 备注
- 隐私硬约束：任何输出路径不得出现 api_key / 完整会话（截断）。

---

### WP-9 演示实体 `demo_agent`（召唤 + 端到端循环）

- **状态**：⬜
- **PRD 映射**：§4.8、§1.1（端到端：听→想→走→挖→答）
- **目标**：交付全库唯一的示例实体：不自然生成、可 `/summon`、挂硬编码系统提示词 + 全量内置工具、命令触发演示完整循环。
- **范围（内）**：`DemoAgentEntity`（`PathfinderMob` 子类）、`EntityType` 注册、生成属性、系统提示词、`/embodimentlib talk` 触发器接线、端到端演示。
- **范围（外）**：任何玩家交互 UI / 聊天界面（PRD 明确 addon 职责）；自然生成。

#### 关键设计

**① 实体**
```java
public class DemoAgentEntity extends PathfinderMob {
    public DemoAgentEntity(EntityType<? extends DemoAgentEntity> type, Level level) { super(type, level); }
    // 无自定义 goal；寻路由工具 #7/#8 通过 getNavigation().moveTo(...) 驱动
}
```
- 注册：`DeferredRegister<EntityType<?>>` + `EntityType.Builder.of(DemoAgentEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).build("demo_agent")`；生成属性经 `EntityAttributeCreationEvent` 注册（默认 `Mob` 属性）。
- **不注册** `SpawnPlacementTypes`（不自然生成，PRD 硬约束）。

**② 生命周期接线（服务器端）**
- `EntityJoinLevelEvent`：实体类型为 `demo_agent` 时 → `AgentAttachment.setType(target, "demo_agent")`、`AgentAttachment.setSessionId(target, entity.getStringUUID())`（WP-2 已合并）；随后由**本 WP 自己**构造 `EmbodiedAgent` 并 `AgentRegistry.server().register(new RegistryEntry("demo_agent", sessionId, AgentHostSide.SERVER, agent, toolkit))`。注意库**不会**代为兜底注册（PLAN §8 决策 14），演示实体的注册必须由本 WP 显式完成。
- 卸载/死亡：**无需本 WP 额外接线**——WP-2 的 `AgentLifecycle` 已统一处理 `EntityLeaveLevelEvent` / `LivingDeathEvent` / 兜底扫描 / `ServerStoppedEvent`（判定依据是注册表有无条目，与附着状态无关）。

**③ 触发器（本计划解决的 PRD 开放决策 7）**
`/embodimentlib talk <entity> <text...>`：把 `text` 送入 `EmbodiedAgent.reply(text)`；最终回答通过订阅 `AgentSayEvent` 广播为聊天消息（演示实体自带一个 say 渲染订阅——真实 addon 由 addon 自己做，这里仅为演示闭环）。
- 触发命令具备 `hasPermission(2)`；`talk` 在 agent 非 IDLE 时返回 `"agent busy"`。

**④ 端到端验收脚本（手动）**
```
/summon embodimentlib:demo_agent ~ ~ ~
/embodimentlib talk @e[type=embodimentlib:demo_agent,limit=1] "到 (x,y,z) 挖一块铁矿石并报告"
→ 观察：实体寻路移动 → 挖方块 → 聊天出现回答
```

#### 验收标准（**全部为 JUnit 单测**，见 §3.8；端到端跑通另作手动展示，不计入验收）
- [ ] 单测：`demo_agent` 的 `EntityType` 注册属性正确；**未注册任何 `SpawnPlacementType`**（断言不自然生成，PRD 硬约束）
- [ ] 单测：附着逻辑对 `demo_agent` 生成 agent-type=`demo_agent`、session-id=实体 UUID（纯函数抽取后断言）
- [ ] 单测（FakeModel）：`talk` 触发后 `reply` 被调用（桩返回预设工具序列，断言观察链）
- [ ] 单测：agent 非 IDLE 时 `talk` 返回 `"agent busy"`
- [ ] 手动（记录于交付说明，不计入验收）：完整「走→挖→答」循环在 runServer 可复现，无真 LLM 时用 FakeModel 开关演示

#### 前置依赖
WP-2（附着，已合并：`AgentAttachment.setType/setSessionId/defaultSessionId`，`AgentAttachment.sideOf`）、WP-3（runtime）、WP-6（12 个 P0 工具）、WP-8（talk 命令挂在命令树）。未合并时按桩契约联调。

#### 风险 / 备注
- 演示需要 API key 或 FakeModel 开关（`config` 里 `demo.fake_model=true` 或系统属性），保证无 key 也能演示循环（PRD：服务器 owner 配一次 key 即可真跑）。
- 真 LLM 下的路径不确定性由 FakeModel 测试兜底，手动验证仅作展示。

---

### WP-10 Addon 扩展面与库分发验证（公开 API + 示例 addon）

- **状态**：⬜
- **PRD 映射**：§4.6（显式注册、ToolBase 子类化）、§6.5（扩展面清单）、§6.6（compileOnly/JarInJar 消费）
- **目标**：addon 作者按文档即可完成「选 side → 附着 → 建 Toolkit → 建 agent → 选触发器 → 订阅 AgentSayEvent → 否决工具」的全部步骤；并有一个真实示例 addon 验证库以 `compileOnly` 方式被消费。
- **范围（内）**：`EmbodimentLibAPI` 门面；`AgentTypeRegistration`；`AgentSayEvent` 订阅 API；权限钩子全局注册；示例 addon（`examples/` 独立 Gradle 模块，P1）；addon 开发文档。
- **范围（外）**：annotation 扫描（PRD 明确程序化注册）；官方 addon 生态。

#### 关键设计

**① `EmbodimentLibAPI` 门面（api 包）**
```java
public final class EmbodimentLibAPI {
    /** 注册一个字符类型：系统提示词提供者 + 可选工具补充器。同类型重复注册 → 抛异常 */
    public static void registerAgentType(String agentType,
                                         SystemPromptProvider promptProvider,
                                         Consumer<Toolkit> toolContributor);

    /** SERVER 模式：把 brain 附着到任意 LivingEntity，返回句柄 */
    public static AgentHandle attach(LivingEntity entity, String agentType, String sessionId);

    /** CLIENT 模式：本地（本玩家）agent；不写服务器 attachment */
    public static AgentHandle attachClient(String agentType, String sessionId);

    /** 订阅 AgentSayEvent（渲染层）；返回可取消的订阅 */
    public static AutoCloseable subscribeAgentSay(Consumer<AgentSayEvent> listener);

    /** 全量内置工具目录；addon 可增删 */
    public static Toolkit builtinToolkit();

    /** 权限否决钩子（WP-5，P1） */
    public static void setGlobalPermissionChecker(ToolPermissionChecker checker);

    /** 查看当前 agent-type 解析到的 profile（不含 key） */
    public static Optional<AgentProfile> resolveProfile(AgentHostSide side, String agentType);
}
```
- `AgentHandle`：`reply(String) → Mono<String>`、`close()`、`state()` 的最小门面（薄封装 `EmbodiedAgent`）。
- 所有 API 方法必须有 Javadoc 与线程说明（哪些回调在游戏线程）。

**② 示例 addon（P1，验证 §6.5/§6.6）**
- `examples/example-addon/`：独立 Gradle 模块，`compileOnly "com.hexagram2021:embodimentlib:<version>"`（从本地 `repo/` 解析，验证 WP-0 publish）；运行时经 JarInJar。
- 内容：给村民附着 `"village_npc"` 类型（自定义系统提示词 + 1 个自定义 `ToolBase`（如 `funny_emote`）+ 订阅 `AgentSayEvent` 渲染为聊天）；用**单测**断言 addon 视角下工具注册与事件收到。
- 若独立模块成本过高（CI/构建复杂度），P0 替代：主工程内 `testmod` 源集模拟 addon 行为（注册自定义工具 + 订阅事件 + 单测），文档仍给出独立模块示例代码。二者实现时二选一并在交付说明记录。
- 文档：`docs/addon-guide.md` 覆盖 PRD §6.5 六步清单，配最小可编译示例。

#### 验收标准（**全部为 JUnit 单测**，见 §3.8）
- [ ] 单测：`EmbodimentLibAPI` 各方法——注册类型、附着、订阅事件、`resolveProfile`
- [ ] 单测：addon 视角自定义 `ToolBase` 被模型调用链观察到（FakeModel 驱动）
- [ ] 单测：`AgentSayEvent` 订阅收到 `meta.say` 产生的事件
- [ ] （P1）`examples/example-addon` 独立模块 `build` 通过，依赖来自本地 `repo/`（证明 §6.6 compileOnly 消费）
- [ ] 文档 `docs/addon-guide.md` 存在，六步清单逐项有代码
- [ ] 单测：权限钩子——addon 注册 checker 后内置工具被否决（与 WP-5 验收联动）

#### 前置依赖
WP-0（publish）、WP-2/3/5/6（门面接线）。未合并时先定义接口签名 + 空实现，单测随上游合并逐步点亮。

#### 风险 / 备注
- 独立模块会引入 Gradle 复合构建复杂度；若阻碍，按上述 P0 替代方案执行并在文档记录，不得静默省略示例。

---

## 5. 执行顺序与依赖

### 5.1 依赖图

```
WP-0（基线）✅
 ├─► WP-1（配置）✅ ──► WP-2（附着/注册表）✅ ──► WP-3（运行时）✅
 │                    │                        ▲
 │                    └────────► WP-4（会话）──┤
 ├─► WP-5（工具契约）──► WP-6（P0 工具）──► WP-7（P1 工具）
 │                     │   ▲                  ▲
 │                     │   └──(并行推进)──────┘
 ├─► WP-8（命令：依赖 2/3/4/6）
 └─► WP-9（演示：依赖 2/3/6/8）
     WP-10（扩展面：依赖 0/2/3/5/6；门面接口可先行定义）
```

### 5.2 建议批次

| 批次 | 内容 | 出口标准 |
|---|---|---|
| Phase 0（基线） | WP-0 ✅ | 可构建 + 可发布 + jarjar 生效 |
| Phase 1（P0 纵切，核心交付） | WP-1 ✅ → WP-5 ✅ → WP-6 → WP-2 ✅ → WP-3 ✅ → WP-8 → WP-9 | 服务器端「召唤→说话→走→挖→答」闭环可演示（FakeModel 或真 key） |
| Phase 2（记忆 + P1） | WP-4（可并入 Phase 1 末）、WP-7、WP-5 权限钩子 ✅ | 23 工具全量 + 双环 + 会话持久化 |
| Phase 3（扩展与分发） | WP-10 | 示例 addon 可编译消费库 |

> 并行建议：Phase 1 中 WP-1/WP-5/WP-6 可并行（WP-6 依赖 WP-5 基类，先做 WP-5 的探针）；WP-7 与 WP-6 并行推进（同一契约）；WP-10 的门面签名可在 Phase 1 定义，实现随上游点亮。

---

## 6. 全局验收（端到端，发布前）

1. `.\gradlew.bat build` 与 `.\gradlew.bat publish` 成功；产物 jar 含 AgentScope jarjar。
2. **`.\gradlew.bat test` 单测全绿（0 失败）——这是唯一验收标准**（覆盖各 WP 的纯逻辑断言）。
3. `runServer` 手动冒烟：`/summon embodimentlib:demo_agent` → `/embodimentlib inspect` 显示 5 类信息（无 key）→ `/embodimentlib talk` 完成「走→挖→答」（FakeModel 开关演示 + 真 key 演示各一次，记录日志）。手动验证不计入验收，仅作展示。
4. 双端隔离抽查：CLIENT 端从未读取 `server.toml`（日志断言）；服务器聊天广播不含 key/会话；`AgentRegistry.server() != AgentRegistry.client()` 且条目不跨端可见（WP-2 单测已覆盖）。
5. `mobGriefing=false` 时 `action.mine_block` 返回 `"griefing denied"` 且世界未变（单测覆盖）。
6. 23 个工具全部注册成功（`BuiltinToolkit.create()` 计数 23），每个工具至少 1 条**单测**通过。
7. `runGameTestServer` 正常起停（exit 0），无 ERROR/FATAL——**冒烟项，不计入验收**。
8. addon 视角：示例 addon 自定义工具/事件订阅生效（§4 WP-10 验收）。

## 7. 交付物清单

| 产物 | 位置 | 说明 |
|---|---|---|
| 本执行计划 | `docs/v0.1.0/PLAN.md` | 本文档 |
| 需求文档 | `docs/v0.1.0/PRD.md` | 需求唯一权威 |
| 可构建源码 | `src/main/java/...` | 各 WP 产出 |
| 测试 | `src/test/java` | JUnit 5 单测（唯一验收依据，见 §3.8） |
| 配置示例 | `src/test/resources/config/embodimentlib/*.toml` | WP-1 测试用（当前以 night-config 内联 TOML 字符串模拟，见 `EmbodimentConfigTest`） |
| Addon 指南 | `docs/addon-guide.md` | WP-10 |
| 示例 addon | `examples/example-addon/`（P1） | WP-10 |
| 发布产物 | `repo/`（本地 Maven） | WP-0 |

## 8. 开放决策清单（本计划对 PRD「延后到实现」决策的裁决）

| # | PRD 悬置项 | 本计划裁决 | 影响 WP |
|---|---|---|---|
| 1 | 精确 TOML 键名 | §4 WP-1 ② 的结构（`[default]` / `routing` / `profiles`）；**实现载体经用户裁决由 tomlj 改为 ModConfigSpec，且经 26.1 实测把 routing/profiles 从嵌套表改为 List + JSON（见决策 11）** | WP-1 |
| 2 | NeoForge 配置屏 | **更新**：ModConfigSpec 注册天然获得配置屏，0.1 直接可用；无需自建磁盘直改 | WP-1 |
| 3 | `/embodimentlib` 命令树 | `inspect`（§4.7 主体）+ `talk`（演示触发器）+ `/emb` 别名；其余子命令延后 | WP-8/9 |
| 4 | 工具 JSON schema | 逐工具定义于 WP-6/WP-7 表格 | WP-6/7 |
| 5 | session-id 默认值 | 实体 UUID 字符串（PRD 推荐值，直接采纳） | WP-2/4 |
| 6 | 会话历史存储 | 委托 AgentScope workspace（按 session 目录）；不可用则回退库自管 `history.json` | WP-3/4 |
| 7 | 演示实体触发器 | `/embodimentlib talk`（命令驱动，符合「command-only」约束） | WP-8/9 |
| 8 | 权限钩子实现 | 库级 `ToolPermissionChecker`；可包一层 AgentScope 2.0.1 PermissionEngine（若可用） | WP-5/10 |
| 9 | AgentScope 版本 | 以 PRD 的 2.0.1 为准；若 Maven 不可得，取同线最新稳定版并记录替换 | WP-0 |
| 10 | 26.1 GameTest 函数注册 | **实测裁决**：TEST_FUNCTION 为 simple registry，bootstrap（`runLoaders`）早于 mod 构造，mod 无法在运行时注册自定义测试函数；`RegisterGameTestsEvent` 仅暴露 TEST_INSTANCE/TEST_ENVIRONMENT。故 WP-1 用 vanilla 内置 `minecraft:always_pass` 函数键走通端到端注册/发现/执行链（`wiring_smoke`）。**后续裁决（用户指令）：验收唯一标准 = `.\gradlew.bat test` 单测全绿；GameTest 仅作启动冒烟，不承载行为断言、不计入验收**（§3.8） | WP-1…WP-10 |
| 11 | 26.1 ModConfigSpec 嵌套表清空 | **实测裁决 + 用户决策**：FML 加载路径把嵌套表包装为 night-config `SynchronizedConfig`，ModConfigSpec 的 `correct()` 把其中条目视为「未声明键」删除/替换——**每次启动清空用户数据**（内联 `{}` 与 `[table]` 两种写法均复现；纯 JUnit 复现 `isCorrect=false` + REPLACE）。用户裁决：**保留 ModConfigSpec**，routing 改 `List<String>`（`"agentType=profileName"`），profiles 改 `List<String>`（每个元素一个含 `name` 字段的 JSON 对象字符串，Gson 解析）。列表可被 ModConfigSpec 无损往返：真实条目启动后原样保留、无修正 WARN | WP-1 |
| 12 | 附着类型注册表位置 | **实测裁决**：vanilla `Registries` **无** `ATTACHMENT_TYPE` 键；附着类型注册于 NeoForge 侧 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`（`neoforge:attachment_types`），`DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID)`。WP-2 已按此落码 | WP-2 |
| 13 | 附着默认值语义 | **实现裁决**：`agent_type` / `session_id` 默认值取**空串**（非 PRD 未规定的占位名）。理由：`IAttachmentHolder#getData` 在键缺失时会把默认值**写入**实体，非空默认值会让任何被触碰过的普通实体被误判为「已附着的 unknown 类型智能体」；空串与「空白视为未附着」判定天然一致。同时附着**不 serialize、不 sync**：环境重载会重建实体（addon 构造期写入才是权威来源，库持久化会双写冲突），且 PRD §4.1.1 要求两字段永不下发客户端 | WP-2 |
| 14 | 实体加入世界时是否兜底注册 | **实现裁决（否决原示意代码）**：库**不做**隐式兜底注册。原设计「监听 `EntityJoinLevelEvent`，有 attachment 就确保注册表有条目」被否决——构造 `EmbodiedAgentHandle` 需要模型 profile、系统提示词与工具集，只有 addon 知道；库代其决定会用 `[default]` 模型**悄悄发起真实计费请求**。故注册是 addon / WP-10 门面的职责，`AgentLifecyclePlan.onJoin` 恒返回 `NONE`（有显式单测锁定该决策）。卸载方向不受影响：只要注册表有条目就关闭 | WP-2 |
| 15 | 模型客户端依赖位置 | **实测裁决**：`agentscope-core` / `agentscope-harness` 2.0.1 的 jar 内**不含任何** OpenAI/Anthropic 实现类（按类型名检索 0 命中）。官方把模型适配器拆为独立 artifact `agentscope-extensions-model-openai` / `-anthropic`，经 `io.agentscope.core.model.spi.ModelProvider` SPI 发现。故 `build.gradle` 增补这两个依赖并一并 `jarJar`；PLAN 原文的 `new OpenAIChatModel(baseUrl, apiKey, modelName)` 不存在（二者只有 builder） | WP-3 |
| 16 | 同一 agent 并发 reply 的语义 | **实现裁决（用户可覆盖）**：**拒绝**（抛 `IllegalStateException("agent is busy: ...")`）而非排队。理由：排队会让命令层拿到「已受理但无反馈」的未来，玩家在聊天里看不到任何回应；显式拒绝允许 WP-8 立即回一句 `agent busy`（PLAN WP-9 ③ 原文即要求此行为）。实现用 `AtomicReference#compareAndSet`，**不用** `ReentrantLock#tryLock()`——后者对同一线程可重入，会把「tick 线程连续两次触发」误判为空闲。有非重入专项单测 | WP-3 |
| 17 | `EntityMobGriefingEvent` 的调用方式 | **实测裁决（否决原示意代码）**：原设计 `new EntityMobGriefingEvent(entity, pos)` + `post(...).isCanceled()` + `canGrief()` **不成立**。实源码核实：① 构造器为 `EntityMobGriefingEvent(ServerLevel level, Entity entity)`，**无 BlockPos**（该事件只回答「此实体此刻能否破坏」，与坐标无关）；② `EntityEvent extends Event` 而非 `ICancellableEvent`，**不存在** `isCanceled()`；③ 唯一判据是 `canGrief()`，构造时已纳入 `GameRules.MOB_GRIEFING` 初值。故 `Griefing` **直接复用 NeoForge 规范入口 `EventHooks.canEntityGrief(ServerLevel, Entity)`**（其实现即 post + 取 `canGrief()`），NeoForge 若调整语义本库自动跟随。另：非 `ServerLevel`（客户端）一律**保守拒绝**——客户端不应产生权威世界变更（PRD §4.1.1） | WP-5/6/7 |
| 18 | 工具的可测边界 | **实测裁决**：纯 JUnit 下 `LivingEntity` 类可加载，但 `net.minecraft.world.entity.animal.Pig` **不在测试编译类路径**，且 `LivingEntity(EntityType, Level)` 构造依赖注册表与世界对象——**单测无法构造实体**，故 `ToolContext` 无法实例化。应对：把「参数解析 / observation 规约 / schema 构造」下沉到无 Minecraft 依赖的 `ToolResults`，使工具契约的核心逻辑获得完整覆盖；实体相关分支（`isEntityUsable`/`level`/`asMob`、`Griefing` 真实判定、`guarded` 放行分支）**显式记为未闭合验收项**，排入 WP-6（真实游戏环境）与 WP-9（端到端），**不接受用 mock 糊过去** | WP-5/6/9 |

## 9. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| `agentscope-harness:2.0.1` 具体 API 与 PRD 描述有出入 | 高 | WP-0 后写探针测试；**WP-3 已按 `../Sources-26.1.2/` 实源码逐签名核对并记录 6 条偏差**（§4 WP-3 偏差 1–6、§8 决策 15）。WP-5 落码前同样先核对 `ToolBase`/`Toolkit.registerAgentTool` 签名 |
| JarInJar 配置语法随 moddev 版本变动 | 中 | 以官方文档为准，验收检查 jar 内 `META-INF/jarjar/` |
| 线程桥接并发缺陷（游戏线程 park / 竞态） | 高 | **已实现缓解**（WP-3）：`GameThreadExecutor` 端口 + `RecordingExecutor` 手动 drain 桩，断言「派发不含等待」（drain 前任务仍在队列且 IO 线程已在等）；`ExecutionGuard` 用 CAS 而非可重入锁（同线程重入、跨线程释放各有专项单测）；变异测试确认 3 个关键不变量断言非空转；连续 3 次 `--rerun-tasks` 全绿 |
| 破坏性工具误伤（griefing 未拦截） | 高 | 统一走 `Griefing.denied` 助手；每个破坏性工具测试 `mobGriefing=false` 用例 |
| API key 泄漏路径 | 高 | 输出/日志/网络包三处白名单审查（§3.5 + WP-8 验收） |
| 示例 addon 独立模块拖慢构建 | 低 | 提供 P0 替代（testmod 模拟），文档记录 |
| 26.1 GameTest 框架重构（无 `@GameTest`，函数注册受限） | 中 | WP-1 已实测并记录（§8 决策 10）；**已裁决规避：验收断言一律改由 JUnit 单测承担（§3.8）**，GameTest 降级为启动冒烟 |
| ModConfigSpec 嵌套表边界（26.1 清空用户数据） | 中 | **已裁决规避**（§8 决策 11）：routing/profiles 一律 List + JSON，不声明嵌套表 / Map 值；实测真实条目无损往返 |
| 注册表句柄泄漏（实体卸载后 agent 未关闭） | 中 | **已实现缓解**（WP-2）：主路径 `EntityLeaveLevelEvent` + `LivingDeathEvent` 双重注销，兜底 `ServerTickEvent.Post` 每 600 tick 扫描「有条目无实体」的泄漏项，`ServerStoppedEvent` 全清。判定依据是「注册表有无条目」而非「实体是否还带附着」，避免 addon 清附着后条目悬空。并发替换的原子性由 `ConcurrentHashMap#compute` + 8 线程并发单测保证 |
| 附着读取的隐式副作用（`getData` 写回默认值） | 中 | **已裁决规避**（§8 决策 13）：解析一律走 `getExistingDataOrNull`（经 `AttachmentTarget` 抽象），并有「纯读取不写入附着」专项单测 |

---

*End of PLAN. 子 Agent 按 §0 规则执行；状态更新见各 WP 状态行。*
