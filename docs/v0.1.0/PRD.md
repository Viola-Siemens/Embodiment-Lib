# Embodiment Lib — Product Requirements Document

| Field | Value |
|---|---|
| Project | Embodiment Lib (`embodimentlib`) |
| Version | 0.1 (initial scope) |
| Status | Draft for alignment |
| Owner | Liu Dongyu |
| License | Artistic-2.0 (mod); Apache-2.0 (bundled AgentScope Java) |
| Last updated | 2026-09-14 |

---

## 1. Overview & Vision

### 1.1 What it is

Embodiment Lib is a **library mod** for Minecraft: Java Edition (NeoForge) that gives addon mod developers the missing substrate for building **LLM-driven, world-aware entities**. It does not ship a product players directly interact with — instead, it provides:

- an attachable, entity-side "brain" (a capability/data-attachment on any `LivingEntity`),
- a wrapped LLM agent runtime (built on the official **AgentScope Java** framework, bundled inside the mod via JarInJar),
- configuration and routing for OpenAI-compatible and Anthropic-compatible model providers,
- a catalog of **atomic, composable tools** covering perception, locomotion, mining, placing, item use, combat and container interaction,
- a documented extension point so addons can register their own tools, system prompts, and session routing.

An addon built on top of Embodiment Lib can, in a few dozen lines, turn a vanilla villager, a custom mob, or even a pig into an entity that hears a player's chat line, reasons over it with an LLM, walks across the terrain, mines a block, and hands back an answer.

### 1.2 Why now

A wave of "AI NPC" and "AI companion" mods have appeared (Blockpal, Numen, Steve AI, Player2NPC, MobMind, etc.), but almost every one of them:

- is a **player-facing product** with a fixed character, fixed personality and fixed set of abilities;
- either runs an external Node/Python process, locks the user into a proprietary cloud, or hard-codes a single model provider;
- implements its own perception, pathfinding and tool-calling loop from scratch;
- exposes little or no API for other modders to build on.

Embodiment Lib occupies the empty layer below those products: it is **the engine, not the car**. Its ambition is to become the de facto "Minecraft embodiment SDK" — the thing a modder reaches for when they want *their* AI villager, *their* AI golem, or *their* AI hostile mob, without re-solving the LLM plumbing.

### 1.3 Vision statement

> **Any entity in Minecraft can think. Embodiment Lib makes it easy.**

By v1.0, installing Embodiment Lib + one addon should be indistinguishable, from the player's point of view, from playing with a hand-crafted AI companion mod — but the addon author should have spent days, not months, building it.

### 1.4 Scope boundaries (in / out)

**In scope for 0.1**

- A dual-host embodiment runtime that can either attach an AgentScope brain to a server-side `LivingEntity`, or run a self-contained agent on the client (see `AgentHostSide` in §4.1).
- TOML-based model provider configuration with default + per-agent-type profile routing, resolved independently per host side.
- A built-in catalog of atomic perception and action tools (see §4.5).
- A summonable demo entity, command-only, that proves the loop end-to-end on the server.
- Debug command for inspecting an entity's agent state, recent tool calls and model routing.
- Respect for vanilla `EntityMobGriefingEvent` so that built-in mining/placing actions behave like any other mob's griefing.

**Explicitly out of scope for 0.1**

- Any player-facing UI, chat interaction, dialogue system, or "command the agent" UX — that is the addon's job.
- Integration with third-party land-claim / protection mods (FTB Chunks, GriefPrevention, WorldGuard, etc.).
- Token queuing, request coalescing, rate-limit backpressure across multiple agents.
- Fabric / Quilt / Forge (non-NeoForge) support.
- Vision / multimodal input (the agent's "eyes" are structured world queries, not rendered screenshots).
- Voice input/output.
- MCP server hosting.
- Automatic migration between Minecraft versions outside the declared range.

---

## 2. User Personas & Stories

### 2.1 Persona A — The Addon Modder (primary user)

- **Who.** An experienced Minecraft mod developer, usually solo or in a small team, who wants to build an AI-themed addon: an AI villager overhaul, a tameable AI companion, a horror-mob that reasons about the player, an NPC bartender, a "roguelike dungeon with smart monsters".
- **Goals.** Spend their energy on their mod's identity — character, story, balancing, rendering — not on re-implementing OpenAI clients, JSON schemas, pathfinding wrappers or NBT persistence.
- **Frustration today.** Every existing AI mod is closed. Wrapping an LLM from scratch in NeoForge means writing the HTTP layer, the tool-calling loop, the retry/timeout policy, the per-entity session store, and the "make sure I don't freeze the server thread" plumbing themselves.

**Stories**

- As a modder, I want to attach a brain to an existing vanilla entity type (e.g. villager) via one registration call, so that I don't have to write a new entity class.
- As a modder, I want to write one Java class per tool (name, JSON schema, permission check, async executor) and register it on a toolkit, so I can extend what the agent can do without touching Embodiment Lib internals.
- As a modder, I want the library to handle the OpenAI/Anthropic HTTP call, the ReAct loop and the tool-result feeding, so I only write the system prompt and the trigger (e.g. "when a player says my mob's name, invoke the agent").
- As a modder, I want tool failures (block unmineable, target moved away, path blocked) returned to the LLM as text observations, so the model can recover on its own instead of crashing.
- As a modder, I want the library to post `EntityMobGriefingEvent` before it breaks or places blocks, so my addon inherits vanilla mob-griefing rules for free.

### 2.2 Persona B — The Server Owner / Modpack Maker (secondary, indirect user)

- **Who.** Runs a NeoForge survival server or a public modpack, who does not write code but installs addons built on Embodiment Lib.
- **Goals.** Get AI NPCs working for their players without leaking API keys, without paying per-player, and without a second process running on the box.

**Stories**

- As a server owner, I want to fill one TOML file with my provider's base URL, key and model name, and have every AI entity on the server use it by default.
- As a server owner, I want to override the model for specific high-stakes agents (e.g. the quest-giver) while the rest use the cheap default profile, via a small map in the same TOML.
- As a server owner, I want the API key to live only on the server and never appear in a network packet sent to clients.
- As a server owner, I want to be able to ask the running server "which model is this entity using, and what did it just try to do?" via an operator command.

### 2.3 Persona C — The End Player (tertiary, never sees Embodiment Lib)

- **Who.** A Minecraft player who installs a modpack that happens to include Embodiment Lib under the hood.
- **Goals.** Talk to and command the addon's characters. They do not know and do not care which library is powering them.

**Stories**

- As a player, I never see Embodiment Lib in my mod list as something I configure.
- As a player, I experience the addon's characters exactly as the addon author designed them — their chat style, their commands, their voice.
- As a player on a server-hosted addon, I never have to enter an API key; my server owner has done that for me. As a player on a self-hosted client-side addon, I fill in my own key once, locally.

---

## 3. Competitive Analysis (Mod Landscape)

The adjacent landscape splits into three buckets: (i) player-facing AI companion products, (ii) chat-only AI NPC mods, and (iii) external-process / bot-framework bridges. Embodiment Lib is deliberately in a fourth bucket: a **library**.

| Mod | Type | Loader / MC | How it acts in the world | Model & key story | Extensibility | Gap vs. Embodiment Lib |
|---|---|---|---|---|---|---|
| **PlayerEngine** (Goodbird) | Server-side framework + reference companion (Player2NPC) | Forge/NeoForge/Fabric, 1.20–1.21 | Custom mobs get player-like abilities: mining, fighting, inventory, world interaction | Driven by the hosted **Player2 platform**; not self-hostable | Designed as a framework, but tightly coupled to the Player2 backend | Closed backend; not an open SDK that other modders build characters on. |
| **NeuraCraft** | AI chatbot mod (self-described "framework") | Forge, 1.20.1+ | Chat-only in current builds; "AI as a real player" planned for v4 | OpenAI-compatible; multi-room, one AI per room | Mild — mainly a chat SDK, not embodiment tools | No embodied action set today; vision of a framework, not yet one. |
| **Numen** (Dwinovo) | Player-facing AI companion | NeoForge/Fabric, recent | Self-plans, mines, builds, farms, fights, crafts; exposes an MCP server for external AI apps | Bring-your-own key (G-key menu); supports MCP backends | Per-bot customization, community-shared skills | A product, not a library; one fixed character; no documented API for other modders. |
| **Steve AI** (steveai.app) | Player-facing autonomous agents | Fabric, recent | Multi-agent build / mine / fight / explore with task split | Defaults to Groq; OpenAI/Gemini swappable | Per-agent customization | Product, not SDK; closed multi-agent coordination. |
| **MobMind** | AI conversation mod | Forge, recent | Befriend vanilla mobs; voice chat; follow / wait / roam / protect | DeepSeek; microphone input, per-mob voice presets | Personality and voice presets | Conversation-first; no general-purpose tool catalog; no LLM-as-planner loop. |
| **Blockpal AI** (MilkdromedaStudios) | Player-facing companion ("Ethan") | Fabric, MC 26.2 | Renders the companion's actual field-of-view; writes a script that presses keys/mouse; opens chests; never teleports | MCP server (Claude/ChatGPT/Grok/Gemini) OR own OpenAI-compatible key OR local model; in-game config | Per-bot trust, personalities, voice; Bedrock via Geyser | Most mature comparable. Still a product, not a library; requires Fabric and a "press keys" body model rather than a native entity capability. |
| **AI Companion (xuanxuan)** | Client mod bridging to Mindcraft | Fabric, 1.21.1 | Spawns an external **Node/mineflayer** process that joins as a real player | Spawns `keys.json` for Mindcraft; GUI configures host/port | Companion entity fallback when external process is off | External runtime dependency; not in-JVM; not a library others embed. |

### 3.1 Positioning

Embodiment Lib's wedge is the combination that none of the above combine:

1. **It is a library, not a character.** No fixed personality, no fixed default companion, no branded "Ethan".
2. **It attaches to any existing entity** via a capability/data-attachment, rather than requiring addon authors to register a new entity or spawn a fake player.
3. **It runs in-JVM.** AgentScope Java is bundled via JarInJar; there is no Node process, no external Python, no second download.
4. **It is provider-neutral and self-hosted.** OpenAI- or Anthropic-compatible endpoints work, including local Ollama/LM Studio; the user supplies the key; nothing is billed through the Embodiment Lib authors.
5. **It ships atomic tools, not a hard-coded skill list.** Addons compose those primitives into task-level tools or write their own.

The closest analogue in spirit is PlayerEngine — but PlayerEngine couples you to its hosted backend, while Embodiment Lib couples you to nothing except your own OpenAI/Anthropic-compatible endpoint.

---

## 4. Core Features & Modules

### 4.1 Embodiment Host Side and Entity Attachment

Embodiment Lib supports two independent host modes. They are **not two halves of one agent**; an addon picks one per use case, and the two modes never share an agent object, a config profile, a session or an API key.

#### 4.1.1 `AgentHostSide`

| Mode | Invoked by | LLM HTTP thread | Tool execution thread | What it can act on | Client holds an agent? |
|---|---|---|---|---|---|
| `SERVER` | A server-side addon (dedicated server or integrated server) | Server-side IO pool | Game-state reads/writes marshalled back onto the server thread | Server-side entities, blocks, containers, inventories | No — client receives only render/appearance events |
| `CLIENT` | A client-side addon (singleplayer client or modded client) | Client-side IO pool | Client thread / render thread as appropriate | Local player simulation, GUI, particles, sounds, client-cached state | Yes, locally and only for that player |

The two modes are fully isolated:

- **Server-side mode.** Config and API key come from the server-side TOML. Sessions, working memory and agent registry live only on the server. Tools operate only on server-authoritative state. If the agent needs to *appear* to players (text, animation, particle), the library fires a narrow, addon-consumed event (e.g. `meta.say`, see §4.5) and never ships the key, session, or conversation history to clients.
- **Client-side mode.** Config and API key come from the client-side TOML (or NeoForge's per-client config screen). Sessions and memory live only under the client's local directory. Tools touch only client-side state. No server attachment is written, and the library does not open a hidden bridge into the server agent. If an addon genuinely needs the server to do something on its behalf, it must send its own network packet and perform its own permission check; Embodiment Lib does not do this automatically.

#### 4.1.2 Server-side attachment

In `SERVER` mode, the brain is a data-attachment on a server-side `LivingEntity`. It owns two identifiers:

- the entity's **agent-type** — a logical kind string the addon registers (e.g. `"village_npc"`, `"quest_giver"`, `"demo_agent"`). This names *what kind of character this is* and is the key used for profile routing (§4.3).
- the entity's **session-id** — a unique per-body identity (in 0.1 the recommended value is the entity's UUID string). This names *which individual* it is and is the key used for session/memory isolation (§4.4).

It also owns:

- a reference to the underlying AgentScope `HarnessAgent`,
- a reference to the bound `Toolkit`,
- lifecycle hooks (entity spawn → agent ready; entity removed → agent closed).

#### 4.1.3 Agent-type vs. session-id

The library deliberately separates **what the character is** from **which body it is on**:

- The **agent-type** names a class of character (e.g. all villagers on the server share type `"village_npc"`). It is used to look up which model profile to use, and addons typically register one system prompt per type.
- The **session-id** names one individual body. In 0.1 the strong recommendation is **one session-id per physical creature instance**, and the entity's UUID string is a sensible default.
- Therefore two villagers of the same type **share the same model profile and system prompt, but have independent conversation histories**. If an addon wants one quest-giver to run on a stronger model than the common villagers, it registers a second agent-type (`"quest_giver"`) and routes that type to a different profile — it does not need to invent per-entity ids for routing.
- Every built-in tool that senses or moves a body (`perceive.self_status`, `perceive.inventory_contents`, `loco.move_to`, `action.mine_block`, etc.) is **bound to the executing entity at the time of the call**, regardless of its type. The LLM never has to pass the entity in; the tool's observation is always that body's observation.
- Sharing one session-id across two physical entities (e.g. a "hive mind" of two linked villagers) is **not** the default. If an addon wants shared personality or shared history across multiple bodies, it should do so at the system-prompt / application layer, not by collapsing two bodies onto one session.

The library does not dictate how the addon decides *when* to invoke the agent (chat message, right-click, schedule, etc.).

### 4.2 Agent Runtime Wrapper

A thin wrapper over `HarnessAgent` that:

- builds the model client from a profile resolved for the current `AgentHostSide` (§4.3),
- attaches the toolkit (built-ins + addon-registered tools) to the executing entity,
- supports both **ReAct step-by-step loops** and **batched tool-call list execution**,
- surfaces tool failures, timeouts and "no valid target" results back to the model as plain-text observations rather than throwing,
- bridges the reactive (Mono) tool calls onto the correct thread for the host side:
  - in `SERVER` mode, world reads and writes are marshalled onto the server thread;
  - in `CLIENT` mode, world reads and writes are marshalled onto the client thread, with rendering-thread work dispatched as the addon requests.

The LLM HTTP call itself always runs on an off-game-thread IO pool, regardless of host side. The addon chooses when to kick off `agent.reply(...)`; the library guarantees that game-state access from inside a tool happens on the right thread.

### 4.3 Model Provider & Profile Routing

Each host side reads its own TOML file (edited through NeoForge's built-in config screen or directly on disk):

- **server-side:** `config/embodimentlib/server.toml` on the logical server,
- **client-side:** `config/embodimentlib/client.toml` on the connecting client.

Each file contains the same shape:

- a **default profile** with `protocol` (`openai` or `anthropic`), `base_url`, `api_key`, `model_name`,
- a `Map<agent_type, profile_name>` that routes specific character types to non-default profiles,
- sensible defaults and clear validation errors at load time.

The two files are **independent**: a server-side profile and a client-side profile do not share keys, do not share routed agent-types, and do not auto-sync. The server never reads `client.toml`; the client never reads `server.toml`.

API keys are read **only on the machine that performs the inference for that host side**. A server-side key is never serialized into network packets or sent to clients. A client-side key never leaves that player's machine and never reaches the server.

### 4.4 Conversation & Session Memory

- Chat history, working memory and a running to-do list are persisted **on the machine that performs inference**, under that host side's own directory.
- Session identity is the **session-id**, not the agent-type. In 0.1 the recommended mapping is **one session-id per physical creature instance** (typically the entity's UUID string), so each body has its own history. Two entities of the same agent-type share a model profile but not a conversation. Cross-entity shared personality is an addon-level concern, not a library one (see §4.1.3).
- Server-side sessions live under the server world's `embodimentlib/sessions/<session-id>/`; client-side sessions live under the client's local config directory. The two trees are never merged.
- The library does not ship a vector store or long-term memory summarization in 0.1.
- Offloaded chunks / unloaded entities are out of scope in 0.1 — the agent simply does not run while the entity is not ticked.

### 4.5 Built-in Tool Catalog

This is the canonical list of tools Embodiment Lib ships with in 0.1. Every tool follows the same contract:

- input is a JSON object defined by the tool itself,
- output is a text block (or error text) fed back to the LLM,
- failures (exception, timeout, no valid target) are returned as **observations**, not thrown out of the agent loop,
- destructive world actions (breaking blocks, placing blocks, attacking entities within reach) post the appropriate vanilla NeoForge events (notably `EntityMobGriefingEvent`) before they execute.

| # | Tool ID | Category | What the LLM can ask it to do | Key inputs | Output |
|---|---|---|---|---|---|
| 1 | `perceive.nearest_block` | Perception | Find the nearest block of a given type within a radius around the entity | `block` (resource location), `radius` | Position and distance of nearest match, or "not found" |
| 2 | `perceive.block_state_at` | Perception | Read the block state at a coordinate (including orientation, waterlogged, power level, etc.) | `pos` | Serialized block state string, or air/void marker |
| 3 | `perceive.inventory_contents` | Perception | List the executing entity's inventory: slot index, item type, count, durability | (none) | Slot-by-slot summary, or `"inventory not supported on this entity"` if the body has none |
| 4 | `perceive.inventory_slot` | Perception | Inspect a single slot in detail (components, enchantments, custom name) | `slot` | Detailed stack description, or `"no inventory"` |
| 5 | `perceive.nearby_entities` | Perception | List living entities within a radius: type, UUID, distance, health | `radius`, optional `type_filter` | Sorted list of visible entities |
| 6 | `perceive.self_status` | Perception | Report the executing entity's own state: position, health, held item, dimension, time of day, nearby threats | (none) | One-line status snapshot |
| 7 | `loco.move_to` | Locomotion | Path to a target position using vanilla pathfinding. Only available on bodies that expose a `PathNavigation` (i.e. `Mob`); non-pathfinding entities return `"pathfinding not supported"` | `x`, `y`, `z`, optional `reach` | `"arrived"`, `"path blocked"`, or distance remaining after timeout |
| 8 | `loco.move_to_entity` | Locomotion | Path toward a moving entity (same pathfinding capability requirement as #7) | `entity_id`, optional `min_distance` | `"in range"`, `"lost target"`, or reason |
| 9 | `loco.jump` | Locomotion | Make the entity jump (used by the model to clear obstacles) | (none) | Always `"jumped"` |
| 10 | `loco.look_at` | Locomotion | Turn the entity's head/body toward a position or entity | `pos` or `entity_id` | `"looking"` |
| 11 | `action.mine_block` | Action (destructive) | Break the block at a position, using the held tool if applicable | `pos` | `"mined"`, `"block unbreakable"`, `"no tool"`, or griefing-denied |
| 12 | `action.place_block` | Action | Place the held block against a clicked face. `hit_pos` is the block being clicked; `face` is the side normal; the resulting block appears at `inside_pos = hit_pos.adjacent(face)` | `hit_pos`, `face`, optional `hand` | `"placed"`, `"no block in hand"`, `"target occupied"`, or `"out of reach"` |
| 13 | `action.use_item` | Action | Right-click/use the held item in the air (e.g. eat, draw bow, throw potion) | `hand` | Result of use (consumed / effect / nothing) |
| 14 | `action.use_item_on` | Action | Right-click/use the held item on a specific block face (same `hit_pos` / `face` semantics as #12) | `hit_pos`, `face`, `hand` | Result of interaction (chest opened, door toggled, crop planted, etc.) |
| 15 | `action.interact_with_block` | Action | Right-click a block without holding a special item (open door, flip lever, trade villager) | `pos` | Result or `"not interactable"` |
| 16 | `action.attack_entity` | Action | Swing at the **specified** target entity. The LLM names the victim; the tool does not pick a "nearest" target on its own | `entity_id` | `"attacked"`, `"out of reach"`, `"target invalid"` |
| 17 | `action.drop_item` | Action | Drop a stack (or part of it) from a slot onto the ground | `slot`, `count` | `"dropped"`, or `"slot empty"` |
| 18 | `action.follow_entity` | Action | Begin persistently following an entity at a given distance until `action.stop_follow` (requires pathfinding) | `entity_id`, `distance` | `"following"`, or `"pathfinding not supported"` |
| 19 | `action.stop_follow` | Action | Stop the current following behavior | (none) | `"stopped"` |
| 20 | `container.inspect` | Container | Read the contents of a container block at `pos` (chest, barrel, shulker, furnace, hopper, …) after range / reach / line-of-sight / griefing checks. Does **not** open a player-style GUI — mobs have no such UI state | `pos` | Per-slot contents, or `"out of reach"` / `"not a container"` |
| 21 | `container.transfer` | Container | Move a stack between the executing entity's inventory and the container at `pos` in one call. No implicit "currently open" container state; the target `pos` is passed every time | `pos`, `from_slot`, `to_slot`, `count` | `"transferred"`, `"not a container"`, `"out of reach"`, `"not enough items"` |
| 22 | `meta.wait` | Meta | Yield the agent loop for a fixed number of ticks, then resume. Implemented as a **non-blocking scheduled delay** — it never parks the server or client thread | `ticks` | `"waited"` |
| 23 | `meta.say` | Meta | Emit a line of text attributed to this agent. The library fires an `AgentSayEvent` (agent-id, executing entity, text) that addons subscribe to for rendering (chat, bubble, action bar, voice). The library itself draws nothing | `text` | `"said"` |

Tools are intentionally **atomic**. Task-level tools (e.g. "collect 10 iron ore", "build a 5×5 floor") are deliberately not in the library — they belong to the addon's domain logic and compose these primitives.

### 4.6 Addon Tool Extension

Addons extend the catalog by:

- writing a class that subclasses the AgentScope `ToolBase` (name, JSON input schema, permission check, async executor),
- registering an instance on the `Toolkit` passed to their `HarnessAgent`.

The library does not impose annotation scanning; registration is explicit and programmatic, matching how the upstream AgentScope examples already work. The library also exposes a permission hook on every built-in tool so addons can veto a call (e.g. "don't let this agent mine in claimed chunks") without forking the tool.

### 4.7 Debugging & Observability

A built-in operator command, `/embodimentlib inspect <entity>` (and aliases under the `embodimentlib` namespace), prints:

- the entity's **agent-type** and **session-id**,
- which profile (and therefore which model/provider) the agent-type currently resolves to,
- the last few tool calls with their inputs and returned observations,
- a truncated preview of the in-memory conversation history for that session-id,
- whether the agent is currently idle, reasoning, or waiting on a tool result.

All runtime activity is also routed through SLF4J under a dedicated logger namespace, so pack authors can grep logs.

### 4.8 Demo Summonable Entity

The mod ships with exactly one example entity:

- it does **not** spawn naturally anywhere,
- it can be summoned by an operator via `/summon embodimentlib:demo_agent` (or an equivalent debug command),
- it has a hard-coded, minimal system prompt and the full built-in tool catalog attached,
- its sole purpose is to demonstrate the end-to-end loop: receive a text prompt, reason, walk, mine, reply.

It is the "live documentation" of the library and is intended for modders and pack authors to play with — not for end players to live with as a companion.

### 4.9 Griefing and Safety Integration

Every built-in tool that alters the world (break, place, extinguish, toggle lever, attack) posts the appropriate NeoForge gameplay events before acting. In particular:

- block breaking and block placing post `EntityMobGriefingEvent`;
- if the event is canceled (e.g. by a gamerule or another mod), the tool returns "griefing denied" to the LLM.

Third-party land-claim mods are not integrated in 0.1; addons that need them should implement veto logic via the §4.6 permission hook.

---

## 5. Task Priority

Priorities are scoped to the 0.1 release and are derived directly from the features in Chapter 4.

### P0 — Must have for 0.1 (the "speak → walk → mine → reply" loop)

- §4.1 Embodied entity capability attachable to a `LivingEntity`.
- §4.2 Agent runtime wrapper, with at least the ReAct loop path.
- §4.3 TOML config: default profile + per-agent-type map, OpenAI and Anthropic protocols, key kept on the host side that performs inference.
- §4.4 Session memory persistence keyed by session-id (UUID per entity).
- §4.5 tools: `perceive.nearest_block`, `perceive.block_state_at`, `perceive.inventory_contents`, `perceive.self_status`, `loco.move_to`, `loco.jump`, `loco.look_at`, `action.mine_block`, `action.use_item`, `action.attack_entity`, `meta.wait`, `meta.say`.
- §4.8 Demo summonable entity.
- §4.7 `/embodimentlib inspect` command.
- §4.9 `EntityMobGriefingEvent` integration on all destructive built-in tools.
- JarInJar packaging of `agentscope-harness` and its required runtime dependencies.

### P1 — Should have for 0.1, but can slip to 0.2 if needed

- §4.5 tools: `perceive.inventory_slot`, `perceive.nearby_entities`, `loco.move_to_entity`, `action.place_block`, `action.use_item_on`, `action.interact_with_block`, `action.drop_item`, `action.follow_entity` / `action.stop_follow`, `container.inspect`, `container.transfer`.
- Batched tool-call list execution path alongside ReAct.
- Addon-side permission veto hook on built-in tools.

### P2 — Later roadmap (not committed to a version)

- Built-in rate limiting / queuing when more than ~10 agents are actively invoking the model.
- Integration hooks for popular land-claim mods (FTB Chunks, GriefPrevention, WorldGuard).
- Vision input (rendered screenshots as multimodal content).
- Persistent long-term memory / summarization beyond the raw chat log.
- Optional MCP server hosting, inspired by Blockpal and Numen.
- Fabric port.

---

## 6. Technical Architecture

This chapter fixes the engineering constraints that the product decisions above assume.

### 6.1 Platform baseline

| Layer | Version / choice |
|---|---|
| Minecraft | 26.1.2 (range `[26.1, 26.2)`) |
| Mod loader | NeoForge `26.1.2.71` (range `[26,)`, loader range `[4,)`) |
| JDK | Bellsoft Liberica **25.0.4.1+** (Java 25, toolchain pinned to 25) |
| Mod id / group | `embodimentlib` / `com.hexagram2021.embodimentlib` |
| Build | Gradle with `net.neoforged.moddev` 2.0.x; runs configured for `client`, `server`, `gameTestServer`, `data` |
| License | Artistic-2.0 |

### 6.2 Embedded agent framework

- Core dependency: `io.agentscope:agentscope-harness:2.0.1` (Apache-2.0, JVM-native, no Python).
- It is bundled into the mod artifact using **NeoForge JarInJar**, so downstream addons and end users do not need to install AgentScope separately.
- The agent entry point used by the wrapper is `HarnessAgent.builder()`; model clients are `OpenAIChatModel` and `AnthropicChatModel`, selected from the resolved profile's `protocol` field.
- Tools extend `io.agentscope.core.tool.ToolBase` and are registered on an `io.agentscope.core.tool.Toolkit` via `registerAgentTool(...)`.
- Tool execution is asynchronous and reactive (returns `Mono<ToolResultBlock>`); the library bridges that onto the appropriate Minecraft scheduler so that game-state reads and writes happen on the game thread while the LLM HTTP call itself stays off it.

### 6.3 Sides, threading and isolation

Embodiment Lib runs on both logical sides, but the two sides are **isolated instances**, not a split of one system.

- **Server-side runtime.** The capability, the server-side agent registry, the server TOML and all server-side sessions live on the logical server (including the integrated server of singleplayer). Tools operate on server-authoritative state. The API key in `server.toml` is read only here and is never placed on a network-synced attachment or sent to clients.
- **Client-side runtime.** When an addon runs in `CLIENT` mode, the client hosts its own agent runtime with its own `client.toml`, its own IO pool and its own session directory. It operates only on client-side state. It does **not** write into server attachments, does **not** automatically talk to the server-side agent, and the client-side key never reaches the server. If a client-side agent needs the server to perform an action on its behalf, the addon sends its own packet and performs its own permission check — the library does not bridge this for you.
- **Threading.** In both modes, LLM HTTP calls run on an off-game-thread IO pool. Tool bodies are scheduled onto the game thread for their host side (server thread in `SERVER` mode, client thread in `CLIENT` mode) so that world reads and writes are safe.
- **What the client receives from the server.** When a server-side agent speaks (`meta.say`) or needs a visual effect, the library sends only narrow presentation events (text, particle, animation type). It never forwards the key, the profile name, the conversation history, or the tool-call log.

The client therefore does *not* "contain no Embodiment Lib logic"; it contains a full client-side runtime that is simply quarantined from the server-side one.

### 6.4 Data and persistence

- Configuration:
  - server-side: `config/embodimentlib/server.toml` (NeoForge config screen editable),
  - client-side: `config/embodimentlib/client.toml`.
- Per-agent session files (history, working memory, to-do list):
  - server-side: under the running world's `embodimentlib/sessions/<session-id>/` on the server,
  - client-side: under the client's local `embodimentlib/sessions/<session-id>/` directory.
- Server-side attachment: the entity stores two short strings — its **agent-type** (used to look up the model profile) and its **session-id** (used to look up its conversation). The heavyweight `HarnessAgent` object is resolved from a runtime registry keyed by `(host-side, session-id)`. In 0.1 the recommended session-id is the entity's UUID string, so attachment and session stay 1:1 per body. Tools always resolve their executing body from the entity the attachment is on — they never infer "who is acting" from the session alone.

### 6.5 Extension surfaces for addons

- Choose the `AgentHostSide` (`SERVER` or `CLIENT`) their agent will run on; the library selects the matching config file, thread scheduler and session directory accordingly.
- In `SERVER` mode, attach the capability to any `LivingEntity`, register an **agent-type** for that kind of character, and assign a unique **session-id** to the body (UUID string recommended).
- Build a `Toolkit`, register built-ins and addon `ToolBase` instances.
- Build a `HarnessAgent` with the resolved profile and toolkit.
- Choose the trigger (chat, command, schedule, GUI) and subscribe to `AgentSayEvent` (and similar presentation events) to render what the agent says.
- Veto built-in tool calls via the exposed permission hook.

### 6.6 Distribution

- Source: GitHub, open under Artistic-2.0.
- Binary: published to Modrinth and CurseForge as a library mod (marked as such; players are expected to install it transitively via an addon).
- Maven artifact: published under the `com.hexagram2021` group to the project's Maven repository, so addon developers can declare it as a `compileOnly` / JarInJar dependency.

---

*End of PRD. Open decisions deferred to implementation: exact TOML key names, the precise JSON schema for each built-in tool, and the command tree under `/embodimentlib`.*
