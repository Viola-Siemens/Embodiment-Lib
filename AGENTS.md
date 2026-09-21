# AGENTS.md

> 给 AI（及协作者）的项目速览：这个模组是什么、怎么跑、有哪些常用命令、愿景与最终成品形态，以及如何快速上手工作。**需求唯一权威 = `docs/**/PRD.md`；可执行分解 = `docs/**/PLAN.md`（子 Agent 按其执行）。**

---

## 1. 这是什么

**Embodiment Lib** 是一个 **NeoForge 库模组（library mod）**，为 Minecraft Java Edition 提供「LLM 驱动的世界感知实体」底层基座。它本身不是玩家能直接玩的角色，而是给 addon 作者用的 SDK——「引擎，不是车」。

核心能力：

- 给任意 `LivingEntity` 挂载实体侧「大脑」（数据附着：agent-type + session-id）；
- 内嵌 **AgentScope Java** 运行时（`agentscope-harness:2.0.1`，JarInJar 打入 mod jar，下游无需单独安装）；
- OpenAI / Anthropic 兼容模型提供方配置与按 agent-type 的路由（双端各自独立 TOML）；
- 23 个**原子、可组合**工具（感知 / 移动 / 挖掘 / 放置 / 物品使用 / 战斗 / 容器 / 元操作）；
- 明确的 addon 扩展点：自定义工具、系统提示词、会话路由、权限否决钩子、`AgentSayEvent` 渲染事件；
- 一个仅命令可召唤的演示实体 `demo_agent`，端到端演示「听 → 想 → 走 → 挖 → 答」。

一个基于它写的 addon，几十行代码就能把村民、自定义怪甚至猪变成「听到玩家说话 → LLM 推理 → 走到目标 → 挖方块 → 回答」的实体。

## 2. 愿景

**愿景（PRD §1.3）**：`Any entity in Minecraft can think. Embodiment Lib makes it easy.`

- v1.0 时，安装「Embodiment Lib + 一个 addon」，玩家视角与手工打造的 AI 同伴模组**不可区分**；
- 但 addon 作者只需花**几天而不是几个月**——不用自己写 HTTP 客户端、JSON schema、寻路封装、NBT 持久化、ReAct 循环和「别卡服务器线程」的管道。

**0.1 最终成品形态**（玩家/服务器视角）：

- 服务器 owner 填一个 `config/embodimentlib/server.toml`（base URL / key / model），服务器上所有 AI 实体默认用它；
- 高阶 agent 可通过 `[routing]` 指定更强的 profile，其余用便宜默认模型；API key **只在做推理的那一端**，永不下发客户端；
- 运维可用 `/embodimentlib inspect <entity>` 查看某实体用的模型、最近工具调用与状态；
- 开发者/玩家可用 `/summon embodimentlib:demo_agent` 召唤演示实体，用 `/embodimentlib talk` 喂一句话看它走、挖、答；
- addon 作者：一个 Java 类 = 一个工具（name / JSON schema / 权限检查 / 异步执行器），注册进 `Toolkit` 即扩展能力。

**0.1 明确不做**（PRD §1.4）：玩家 UI / 聊天交互系统、领地保护集成、token 排队/背压、Fabric/Forge、视觉/语音、MCP 服务端、跨版本迁移。

## 3. 项目结构（当前实测状态）

```
Embodiment-Lib
├── docs/
│   └── v0.1.0/
│       ├── PRD.md      # 需求唯一权威（v0.1）
│       └── PLAN.md     # 执行计划：11 个 WP、MECE 分解、子 Agent 可执行 Spec
├── src/
│   ├── main/
│   │   ├── java/com/hexagram2021/embodimentlib/
│   │   │   ├── EmbodimentLib.java      # 主类，MODID="embodimentlib"
│   │   │   ├── api/                    # WP-1/6：AgentHostSide、AgentProfile；event/AgentSayEvent（WP-6）
│   │   │   ├── config/                 # WP-1：EmbodimentConfig、HostConfig、AgentProfileConfig
│   │   │   ├── attach/                 # WP-2：AttachmentTypes、AgentAttachment、AgentRegistry、RegistryEntry、AgentLifecycle(/Plan)、EmbodiedAgentHandle
│   │   │   ├── runtime/                # WP-3：EmbodiedAgent、AgentLoop、ThreadBridge、ToolBridge、ExecutionGuard、ModelFactory、GameThreadExecutor
│   │   │   ├── memory/                 # WP-4：SessionPaths（纯逻辑：布局+id 校验）、SessionSink（写穿端口）、SessionData（记忆/待办）、SessionStore（根解析+IO+缓存）、SessionLifecycle（接线）
│   │   │   ├── tool/                   # WP-5/6/7：EmbodiedToolBase、ToolContext(Scope)、ToolResults、Griefing、BuiltinToolkit、ToolPermissionChecker
│   │   │   │   │                       #   纯逻辑基础设施（跨子包共用）：Slots（槽位文本+槽号/数量解析）、Containers、Blocks、BlockAccess、BlockCoordinates
│   │   │   │   ├── perceive/           # WP-6/7：NearestBlockTool、BlockStateAtTool、InventoryContentsTool、InventorySlotTool、NearbyEntitiesTool、SelfStatusTool（+各自 *Logic；ResourceId 为 id 校验）
│   │   │   │   ├── loco/               # WP-6/7：MoveToTool、MoveToEntityTool、JumpTool、LookAtTool（+各自 *Logic）
│   │   │   │   ├── action/             # WP-6/7：MineBlockTool、PlaceBlockTool、UseItemTool、UseItemOnTool、InteractWithBlockTool、AttackEntityTool、DropItemTool、FollowEntityTool、StopFollowTool
│   │   │   │   │                       #   另有 BlockAimLogic/BlockInteractionLogic（#12/#14/#15 共用纯逻辑）、FollowGoal/FollowService（唯一持久状态）、InteractionResults（原版结果映射）
│   │   │   │   ├── container/          # WP-7：InspectContainerTool、TransferContainerTool（+InspectContainerLogic/TransferLogic）
│   │   │   │   └── meta/               # WP-6：WaitTool、SayTool（+各自 *Logic）
│   │   │   └── gametest/               # WP-1：ConfigGameTests（冒烟）
│   │   ├── resources/pack.mcmeta       # pack_format 84 + min/max_format（26.1 schema）
│   │   └── templates/META-INF/neoforge.mods.toml   # 占位符模板，构建时展开
│   └── test/java/com/hexagram2021/embodimentlib/   # 验收唯一依据（JUnit 5）
│       ├── api/ config/ attach/ runtime/ memory/ tool/     # 与主源集同构
│       │   └── runtime/                # 另含测试桩 FakeModel（脚本化模型）、RecordingExecutor（手动 drain 游戏线程桩）
├── src/generated/resources/            # datagen 输出（当前为空）
├── build.gradle        # moddev 2.0.141；JarInJar 打入 AgentScope；publish 到 repo/
├── settings.gradle     # rootProject.name='embodimentlib'
├── gradle.properties   # MC 26.1.2 / NeoForge 26.1.2.71 / Java 25 / 版本 0.1.0+mc26.1.2
├── repo/               # 本地 Maven 仓库（publish 输出，gitignore）
└── run/                # 运行产物（世界、日志，gitignore）
```

## 4. 技术基线（改代码前先核对这里）

| 项              | 值                                                                                          |
|----------------|--------------------------------------------------------------------------------------------|
| Minecraft      | 26.1.2（范围 `[26.1, 26.2)`）                                                                  |
| 加载器            | NeoForge `26.1.2.71`（loader 范围 `[4,)`）                                                     |
| JDK            | **Java 25**（本机：`C:\Program Files\BellSoft\LibericaJDK-25`）                                 |
| 构建             | Gradle 9.2.1 wrapper + `net.neoforged.moddev` 2.0.141                                      |
| Mod ID / Group | `embodimentlib` / `com.hexagram2021.embodimentlib`                                         |
| 版本             | `0.1.0+mc26.1.2`                                                                           |
| 内嵌运行时          | `io.agentscope:agentscope-harness:2.0.1` + `agentscope-core:2.0.1` + `agentscope-extensions-model-openai:2.0.1` + `agentscope-extensions-model-anthropic:2.0.1`（全部 JarInJar，范围 `[2.0.0,)`） |
| 许可证            | Artistic-2.0（内嵌 AgentScope 为 Apache-2.0）                                                   |

⚠️ **模型客户端在扩展模块里**：`agentscope-core` / `agentscope-harness` 的 jar **不含**任何 OpenAI/Anthropic 实现类；官方拆为 `agentscope-extensions-model-openai` / `-anthropic` 两个 artifact，经 `io.agentscope.core.model.spi.ModelProvider` SPI 发现。核对该包 API 时源码在 `../Sources-26.1.2/io/agentscope/extensions/model/`（PLAN §8 决策 15）。

⚠️ **环境注意**：系统默认 `JAVA_HOME` 指向 JDK 17；跑 Gradle 前需临时切到 JDK 25（见 §5 命令前缀），否则工具链解析会失败或下载额外 JDK。

## 5. 如何运行与常用命令（Windows，仓库根执行）

> 统一前缀（每条命令前先设置）：
> `$env:JAVA_HOME="C:\Program Files\BellSoft\LibericaJDK-25"; $env:Path="$env:JAVA_HOME\bin;$env:Path";`

| 目的               | 命令                                                   | 说明                                                                                      |
|------------------|------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 编译 + 打包          | `.\gradlew.bat build`                                | 产物在 `build/libs/`，内含 `META-INF/jarjar/`                                                 |
| **单元测试（验收唯一标准）** | `.\gradlew.bat test`                                 | JUnit 5 纯逻辑测试；**WP 验收以本命令全绿为准**                                                         |
| 游戏测试 / 冒烟        | `.\gradlew.bat runGameTestServer`                    | 启动测试服务器，走完服务端**完整生命周期**后自动终止；仅验证 mod 能正常起停（26.1 起无 `@GameTest` 注解，见 §7 第 4 条），**不计入验收** |
| 启动服务器            | `.\gradlew.bat runServer`                            | 交互式专用服务器（`--nogui`），不会自动退出，需手动停                                                         |
| 启动客户端            | `.\gradlew.bat runClient`                            | 打开游戏窗口（手动验证 UI / 模组加载）                                                                  |
| 数据生成             | `.\gradlew.bat runData`                              | datagen，输出到 `src/generated/resources/`                                                  |
| 发布本地 Maven       | `.\gradlew.bat publish`                              | 输出到 `repo/`（坐标见 §4）                                                                     |
| 检查产物             | `jar tf build\libs\embodimentlib-0.1.0+mc26.1.2.jar` | 查看 jar 内条目                                                                              |

**冒烟检查点**（以 `runGameTestServer` 日志为准）：Mod List 出现 `Embodiment Lib`；`META-INF/jarjar/` **四个** AgentScope jar 被发现（core / harness / extensions-model-openai / extensions-model-anthropic）；无 `ERROR`/`FATAL`/mod 相关 `WARN`；进程正常终止（exit 0）。

## 6. 核心概念速览（上手必读）

| 概念              | 要点                                                                                                                                                                                                                                                                                                                                   |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AgentHostSide` | `SERVER` / `CLIENT` 两种独立宿主模式；**永不共享** agent、配置、会话、API key                                                                                                                                                                                                                                                                            |
| `agent-type`    | 字符类型（如 `"village_npc"`）→ 路由模型 profile + 系统提示词（按类型注册）                                                                                                                                                                                                                                                                                 |
| `session-id`    | 个体身份（0.1 默认 = 实体 UUID 字符串）→ 会话/记忆隔离；同类型不同个体**不共享对话**                                                                                                                                                                                                                                                                                 |
| 附着（Attachment）  | `attach/` 包：`agent_type` / `session_id` 两个字符串附着（注册于 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`，**非** vanilla `Registries`）。**默认为空串且不持久化、不同步**（PRD 硬约束：永不下发客户端）；读取一律用 `getExistingDataOrNull`（`getData` 会把默认值写回实体）；两字段任一空白即视为未附着 |
| 注册表             | `AgentRegistry.server()` / `client()` 两个**不同**单例（PRD 隔离硬约束）；`register` 用 `ConcurrentHashMap#compute` 保证「替换 + 关闭旧句柄」原子；跨端注册直接抛异常。**库不做隐式兜底注册**——构造智能体（需 profile/提示词/工具集）是 addon 职责，否则会用默认模型悄悄发起真实计费请求 |
| 配置文件            | `config/embodimentlib/server.toml` 与 `client.toml`，互不读取；ModConfigSpec 驱动（参考 GirlfriendsCommonConfig 写法）。**26.1 约束：routing = `List<String>`（`"agentType=profileName"`），profiles = `List<String>`（JSON 对象字符串，含 `name` 字段，Gson 解析）**——嵌套表会被 ModConfigSpec 清空（PLAN §8 决策 11）；routing/profiles 必须位于 `[default]` 之前（根级），否则归入 `[default]` 表 |
| 会话目录            | 服务器端在**世界目录**下、客户端在本地 config 下（`<base>/embodimentlib/sessions/<session-id>/`）；两棵树永不合并；根目录**每次调用重新解析**（单人存档切世界时 JVM 不重启，缓存旧路径会写进上一个存档）                                                                                                                                                                                       |
| 会话存储（`memory/`） | WP-4：`SessionPaths`（**纯逻辑**：目录布局 + session-id 校验——字符集 `[A-Za-z0-9._-]{1,64}`，拒 `.`/`..`/分隔符/Windows 设备名，**拒绝而非清洗**）、`SessionSink`（写穿端口，把副作用挪出数据层）、`SessionData`（工作记忆 + 待办，**改一次写一次**；未改变数据的调用不写盘）、`SessionStore`（根解析 + Gson 原子写 + 会话缓存 + AgentScope 状态存储/历史查询）、`SessionLifecycle`（实体消失 → `evict` 写盘并摘缓存；存档/停服 → `flushAll`）。**会话历史不由本库写**：`JsonFileAgentStateStore` 被显式指向会话目录（`EmbodiedAgent.create` 的 `stateStore` 参数），落在 `<session dir>/__anon__/<session-id>/agent_state.json`（PLAN §8 决策 6/20） |
| 工具契约            | 输入 = 工具自定 JSON；输出 = 纯文本 observation 回喂 LLM；失败（异常/超时/无目标/被拒）**转文本，禁止抛异常**                                                                                                                                                                                                                                                             |
| 工具绑定            | 工具永远绑定**执行时所在实体**（`ToolContext`），LLM 无需传实体                                                                                                                                                                                                                                                                                           |
| 线程规则            | LLM HTTP 在 IO 池；工具体**必须**桥到游戏线程（SERVER=服务器线程 / CLIENT=客户端线程）；`meta.wait` 非阻塞，绝不 park 游戏线程。实现见 `runtime/ThreadBridge`：**派发不含等待**，等待发生在 IO 线程侧（`CompletableFuture#get(timeout)`），方向由 `ThreadBridge.executorFor(side, server, client)` 唯一裁决 |
| 运行时包装（`runtime/`） | WP-3：`EmbodiedAgent`（包装 `HarnessAgent`，实现 `EmbodiedAgentHandle`；`reply()` 返回 `Mono<String>`）；`AgentLoop`（`REACT_STEP` 逐步环 P0 / `BATCH_TOOLS` 批量环 P1；默认 12 步）；`ToolBridge`（工具异常/超时/空/null → 文本 observation，**永不抛异常**；`null` 与「超时」用引用比较哨兵区分）；`ExecutionGuard`（**忙碌即拒绝而非排队**，用 CAS 不用可重入锁——同线程重入会被误判为空闲）；`ModelFactory`（按 protocol 造 `OpenAIChatModel`/`AnthropicChatModel`） |
| `GameThreadExecutor` | WP-3 测试缝：把「排到本端游戏线程」抽为端口（真实实现 = `server.execute` / `Minecraft#execute`），使桥接的**方向**与**零 park** 可在无游戏进程下单测（`RecordingExecutor.manual()` + `drain()`） |
| Griefing        | 破坏性动作（挖/放/攻击…）先过 `tool/Griefing.denied(...)`，被拒 → 返回 `"griefing denied"`。**不要自行 `new EntityMobGriefingEvent(entity, pos)`**——该构造器在 26.1.2 是 `(ServerLevel, Entity)`、**无 pos**，且事件**不可取消**（`EntityEvent extends Event`，非 `ICancellableEvent`），唯一判据是 `canGrief()`；`Griefing` 复用 NeoForge 规范入口 `EventHooks.canEntityGrief`。非 `ServerLevel`（客户端）一律保守拒绝（PLAN §8 决策 17） |
| 工具契约（`tool/`） | WP-5/6/7：`EmbodiedToolBase`（继承 AgentScope `ToolBase`，`run` → observation，异常/空/null 全转文本）；`ToolContext`（record：side/entity/agentType/sessionId）+ `ToolContextScope`（ThreadLocal，**可嵌套**且**不继承到子线程**）；`ToolResults`（**无 Minecraft 依赖的纯逻辑层**：参数解析/observation 规约/schema 构造）；`BuiltinToolkit`（23 工具唯一清单 `BUILTIN_TOOL_IDS` + 装配/去重/裁剪/覆盖度校验——P0/P1 是内部口径，**不暴露为 API**；`create()` 已注册**全部 23 个**工具，`without()` 真实过滤）；`ToolPermissionChecker`（否决钩子，**默认放行**）。每个工具 = `*Logic` 纯逻辑类（无 Minecraft 依赖、可单测）+ `*Tool` 薄适配器（世界交互）；跨子包共用的纯逻辑下沉到 `tool/Slots`/`BlockAccess`/`BlockCoordinates`，MC 侧助手为 `tool/Containers`/`Blocks`；`api/event/AgentSayEvent` 由 `meta.say` post（库不渲染） |
| 单测的实体边界 | **实测：纯 JUnit 无法构造 `LivingEntity`**——`Pig` 不在测试编译类路径，且 `LivingEntity(EntityType, Level)` 需注册表与世界。故 `ToolContext` 无法实例化：`isEntityUsable`/`level`/`asMob`、`Griefing` 真实判定、`guarded` 放行分支、各工具的世界交互（真实读块/扫描/寻路/落块/交互/伤害/消耗/掉落/容器读写/跟随移动/姿态变化/事件订阅/`meta.wait` 延迟恢复）**均未闭合**，排入 WP-9 端到端（PLAN §8 决策 18）。WP-6/7 已把可纯函数化的裁决与文本下沉到各工具的 `*Logic` 类与 `tool/` 基础设施类并获得完整覆盖 |
| 日志              | SLF4J，logger 名空间 `embodimentlib`（子域 `embodimentlib.config` / `.runtime` / `.registry` / `.memory` / `.tool` / `.command`）                                                                                                                                                                                                                                                     |

## 7. 工作方式（AI 执行约定）

1. **PRD 是需求唯一权威**；PLAN.md 是它的可执行分解——11 个工作包（WP-0…WP-10），MECE：每个 WP 独立、可单测、有验收清单与验证命令。
2. **执行顺序**：先 P0 链（WP-1→WP-5→WP-6→WP-2→WP-3→WP-8→WP-9），再 P1/记忆项（**WP-4 ✅、WP-7 ✅ 均已完成**），最后 WP-10；依赖图见 PLAN §5。WP-8 依赖 WP-4（`inspect` 要展示会话预览），该前置已就绪。
3. **当前进度**：WP-0 ✅、WP-1 ✅（配置系统：`AgentHostSide` + ModConfigSpec 双端 TOML + Profile 路由——routing 为 List、profiles 为 JSON 列表，见 §6 配置行）、WP-2 ✅（实体附着 + 双端注册表：`attach/` 包提供 `agent_type`/`session_id` 附着、`AgentRegistry` 双端单例、`RegistryEntry`、`AgentLifecycle` 生命周期钩子）、WP-3 ✅（代理运行时：`runtime/` 包提供 `EmbodiedAgent`、`AgentLoop` 双环、`ThreadBridge`/`ToolBridge` 游戏线程桥接、`ExecutionGuard` 串行化、`ModelFactory` 模型工厂）、WP-4 ✅（会话与记忆持久化：`memory/` 包提供 `SessionPaths`（纯逻辑布局与 id 校验）、`SessionSink`/`SessionData`（写穿的工作记忆与待办）、`SessionStore`（根解析 + Gson 原子写 + 缓存 + AgentScope 状态存储/历史查询）、`SessionLifecycle`（实体消失 → 写盘并摘缓存；存档/停服 → flushAll）；会话历史改由 `JsonFileAgentStateStore` 落在会话目录内，全量 `test` **353 例 0 失败**）、WP-5 ✅（工具契约与基础设施：`tool/` 包提供 `EmbodiedToolBase`、`ToolContext`+`ToolContextScope`、`ToolResults`、`Griefing`、`BuiltinToolkit`、`ToolPermissionChecker`）、WP-6 ✅（P0 内置工具 12 个：`tool/perceive|loco|action|meta` 四个子包，每个工具 = `*Logic` 纯逻辑类 + `*Tool` 薄适配器；`api/event/AgentSayEvent`）、WP-7 ✅（P1 内置工具 11 个：新增 `tool/container` 子包；基础设施下沉 `tool/Slots`/`Containers`/`Blocks`/`BlockAccess`/`BlockCoordinates`、`perceive/ResourceId`；`BuiltinToolkit.create()` 已注册**全部 23 个**工具；唯一持久状态 `FollowService`/`FollowGoal` 由主类 `EmbodimentLib` 接线）；下一步 **WP-8**（调试命令 `/embodimentlib inspect`，前置 WP-2/3/4/6 已全部就绪），随后 **WP-9**（演示实体 `demo_agent` + 全部未闭合验收项的端到端验证），最后 **WP-10**（Addon 扩展面与分发）。
   ⚠️ **WP-5/6/7 留有未闭合的验收项**：`ToolContext` 需要真实 `LivingEntity`，纯 JUnit 构造不出来，故实体相关分支（`Griefing` 真实判定、`guarded` 放行分支、`"entity unavailable"`、各工具的世界交互：真实读块/扫描/寻路/落块/交互/伤害/消耗/掉落/容器读写/跟随移动/姿态变化/事件订阅/`meta.wait` 延迟恢复）**推迟到 WP-9 端到端验证**（PLAN §8 决策 18）；WP-6/7 已把可纯函数化的裁决与文本下沉到 `*Logic` 类与 `tool/` 基础设施类并获得完整覆盖。
   ⚠️ **WP-7 遗留一处能力缺口**：`action.use_item_on` / `action.interact_with_block` 的原版交互 API 在 26.1.2 **强制要求非空 `Player`**，故非玩家身体返回 `"interaction requires a player body"`（刻意不伪造 `FakePlayer`，理由见 PLAN §8 决策 19 与 WP-7 偏差 1）。WP-9 需决策是否补 mob 侧原版路径（如 `DoorBlock#setOpen(@Nullable Entity)`）。
   ⚠️ **WP-4 遗留两处未闭合项**：① `EmbodiedAgent.create` 新增的 `stateStore` 参数需调用方（WP-9/WP-10 门面）传 `SessionStore#openStateStore`——「真实 HarnessAgent 每轮把对话写进会话目录」这条链路无法在纯 JUnit 下闭合（WP-3 已确立「测试不构建真实 HarnessAgent」的边界），本 WP 只验证到 `AgentStateStore` 层面；② `forSide` 的生产根目录（世界目录 / FML 配置目录）需真实服务器与客户端才能解析，单测覆盖的是 `forRoot` 布局 + 无环境时的降级行为。
   ⚠️ **源码缺口**：`net/neoforged/fml` 的源码不在 `../Sources-26.1.2/`（该目录只有 `net/neoforged/neoforge`），故 `FMLPaths.CONFIGDIR.get()`（WP-4 客户端会话根）与 WP-1 已在用的 `ModConfig`/`ModConfigEvent` 一样，签名只能靠**编译**核对；如需逐行核对请补充该模块源码。
4. **测试与验收标准（重要）**：**验收以 `.\gradlew.bat test` 单测全绿为准**，不得把 GameTest 作为验收依据。26.1 起**没有 `@GameTest` 注解**，且 `TEST_FUNCTION` 注册表 bootstrap 早于 mod 构造，**mod 无法注册自定义测试函数**，因此 GameTest 只能做「注册链/生命周期」的冒烟验证（现有 `embodimentlib:wiring_smoke` 用 vanilla 内置 `minecraft:always_pass` 函数键走通链路），无法承载真实行为断言。**所有行为断言一律写 JUnit 单测**（PLAN §8 决策 10）；`runGameTestServer` 仅作启动冒烟，不计入验收。
5. 每个 WP 完成 = 验收标准全部勾选 + `.\gradlew.bat test` 全绿 + **更新 PLAN.md 该 WP 状态行**（⬜/🚧/✅）。
6. 不确定的第三方 API（主要是 AgentScope 2.0.1 签名）：先阅读源码和 JavaDoc 核对再落码，禁止臆造。**源码一律从 `../Sources-26.1.2/` 读**（见 §9），不要在源码缺失时自行解压/反编译 jar——直接告诉用户缺哪个依赖的源码。
7. 安全红线（违反即失败）：API key 只存在于做推理的一端，**不得**出现在网络包、日志、命令输出；双端配置/会话/注册表隔离；工具失败不抛异常；游戏线程不 park。
8. 测试中**不得发起真实 LLM 调用**：工具用 `ToolContext` 直驱，循环用桩模型（PLAN WP-3 已提供 `runtime/FakeModel`）。

## 8. 相关文档入口

- `docs/v0.1.0/PRD.md` — 需求：范围边界、Persona、23 工具目录、技术架构、优先级（P0/P1/P2）
- `docs/v0.1.0/PLAN.md` — 执行：11 个 WP 详述、统一约定、开放决策裁决、风险登记

# 9. 相关代码

依赖库的源代码（包括 Minecraft、NeoForge、Night Config 等）均位于 `../Sources-26.1.2/` 目录中，如有缺失，直接告知用户，**不要自行解压 jar 包**。

AgentScope 相关源码位置：

- `../Sources-26.1.2/io/agentscope/core/` — 核心（agent/model/message/tool/state/skill）
- `../Sources-26.1.2/io/agentscope/harness/agent/HarnessAgent.java` — 被包装的 agent 与其 `Builder`
- `../Sources-26.1.2/io/agentscope/extensions/model/openai/` — `OpenAIChatModel`（及 `compat/` 下的 deepseek/glm/kimi/minimax 兼容层）
- `../Sources-26.1.2/io/agentscope/extensions/model/anthropic/` — `AnthropicChatModel`