# BetterModel research report (for the TerraFirmaPlugin entity renderer)

> Note for this repo: examples use the `tfc_` model-id prefix. Our convention is the provisional namespace from `CLAUDE.md`: prefix `tfp_`. Paths under `scratchpad/` refer to the original research environment and are not kept.

Researched on 2026-10-16 against these sources:

- **Source repo:** `https://github.com/toxicity188/BetterModel` at commit `fe60076` (2026-10-15), version `3.5.1` (`gradle.properties: project_version=3.5.1`). Local clone: `scratchpad/src/bettermodel/`. Paths below starting `api/`, `core/` or `nms/` are relative to that clone. `api/...` is short for `api/src/main/java/kr/toxicity/model/api/...`.
- **GitHub wiki:** `https://github.com/toxicity188/BetterModel/wiki`, cloned at `475b4e5` (2026-10-07) into `scratchpad/src/bettermodel-wiki/`. Some wiki pages are **older than the code**. For example, `General-configuration.md` still shows an `item-namespace` key that 3.4.0 removed. Where they disagree, trust the code.
- **Modrinth API** (`https://api.modrinth.com/v2/project/bettermodel/version`) and GitHub issue pages, fetched directly.
- **TFC bbmodels** were analysed with a small script: `scratchpad/scripts/bbcheck.py`, run on `scratchpad/src/tfc/resources/entity_models/*.bbmodel`.

Anything marked **UNVERIFIED** is my inference and was not confirmed in a source.

---

## Table of contents
1. [TL;DR for the skill](#1-tldr-for-the-skill)
2. [Versions, platforms, rendering model, resource pack](#2-versions-platforms-rendering-model-resource-pack)
3. [Data folder, config.yml, commands, reload](#3-data-folder-configyml-commands-reload)
4. [Blockbench requirements and conventions](#4-blockbench-requirements-and-conventions)
5. [Java API](#5-java-api)
6. [Performance](#6-performance)
7. [Auto-installing our models from our jar](#7-auto-installing-our-models-from-our-jar)
8. [Carrier (base) entity strategy](#8-carrier-base-entity-strategy)
9. [TFC bbmodel compatibility audit](#9-tfc-bbmodel-compatibility-audit)
10. [Gotchas (issues / code / changelog)](#10-gotchas)
11. [Recommended integration skeleton](#11-recommended-integration-skeleton)
12. [UNVERIFIED / open questions](#12-unverified--open-questions)

---

## 1. TL;DR for the skill

- **Not a ModelEngine clone internally.** Each Blockbench *group* (bone) becomes one **packet-only `item_display`**. The display shows a `leather_horse_armor` item with an `item_model` component pointing at a generated model. The displays are sent as **passengers of the real base entity** (passenger packet rewritten), so the client interpolates movement. The base entity stays a normal server entity. BetterModel rewrites its *outgoing metadata packets* so that clients see it as invisible with no equipment. Sources: `README.md`, `nms/v1_21_R7/.../NMSImpl.kt` (`hide`, `mountPacket`, `PlayerChannelHandlerImpl.handle`), `nms/.../Functions.kt` (`entityFlag`).
- **Animation runs on BetterModel's own thread pool, not the server tick.** There is a `ScheduledThreadPool` with 2×CPU threads, and each tracker ticks every **25 ms**, which is 2 frames per MC tick. A tracker only ticks while at least one player sees it. Source: `api/tracker/Tracker.java` (`EXECUTOR`, `TRACKER_TICK_INTERVAL = 25`, `start()`).
- **Requirements: Java 25 and MC 1.21.4 – 26.3, on both client and server.**
  - Java 25 is a 3.0.0 breaking change (`changelog/v3/3.0.0.md`, `buildSrc/.../Extensions.kt: JAVA_VERSION = 25`).
  - The MC range comes from `SUPPORTED_VERSIONS` and Modrinth.
  - The Paper jar declares `foliaSupported = true` (`platform/paper/build.gradle.kts`).
- **Getting our models in:**
  - Register them from our jar with `ModelAssetsEvent` (`api/event/ModelAssetsEvent.java`). The official test plugin does exactly this (`test-plugin/.../FightTester.java`).
  - Inject extra pack files (for example variant textures) with `PluginStartReloadEvent.zipper()`.
  - Nothing needs to be copied into `plugins/BetterModel/models`.
- **Built-in auto animations only fire if the names match exactly:** `idle`, `walk`, `spawn`, `idle_fly`, `walk_fly`, plus `damage`, `death` and `jump` driven by events.
  - `walk` is driven by `Mob.navigation.isInProgress()`, so vanilla or Paper pathfinding drives it automatically.
  - TFC animation names look like `animation.deer.walk`. We must **rename them at import time**, or play them ourselves.
- **Tracker state is mostly NOT persisted.** BetterModel saves only `{model id, scaler, rotator, modifier, body-rotator, hide-option, mark-for-spawn}` in the entity PDC key `minecraft:bettermodel_tracker`. On chunk reload, `/bm reload` or a restart, the tracker is re-created **without** tints, part visibility, item swaps or our manual animations.
  - Sources: `api/tracker/TrackerData.java`, `api/bukkit-api/.../BaseBukkitEntity.java:40`, and issue #402.
  - Re-apply our visual state in `CreateEntityTrackerEvent`.
- **No runtime texture swap API.** Issue #387 (open). Workarounds:
  - one model per texture variant, or
  - "remap" bones to a variant model's items with `TrackerUpdateAction.perBone(... itemStack(otherRenderer.groupByTree(name).getItemStack()))`. This is what `RemapScript.kt` does.
  - `tint(rgb)` multiplies the texture colour for each bone.
- **Baby scale:** `tracker.scaler(ModelScaler.entity().multiply(0.5f))` (pattern from `Commands.kt spawn`). `ModelScaler.entity()` reads `LivingEntity#getScale()`, which is the `SCALE` attribute. UNVERIFIED: that attribute probably does not include the vanilla baby age-scale.

---

## 2. Versions, platforms, rendering model, resource pack

### 2.1 Versions and platforms
| Item | Value | Source |
|---|---|---|
| Latest release | 3.5.1 (2026-10-15) | `changelog/3.5.1.md`, Modrinth API |
| MC versions | 1.21.4, 1.21.5 … 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | `buildSrc/src/main/kotlin/Extensions.kt` (`SUPPORTED_VERSIONS`); NMS modules `nms/v1_21_R3 … v26_R3` |
| 1.21.3 and below | dropped in 3.0.0 | `changelog/v3/3.0.0.md` "Deprecation of 1.21.3 Support" |
| Java | **25** (build toolchain and runtime) | `changelog/v3/3.0.0.md` "Java 25 Usage"; `Extensions.kt` |
| Jars | `bettermodel-<v>-paper.jar` (paper/purpur/folia), `-spigot.jar`, `+<mc>-fabric.jar` | Modrinth API; `platform/*/build.gradle.kts` |
| Folia | `foliaSupported = true` | `platform/paper/build.gradle.kts`, `platform/spigot/build.gradle.kts` |
| Tested servers | Paper, Purpur, Spigot, Folia, Leaf, Canvas, Fabric | `README.md` |
| Paper plugin type | paper-plugin.yml with `loader = kr.toxicity.model.paper.BetterModelLoader`; libraries downloaded at runtime (libby) | `platform/paper/build.gradle.kts`, `README.md` |
| Soft deps | MythicMobs, Citizens, SkinsRestorer (load BEFORE), Nexo (OMIT) | `platform/paper/build.gradle.kts` |
| Client compatibility | General models: a 1.21.4+ server works best with 1.21.4+ clients. Older clients through ViaVersion lose features (tint, free cube rotation). Player models need 1.21.4+ on both sides. | wiki `ViaVersion-support-range.md` |
| Mesh elements | require MC client 26.1+ and BM 3.0.0+ | wiki `Create-your-own-model-using-BlockBench.md` |

**Impact on us:**
- Our servers must run **Java 25**, including on 1.21.x. Paper 1.21.4+ runs on 25.
- Our plugin compiles against `bettermodel-bukkit-api`, whose class files target Java 25. Our Gradle toolchain must therefore be JDK 25, or javac will reject the class files (UNVERIFIED for `--release 21` cross-compilation; just use toolchain 25).

### 2.2 How rendering works
- **One `item_display` per bone (group).** "All elements will be rendered as one item display packet per one model bone." (wiki `Create-your-own-model-using-BlockBench.md`). Cubes inside a group with different arbitrary rotations are split into several JSON models and combined into one item model of type `composite`. It is still one display entity. See `api/data/blueprint/BlueprintElement.java` (`buildModernJson`, grouping by `Cube::identifierDegree`) and `core/.../manager/ModelManagerImpl.kt` (`toModernJson`, `"type" to "composite"`).
- **Display item:** the configured `item` (default `leather_horse_armor`), with the `item_model` component set to `<namespace>:<obfuscated>/<bone>`. Its tints are `[{type: custom_model_data, default: 0xFFFFFF}]`, which is how `tint()` works. See `ModelManagerImpl.kt` (`toModernJson`, `toRenderer`). 3.4.0 dropped index-based CustomModelData and uses namespaced item models only (`changelog/v3/3.4.0.md`).
- **Packet-only, per player.**
  - Displays are not world entities. `RenderPipeline` keeps a `playerMap` of players it has spawned for (`api/data/renderer/RenderPipeline.java`).
  - When the server sends `ClientboundAddEntityPacket` for a base entity that has a registry, BetterModel spawns the model for that player one tick later (`NMSImpl.kt` `PlayerChannelHandlerImpl.handle`). So **visibility range = the base entity's vanilla tracking range.**
- **Passenger sync.** Display IDs are injected into the base entity's `ClientboundSetPassengersPacket` (`NMSImpl.mount`, `mountPacket`). The client then moves the displays with the entity. This is the wiki's "Accurate position sync".
- **Hiding the base entity** (`NMSImpl.hide`, `Functions.kt entityFlag`):
  - The outgoing shared-flags byte is rewritten: invisible bit 5 set, fire bit cleared, glowing bit cleared.
  - Equipment packets are replaced with empty equipment.
  - All of this is configurable per tracker through `EntityHideOption(equipment, fire, visibility, glowing)` (`api/tracker/EntityHideOption.java`).
- **Per-player animation.** `AnimationModifier.player(p)` creates a per-player animation state, and that player gets their own transformation packets (`Tracker.BundlerSet.perPlayerViewBundler`, wiki `Per‐player-animation.md`).
- **Hitboxes are real server entities** (`nms/v1_21_R7/.../HitBoxImpl.kt`):
  - A `HitBoxImpl` (an NMS `ArmorStand` subclass, `persist=false`, invisible, silent) is added with `addFreshEntity`.
  - An `Interaction` entity rides it.
  - Clients are told the armor stand is an `ITEM_DISPLAY` (`Functions.kt toFakeAddPacket`). Only the Interaction entity is clickable.
  - Damage is forwarded to the base entity (`hurtServer` → `HitBoxDamagedEvent` → source `hurtServer`). Interaction is forwarded as an interact packet on the base entity (`interact`/`interactAt`).

### 2.3 Resource pack generation
- **When it is built.**
  - Every reload builds the pack: the first load at `ServerLoadEvent(STARTUP)` and every `/bm reload` (`core/bukkit-core/.../BetterModelPlugin.kt`).
  - It does not depend on core shaders (`README.md`).
- **`pack-type`** (`core/src/main/kotlin/kr/toxicity/model/util/Packs.kt`):
  - `zip` writes `<server>/plugins/<build-folder-location>.zip`, by default `plugins/BetterModel/build.zip`. Note that `build-folder-location` is resolved relative to the **plugins folder** (`File(DATA_FOLDER.parent, ...)`), even though the config comment says "relative to the server root".
  - `folder` writes a raw folder and deletes stale files.
  - `none` builds in memory only. That value exists in code (`BetterModelConfig.PackType.NONE`) but is not documented in config.yml.
- **Zip hash cache.** The zip is rewritten only when its SHA-256 changes. The hash lives in `plugins/BetterModel/.cache/zip-hash.txt` (`Packs.kt hashEquals`).
- **Pack contents:**
  - `assets/<namespace>/items/...`, `assets/<namespace>/models/...` and `assets/<namespace>/textures/item/...` (`api/pack/PackNamespace.java`).
  - Names are obfuscated when `pack.use-obfuscation: true` (default) (`PackObfuscator`; 3.1.0 "fully obfuscating resource pack").
  - Plus `pack.mcmeta` and `pack.png`. Overlays are supported (`PackOverlay`).
- **The pack is not hosted.** BetterModel sends no pack to players. We or a pack plugin must serve it (UNVERIFIED: I found no send code; grep for `setResourcePack`/`sendResourcePacks` finds nothing in core).
- **Merging with other pack plugins** (wiki `Merge-with-external-resources.md`, recommendation "**CraftEngine**"):
  - **CraftEngine.** CE's default config already lists `BetterModel/build.zip` under `merge-external-zip-files`. Nothing is needed unless the BM build path changes (wiki `Merge-with-CraftEngine.md`).
  - **Nexo.** Auto-merge. If Nexo is present and `merge-with-external-resources: true`, BM **skips its startup reload** and only loads models when Nexo fires `NexoPrePackGenerateEvent` (`core/bukkit-core/.../compatibility/nexo/NexoCompatibility.kt`). FAQ: set Nexo `Pack.import.model_engine.import_pack: false`, otherwise textures show as podzol (wiki `FAQ.md`).
  - **ItemsAdder:** `build-folder-location: ItemsAdder/contents/BetterModel/resourcepack` with `pack-type: folder`.
  - **Oraxen:** `build-folder-location: oraxen/pack/uploads/bettermodel`.
- **Injecting our own files** into BM's pack: subscribe to `PluginStartReloadEvent` and use `event.zipper().assets().bettermodel().textures()/models()/items().add(name, () -> bytes)`. Example in `test-plugin/.../FightTester.java` `loadItem`.

---

## 3. Data folder, config.yml, commands, reload

### 3.1 Folder layout
```
plugins/BetterModel/
  config.yml
  models/      # GENERAL models (*.bbmodel, *.ajmodel), searched recursively; saved on entities (PDC)
  players/     # PLAYER "limb" models (player-skin animations), never saved on entities
  .cache/zip-hash.txt
plugins/BetterModel/build.zip   # (default; zip pack-type)
```
- The extensions `bbmodel` and `ajmodel` are both accepted, and folders are walked recursively (`dir.fileTrees()`) (`core/.../manager/ModelManagerImpl.kt: modelExtensions`, `importModels`).
- On the first creation of `models/`:
  - if `plugins/ModelEngine/blueprints` exists, it is **copied** in;
  - otherwise `demon_knight.bbmodel` and `blue_wizard.bbmodel` are written as samples. `players/` gets `steve.bbmodel`.
  - Source: `ModelManagerImpl.loadModels`.
- **Model id = file name without extension, passed through `PackUtil.toPackName`.** That lowercases the name and replaces every char outside `[a-z0-9_.]` with its hashCode in hex. So `Horseshoe crab.bbmodel` becomes `horseshoe20crab` (`api/data/ModelAsset.java`, `api/util/PackUtil.java`).
- Duplicate ids produce a warning and the first file in sort order wins (`importModels`).

### 3.2 config.yml (full default, `core/src/main/resources/config.yml`, 3.5.1)
| Key | Default | Meaning (from comments and code) |
|---|---|---|
| `debug.hitbox / exception / pack / tracker` | false | debug logging |
| `indicator.progress_bar` | true | progress bar during pack generation |
| `module.model` | true | general models (and MythicMobs mechanics). If false, `models/` is not loaded at all. |
| `module.player-animation` | true | `players/` limb models |
| `module.player-armor` | true | armor in player models (3.5.1) |
| `pack.use-obfuscation` | true | obfuscate model and texture names |
| `metrics` | true | bStats |
| `sight-trace` | true | view-cone culling. Animation packets are only sent if the model is inside the player's view cone (`api/util/EntityUtil.canSee`). This is **not** a raycast. |
| `merge-with-external-resources` | true | Nexo auto-merge (see 2.3) |
| `item` | `leather_horse_armor` | base item for display models |
| `max-sight` | -1 | max distance for **animation** packets (-1 = server view distance) |
| `min-sight` | 5 | always animate within this distance |
| `namespace` | `bettermodel` | asset namespace |
| `pack-type` | `zip` | `zip` / `folder` (code also has `none`) |
| `build-folder-location` | `BetterModel/build` | relative to the plugins dir (see 2.3) |
| `follow-mob-invisibility` | true | model is invisible when the mob has invisibility |
| `use-purpur-afk` | true | pause for AFK players (Purpur) |
| `version-check` | true | |
| `default-mount-controller` | `walk` | controller for `p_` seats (`walk` / `fly`) |
| `lerp-frame-time` | 3 | ticks between inserted keyframes for lerp |
| `cancel-player-model-inventory` | false | player models only |
| `player-hide-delay` | 3 | player models only |
| `packet-bundling-size` | 16 | packets per bundle (0 = off) |
| `enable-strict-loading` | false | fail models that contain unsupported features |

### 3.3 Commands (`core/bukkit-core/.../command/Commands.kt`, wiki `Command.md`)
Root command `/bettermodel`, with aliases `/bm` and `/model`. Permissions are `bettermodel.<sub>` and default to OP.
- `reload` (aliases `re`, `rl`). Runs **async** and reloads config, models and pack. If a reload is already running it answers "still on reload".
- `spawn <model> [type=HUSK] [scale 0.0625..16] [location]`. Spawns a real entity and runs `model.create(entity, DEFAULT, t -> t.scaler(ModelScaler.entity().multiply(scale)))`.
- `test <model> <animation> [player] [location]`, `disguise <model> [scaling]`, `undisguise [model]`, `play <limb> <animation> [loop_type] [hide]`, `hide|show <model> <player> <entities>`, `version`.

### 3.4 Reload behaviour (`BetterModelPlugin.reload`, `EntityManager.reload`, `EntityTrackerRegistry.reload`)
- **First load** happens in a `ServerLoadEvent(STARTUP)` listener, so it runs after every plugin's `onEnable`. Before it, `BetterModel.model(..)` returns empty. With Nexo plus merge enabled, it is deferred until Nexo's pack generation.
- **On each reload:**
  1. `PluginStartReloadEvent(zipper)` fires.
  2. Every manager reloads. Model maps are **cleared and rebuilt**, firing `ModelAssetsEvent` once per type and `ModelImportedEvent` for each model.
  3. The pack is written.
  4. `PluginEndReloadEvent(result)` fires.
- **Existing trackers are rebuilt on reload.** `EntityTrackerRegistry.reload()` closes every tracker and re-creates it from `TrackerData`. Tints, part visibility, item swaps and running custom animations are **lost** and must be re-applied (see §10).
- **Do not cache `ModelRenderer` across reloads.** Look it up again by name each time.

---

## 4. Blockbench requirements and conventions

### 4.1 Supported formats (wiki `BlockBench-support-range.md`, code)
- **File versions.** Blockbench 4 and 5 files are supported, and so are AnimatedJava *model* files. AnimatedJava *blueprints* are not.
  - The parser reads `meta.format_version`: major ≥5 selects `BLOCKBENCH_5` axis conversion, anything else selects `BLOCKBENCH_LEGACY` (`api/data/raw/ModelMeta.java`).
  - `meta.model_format` is **not checked**. In practice `modded_entity`, `bedrock` and `free` all parse.
  - The wiki tells users to create a "**Generic model**".
- **Elements:**

  | Element | Status |
  |---|---|
  | Cube | ✅ |
  | Locator | ✅ (IK) |
  | Null object | ✅ |
  | Mesh | ⚠️ (UV limits, client 26.1+) |
  | Billboard, Spline, Armature | ❌ |

  An unsupported element type throws an error when the model loads (`ModelData.assertSupported`).
- **Keyframes:** constant, Molang (`query.anim_time`, via the external molang-compiler; some features don't work), rotate in global space, IK, Bezier. Interpolation: linear, smooth (catmull), bezier, step.
- **Loop modes.** The bbmodel `loop` values `once` / `loop` / `hold` map to `AnimationIterator.Type.PLAY_ONCE / LOOP / HOLD_ON_LAST`. The default is PLAY_ONCE (`api/data/raw/ModelAnimation.java loop()`, `api/animation/AnimationIterator.java`). They can be overridden at play time with `AnimationModifier.type(...)`.
- **The animation `override` flag** (Blockbench's "Override" checkbox, read as `ModelAnimation.override`). Derived from code in `api/bone/RenderedBone.addAnimation` and `Tracker.animate`:
  - `override=false` (default): **every** bone receives the animation. Bones with no keyframes get an "empty" iterator that holds them at rest. So the top animation fully controls the pose.
  - `override=true`: bones without keyframes, and without an animated ancestor, are **skipped**. Lower-priority animations keep playing on those bones. This is how to layer for example `eat` on the head over `walk`. The animation's effect script is ignored.
  - It can be forced at runtime with `AnimationModifier.builder().override(true)`.
- **Textures:**
  - Textures must be **embedded** (`source` is a data URI, base64-decoded in `api/data/raw/ModelTexture.java`).
  - The pack texture name is `<model>_<texture>`, unless the texture name starts with `global_`, in which case it is shared between models.
  - Textures whose names start with `-` are template textures and are not exported (wiki `Template-texture.md`).
  - Animated textures are supported (`frame_time`, `frame_interpolate` → `.mcmeta`).
  - Mixing different per-texture UV sizes in one model renders wrongly (issue #371, open). Float UV sizes are a problem (issue #386, open).
- **Cube rotation.** Any rotation works. Cubes are grouped by rotation "identifier", each group gets its own JSON model with a `display.fixed.rotation`, and the leftover rotation is single-axis (`BlueprintElement.buildModernJson`, `Cube.buildJson`). ViaVersion clients older than 1.21.4 lose "free cube rotation".
- **Bone size.** No hard limit. If any cube is more than 16 px from the group origin, that bone's model is scaled down by `max/16` and scaled back up on the display (`BlueprintElement.Group.scale()`). Very large bones lose precision (UNVERIFIED how much).
- **Root-level cubes** (outside any group) are not rendered. A non-Group root element becomes an empty bone (`ModelManagerImpl.toRenderer.parse`: `if (this !is Group) RendererGroup(1.0F, null, ...)`). **Put every cube in a group.**
- `glow_` tag, or a cube's `light_emission`, sets the emission to 15 (`BlueprintElement.Cube.buildJson`, wiki `Glow-part.md`).

### 4.2 Bone name tags (`api/bone/BoneTags.java`, `api/bone/BoneTagRegistry.java`)
**Parsing rules:**
- Names are **lowercased** and split on `_`.
- Leading segments that are known tags become tags, up to N-1 of them. The rest is the bone "name".
- `h_head` → tags {HEAD}, name `head`.
- `tail_q` → no tag (because `tail` is not a tag).

**Lookups:**
- `BonePredicate.name("head")` matches the **tag-stripped** name, case-insensitively (`api/util/function/BonePredicate.java`).
- Equality and maps use the full raw name.

| Tag(s) | Effect |
|---|---|
| `h` | bone follows the base entity's head rotation |
| `hi` | bone **and children** follow head rotation |
| `b`, `ob`, or bone named `hitbox` | creates a hitbox entity sized by the largest cube in the group (hide the cube in BB). Hitboxes follow the bone's animation. |
| `p` | seat: hitbox plus mount with `default-mount-controller` (controllable) |
| `sp` | sub-seat: mountable, not controllable (`MountControllers.NONE`) |
| `tag` / `mtag` / `ptag` | nametag at the bone. `mtag` is visible only when looked at, `ptag` is always visible. Text = base entity's custom name (when custom-name-visible). |
| `glow` | full-bright |
| `li`/`pli`, `ri`/`pri` | shows the entity's off-hand / main-hand item |
| `ph, pra, prfa, pla, plfa, phip, pw, pc, prl, prfl, pll, plfl, cape` | player-skin limbs (players/ models; usable in general models too) |
| bone named `shadow` | creates a fake shadow entity sized by the group's cube (`EntityTracker` constructor). Invisible base entities cast no vanilla shadow. |

Extra tags can be registered via `BoneTag.REGISTRY.addTag(...)` (`BoneTagRegistry.addTag`). UNVERIFIED: whether this is safe to call before BM loads models.

**Matching rules:**
- **Animations are matched to bones by bone name, not UUID.** Animators are filtered by UUID existence but keyed by `animator.name` (`ModelAnimation.toBlueprint`, `AnimationGenerator.generate` with `pointMap.get(g.name())`).
- If we rename a group during preprocessing, we must rename the matching `animations[].animators[uuid].name`.
- Two groups with the same name in one model collide.

### 4.3 Built-in animation names (wiki `Animating-your-own-model.md`, `api/tracker/TrackerBuiltInAnimation.java`, `TrackerExtraAnimation.java`, `core/bukkit-core/.../manager/EntityManager.kt`)
| Name | Played when | Modifier |
|---|---|---|
| `idle` | always, on first tracker start | LOOP, lerp-in 6 |
| `walk` | predicate `onWalk`: `Mob.navigation.isInProgress()`, or a ranged-attack goal running, or a controlling passenger walking, or `damageTick > 0.25`, or any hitbox walking | LOOP, start 6. Speed = walkSpeed (speed/slowness effects) if `damageAnimation` |
| `idle_fly` / `walk_fly` | `Entity#isFlying` (+walk) | LOOP |
| `spawn` | on start | PLAY_ONCE |
| `damage` | `EntityDamageEvent` (MONITOR) | PLAY_ONCE. Also damage tint `0xFF8080` for 10 ticks if `damageTint` |
| `death` | `EntityDeathEvent` | PLAY_ONCE, then the tracker closes |
| `jump` | Paper `EntityJumpEvent` / `PlayerJumpEvent` | PLAY_ONCE |

The walk detection is in `nms/v1_21_R7/.../Functions.kt Entity.isWalking()`.

**Arbitration** (`api/animation/AnimationStateHandler.java`, `api/util/collection/PriorityMap.java`):
- Each bone keeps a priority map of running animations, ordered by higher `priority` first, then the **most recently added**.
- The first one whose `predicate` is true wins. There is **no blending** between animations on one bone, only lerp-in/out transitions (`start`/`end` ticks).
- `walk` is added with a predicate, so it sits above `idle` only while walking.
- Our manual animations, added later, win over both until they end or are stopped.

---

## 5. Java API

### 5.1 Dependency
Source: `README.md`, wiki `API-example.md`.
```kotlin
repositories { mavenCentral() }
dependencies {
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.1") // transitively brings bettermodel-api
}
```
- Snapshots are on `https://maven.pkg.github.com/toxicity188/BetterModel` and need GitHub credentials.
- Packages: `kr.toxicity.model.api.*` (core API) and `kr.toxicity.model.api.bukkit.*` (Bukkit adapter).
- Our plugin.yml / paper-plugin.yml should declare a dependency on `BetterModel` (`depend` in the test plugin: `test-plugin/build.gradle.kts`).

UNVERIFIED: whether 3.5.1 is published to Central yet. A search.maven.org query returned 0 docs, which may just be index lag. The README badge points to Central.

### 5.2 Entry points
| Class | Use | Source |
|---|---|---|
| `BetterModel` (static) | `model(name)`/`modelOrNull`, `limb(name)`, `models()`, `modelKeys()`, `registry(uuid / PlatformEntity / BaseEntity)`, `registryOrNull`, `eventBus()`, `platform()`, `nms()`, `config()` | `api/BetterModel.java` |
| `BetterModelBukkit.platform()` | Bukkit platform: `scheduler()`, `adapter()`, `eventBus()` (`BukkitModelEventBus.subscribe(Plugin, Class, Consumer)`), `reload()`, `addReloadStartHandler`, `addReloadEndHandler` | `api/bukkit-api/.../BetterModelBukkit.java`, `api/BetterModelPlatform.java` |
| `BukkitAdapter.adapt(...)` | Entity/LivingEntity/Player/Location/World/ItemStack → `Platform*` | `api/bukkit-api/.../platform/BukkitAdapter.java` |
| `ModelRenderer` (record) | `create(...)` / `getOrCreate(...)` trackers; `animation(name)` → `BlueprintAnimation` (`length()`, `loop()`); `groupByTree(BoneName)` → `RendererGroup` (`getItemStack()`); `flatten()` | `api/data/renderer/ModelRenderer.java` |
| `Tracker` (sealed) → `EntityTracker`, `DummyTracker`, `PlayerTracker` | live instance | `api/tracker/*.java` |
| `EntityTrackerRegistry` | per-entity container of trackers keyed by model name | `api/tracker/EntityTrackerRegistry.java` |

### 5.3 Creating trackers
```java
// on an existing entity (from api/data/renderer/ModelRenderer.java)
EntityTracker t = BetterModel.model("tfc_cow").map(r ->
    r.getOrCreate(BukkitAdapter.adapt(entity), TrackerModifier.DEFAULT, tr -> {
        // pre-spawn configuration: runs inside the EntityTracker constructor,
        // before CreateEntityTrackerEvent and before the first spawn packets
        tr.scaler(ModelScaler.entity().multiply(0.5f));
        tr.update(TrackerUpdateAction.tint(0xA0522D), BonePredicate.name("body").notSet());
    })).orElse(null);
```
- `create(...)` always builds a new tracker and **replaces** any tracker with the same model name, closing the old one (`EntityTrackerRegistry.putTracker`).
- `getOrCreate(...)` returns the existing one if present. The `preUpdateConsumer` only runs when a new tracker is created.
- One entity can carry several trackers with different model names. `registry.tracker(name)` gets one, `registry.first()` gets the first.
- Creation also immediately `save()`s the PDC data and spawns the model for players already tracking the entity.

`TrackerModifier(sightTrace, damageAnimation, damageTint)`, all true by default (`api/tracker/TrackerModifier.java`). Example: `TrackerModifier.builder().damageTint(false).sightTrace(true).build()`.

**Dummy (no entity):**
- `renderer.create(BukkitAdapter.adapt(location))` returns a `DummyTracker`.
- Move it with `location(newLoc)`.
- You must call `dummy.spawn(player)` for each viewer yourself; it is not auto-tracked (`api/tracker/DummyTracker.java`).
- It plays `spawn` on create.

### 5.4 Animations
Sources: `api/tracker/Tracker.java`, `api/animation/AnimationModifier.java`.
```java
tracker.animate("eat");                                      // DEFAULT modifier (loop type from bbmodel)
tracker.animate("attack", AnimationModifier.DEFAULT_WITH_PLAY_ONCE, () -> { /* on end */ });
tracker.animate("graze", AnimationModifier.builder()
    .start(4)            // lerp-in ticks (default 1)
    .end(4)              // lerp-out ticks back to previous pose (default 0)
    .speed(1.5f)         // or speed(FloatSupplier) – re-evaluated, throttled per tick
    .type(AnimationIterator.Type.LOOP)   // PLAY_ONCE | LOOP | HOLD_ON_LAST
    .priority(10)        // higher wins (3.0.0+)
    .predicate(() -> cow.isEating())     // only active while true (throttled per tick)
    .override(true)      // only affect animated bones (layering)
    .player(BukkitAdapter.adapt(p))      // per-player animation
    .build());
tracker.stopAnimation("graze");
tracker.stopAnimation(bone -> true, "graze", platformPlayerOrNull);
tracker.replace("walk", "run", AnimationModifier.DEFAULT);   // swap running animation, keeps slot
float len = tracker.renderer().animation("attack").map(BlueprintAnimation::length).orElse(0f); // seconds
```
- `animate` returns `false` if the animation name does not exist.
- **Bug:** `AnimationModifier.toBuilder()` drops `priority`. Set it again after `toBuilder()` (`AnimationModifier.java toBuilder`).
- `TrackerAnimation` (`api/tracker/TrackerAnimation.java`) is a reusable recipe (name + condition + modifier factory + callbacks), played with `tracker.animate(TrackerAnimation)`.
- `TrackerBuiltInAnimation.register(name, builder)` adds an animation that auto-plays on **every** tracker's first start. It is global, so prefer per-tracker calls.

### 5.5 Visual updates (`api/tracker/TrackerUpdateAction.java`)
Apply with `tracker.update(action)` or `tracker.update(action, BonePredicate)`:
- `tint(rgb)` / `previousTint()`
- `togglePart(boolean)` shows or hides a bone's display (sheared wool, saddle, horns, gender parts)
- `itemStack(TransformedItemStack)` swaps a bone's rendered item (texture variants via a variant model)
- `enchant(bool)`, `glow(bool)`, `glowColor(rgb)`, `brightness(block, sky)`, `viewRange(float)`, `billboard(PlatformBillboard)`, `moveDuration(ticks)`, `itemMapping()`, `composite(...)`, `perBone(fn)`, `none()`

`BonePredicate` (`api/util/function/BonePredicate.java`):
- `BonePredicate.name("wool").withChildren()` / `.withoutChildren()` / `.notSet()`
- `BonePredicate.tag(BoneTags.HEAD).notSet()`
- `BonePredicate.from(bone -> ...)`

Texture variant via remap, following `core/src/main/kotlin/kr/toxicity/model/script/RemapScript.kt`:
```java
ModelRenderer variant = BetterModel.modelOrNull("tfc_cow_female_brown");
tracker.update(TrackerUpdateAction.perBone(bone -> {
    RendererGroup g = variant.groupByTree(bone.name());
    return g != null ? TrackerUpdateAction.itemStack(g.getItemStack()) : TrackerUpdateAction.none();
}));
```
Bone access:
- `tracker.bone("head")` returns a `RenderedBone`.
- `bone.hitBoxPosition()` gives a world-relative offset; add it to the entity location.
- Rotation and position modifiers: `bone.addGlobalRotModifier(pred, quat -> ...)`, `addPositionModifier`.
- `RenderedBone.runningAnimation()`.

### 5.6 Scaling
Source: `api/tracker/ModelScaler.java`.
- `tracker.scaler(ModelScaler.value(0.5f))`, `ModelScaler.entity()` (default), `ModelScaler.entity().multiply(0.5f)`, `ModelScaler.composite(...)`.
- The scaler is re-evaluated (throttled per tick), so a growing animal can use a custom scaler.
- **Only built-in scalers serialise.** A custom one needs `ModelScaler.DESERIALIZER.addScaler(name, builder)`, or it falls back to default after reload (UNVERIFIED that lambdas are not serialisable, but `ModelScaler` is a `sealed` interface, so we must use `value`/`entity`/`composite`).
- The scaler **is saved** in PDC.
- Hitbox entities scale with `bone.hitBoxScale()`. The vanilla base entity's own hitbox does not scale; use the `generic.scale` attribute if needed.

### 5.7 Hitboxes, damage, interaction
Sources: `EntityTracker`, `api/nms/HitBox.java`, `api/nms/HitBoxListener.java`, `HitBoxImpl.kt`.
- **Automatic creation.** Hitboxes are created automatically for bones named `hitbox`, tagged `b_`/`ob_`, or mountable (`p_`/`sp_`). Creation is scheduled on the entity's region via `entity.platform().task`.
- **No hitbox bone means no BM hitbox.** The base entity's vanilla hitbox handles hits and clicks, because the client still knows the base entity with its real type and dimensions; it is only flagged invisible.
- **Listening:**
  - `tracker.listenHitBox(HitBoxDamagedEvent.class, e -> ...)` or `listenHitBox((bone, builder) -> builder.damage(...).interactAt(...).mount(...).dismount(...))`.
  - Events: `HitBoxCreateEvent`, `HitBoxDamagedEvent` (cancellable, settable damage), `HitBoxInteractAtEvent` (cancellable), `HitBoxMountEvent`, `HitBoxDismountEvent`, `HitBoxRemoveEvent`.
  - `HitBoxInteractEvent` was removed in 3.0.2, so `Tracker.listenHitBox` Javadoc mentioning it is stale.
- **Bukkit side.** The hitbox Bukkit entity implements `HitBox` (a `CraftArmorStand`). In Bukkit events, `if (e instanceof HitBox hb) hb.source()` gives the owning entity (wiki `API-example.md`).
- Damage to a hitbox is forwarded via the base entity's `hurtServer`, so `EntityDamageEvent` fires on the base entity too.

### 5.8 Mounting (horses, camels)
Sources: `MountController`, `MountControllers`, `HitBoxImpl.mount/mountControl`, `EntityManager`.
- **How BM-native mounting works.** A `p_` bone becomes a hitbox, and a right-click with the main hand calls `hitBox.mount(player)`. This happens in a **MONITOR** `PlayerInteractEntityEvent` handler in `EntityManager` that skips cancelled events. The player rides the Interaction entity.
- **Steering.** The base entity is moved by `MountController.move(...)` using the player's input and the base entity's `MOVEMENT_SPEED` attribute. While mounted, AI is turned off (`isNoAi`).
- **Controllers:**
  - `WALK`, `FLY`, `NONE` (seat without control), `INVALID` (no mount).
  - Custom controllers via `controller.modifier().canJump(false).canDismountBySelf(true)....build()`.
  - Set one with `hitBox.mountController(c)`.
  - Bug: `Modifier` does not copy `canBeDamagedByRider` from the source (`MountController.java`).
- **Blocking a mount.** Cancel `PlayerInteractEntityEvent` where `rightClicked instanceof HitBox` at a priority below MONITOR. Or set `MountControllers.INVALID`.
- **Recommendation for TFC horses and camels.** Do **not** use `p_` bones. Use a vanilla `Horse`/`Camel`/`Donkey` as carrier and let vanilla riding, saddles, taming and inventory work.
  - The model displays are already the carrier's passengers. BM re-injects real passengers into the passenger packet: `mountPacket` keeps non-registry passengers.
  - `walk` animates while ridden because `isWalking()` checks `controllingPassenger`.
  - UNVERIFIED: whether the rider's seat height lines up with the model; tune it with a scale or attribute.
- Events: `MountModelEvent` / `DismountModelEvent`. `registry.hasPassenger()`, `registry.mountedHitBox()`.

### 5.9 Nametags
- Add a `tag_`/`mtag_`/`ptag_` bone. Its text is the base entity's custom name when custom-name-visible (`EntityTracker` constructor, `BaseEntityImpl.customName`).
- `tracker.createNametag(predicate, (bone, tag) -> tag.component(...))`.
- UNVERIFIED: whether the vanilla nameplate of the invisible base entity is also shown (BM only rewrites the shared-flags byte).

### 5.10 Events
- Subscribe with `BetterModelBukkit.platform().eventBus().subscribe(plugin, Class, Consumer)`.
- All `ModelEvent`s are also bridged into one Bukkit event, `BetterModelBukkitEvent`, with `is(Class)` / `as(Class)` (`api/bukkit-api/.../event/BetterModelBukkitEvent.java`, `BukkitModelEventBusImpl.kt`).
- List (`api/event/`):
  - Reload and assets: `PluginStartReloadEvent`, `PluginEndReloadEvent`, `ModelAssetsEvent`, `ModelImportedEvent`.
  - Tracker lifecycle: `CreateEntityTrackerEvent`, `CreateDummyTrackerEvent`, `CloseTrackerEvent(tracker, reason)`.
  - Per-player visibility: `ModelSpawnAtPlayerEvent` (cancellable), `ModelDespawnAtPlayerEvent`, `PlayerHideTrackerEvent`, `PlayerShowTrackerEvent`.
  - Mounting: `MountModelEvent`, `DismountModelEvent`.
  - Per-player animation: `PlayerPerAnimationStartEvent` / `End`.
  - Script signal: `AnimationSignalEvent`.
  - Player skin: `CreatePlayerSkinEvent`, `RemovePlayerSkinEvent`.
  - Hitbox events.

**Event bus gotchas** (`core/src/main/kotlin/kr/toxicity/model/BetterModelEventBusImpl.kt`):
- `subscribe` returns `ModelEventListener.NONE` and does nothing if our plugin is not enabled yet. **Subscribe in `onEnable`, not `onLoad`.**
- If a subscriber throws, **all of our plugin's listeners for that event class are removed** (`removeIf ... isFailure`). Wrap the handler bodies in try/catch.
- Events fire on whatever thread triggered them: the reload thread (async for `/bm reload`), the tracker worker threads, or region threads.

### 5.11 Lifecycle, close, persistence
- **Closing:**
  - `tracker.close()` stops scheduling, despawns displays and removes the hitboxes.
  - `registry.close()` closes all of an entity's trackers. Its default `REMOVE` reason **also deletes the PDC data**.
  - `registry.remove(modelName)` removes one tracker.
- **Persistence:**
  - GENERAL-model trackers are serialised to PDC key `NamespacedKey.fromString("bettermodel_tracker")`, which is `minecraft:bettermodel_tracker`, as JSON (`BaseBukkitEntity.TRACKING_ID`).
  - On `EntityAddToWorldEvent` (Paper) or `EntitySpawnEvent` (Spigot), `BetterModel.registryOrNull(entity)` sees the PDC and **re-creates the trackers automatically** (`EntityManager.PaperListener`, `EntityTrackerRegistry.registry(BaseEntity)`).
  - On chunk unload, death or remove, trackers are `despawn`ed. They are closed with `DESPAWN`, which keeps the PDC, once the entity is dead.
  - `CloseReason.PLUGIN_DISABLE`/`DESPAWN` keep the data; `REMOVE` deletes it.
- **Re-applying our state.** What persists is only what is in `TrackerData`. Everything else must be re-applied in a `CreateEntityTrackerEvent` subscriber that reads **our own** PDC (variant, sheared, gender, age).

### 5.12 Threading and Folia
- **Tracker ticking** runs on BM's own executor. `animate`, `stopAnimation`, `update` and `tint` use concurrent structures and are designed to be called from any thread. `task(Runnable)` queues work onto the tracker tick.
- **Creation:**
  - `EntityTrackerRegistry.initialLoad()` only loads saved PDC data when `adapter.isRegionSafe()` is true. On Folia that means `Bukkit.isPrimaryThread()` (`BukkitAdapter.isRegionSafe`).
  - Hitbox creation is posted through the region scheduler.
  - **On Folia, create trackers from the entity's owning thread**, for example `entity.getScheduler().run(plugin, t -> ..., null)` or inside an event on that entity. PDC writes in `save()` run on the tick thread or are re-posted.
- The BM scheduler on Paper/Folia uses `RegionScheduler` and `AsyncScheduler` (`core/bukkit-core/.../scheduler/PaperScheduler.kt`).
- 3.5.1 fixed Folia region violations in hitbox hide/show (#450).

---

## 6. Performance

- **Network.**
  - Packets per entity ≈ the number of bones (displays) at spawn, plus transformation metadata for bones that changed, sent every 25 ms frame while animating.
  - Unchanged bones send nothing ("dirty" updates). Idle produces less than half the traffic of walk.
  - The author's measurements: BM used about 32 % of ModelEngine's `set_entity_data` bytes for 5 models over 90 s (wiki `Report-about-network-cost...md`).
  - Packets are bundled (`packet-bundling-size: 16`).
- **CPU.**
  - Ticking happens off the main thread on a pool of 2×CPU threads.
  - **A tracker with no viewers shuts its scheduler down** (`Tracker.start` lambda: `if (playerCount()==0 && !forRemoval) shutdown()`), so unseen animals cost close to nothing.
  - Animation keyframes are precomputed at import, with lerp frames inserted every `lerp-frame-time` ticks.
- **Culling.**
  - Spawn and visibility follow the base entity's **vanilla entity-tracking range**. Configure it in `spigot.yml` `entity-tracking-range.animals`/`monsters`/`misc`, or Paper's equivalent (UNVERIFIED wording).
  - Animation packets are further limited by `max-sight`/`min-sight` and the view-cone `sight-trace` (per tracker via `TrackerModifier.sightTrace`).
  - `TrackerUpdateAction.viewRange(f)` sets the display entity view range.
- **Server entities.**
  - Each hitbox bone adds **2 real entities** (ArmorStand subclass + Interaction), ticking on the region. For ~70 animal types × many instances, **avoid hitbox bones** and rely on the vanilla carrier hitbox.
  - Seats (`p_`) also create hitboxes.
- **Keep bone counts low.** Every group is one display entity per viewer. TFC models have 4 to 21 groups (§9). The Peacock has 21.

UNVERIFIED: no official recommended entity limits. ModelEngine-style "N models per chunk" guidance was not found.

---

## 7. Auto-installing our models from our jar

**Recommended: `ModelAssetsEvent`.** No files are copied, and our assets win over nothing. Pattern from `test-plugin/src/main/java/kr/toxicity/model/test/FightTester.java`:
```java
@Override public void onEnable() {
    var bus = BetterModelBukkit.platform().eventBus();
    bus.subscribe(this, ModelAssetsEvent.class, e -> {
        if (e.type() != ModelRenderer.Type.GENERAL) return;
        for (String id : OUR_MODEL_IDS) {                 // ids must match [a-z0-9_.]+ (PackUtil.assertPackName)
            byte[] json = preprocess(readResource("bettermodel/" + id + ".bbmodel")); // rename anims/bones, etc.
            e.addAsset(ModelAsset.of("tfc_" + id, json)); // throws IllegalArgumentException if name already exists
        }
    });
    bus.subscribe(this, PluginStartReloadEvent.class, e -> {
        // optional: extra textures/items into BM's pack
        // e.zipper().assets().bettermodel().textures().add("tfc_extra.png", () -> bytes);
    });
    bus.subscribe(this, PluginEndReloadEvent.class, e -> {
        if (e.result() instanceof BetterModelPlatform.ReloadResult.Success) {
            // models are (re)available now → (re)ensure trackers for our loaded entities; notify pack plugin
        }
    });
}
```
**Notes:**
- `ModelAsset.of(name, StreamSupplier)` and `of(name, byte[])` exist. Suppliers are invoked in parallel on worker threads (`ModelManagerImpl.importModels` → `forEachParallel`).
- `ModelAssetsEvent` is fired on every reload, so our models survive `/bm reload` with no extra work.
- Use a unique prefix (`tfc_`). A server admin's file with the same name in `models/` makes `addAsset` **throw**, and per §5.10 the exception removes our subscriber. Catch it.
- **Alternative:** copy files into `plugins/BetterModel/models/tfc/` at startup and call `BetterModelBukkit.platform().reload()`. A reload is a heavy, full pack rebuild, so avoid calling it more than once. Copying before BM's first startup reload (our `onEnable`) needs no reload call. This is less clean (it gets edited by admins and leaves stale files) but works.
- **Pack delivery.** Keep `pack-type: zip`. CraftEngine merges `BetterModel/build.zip` by default (wiki `Merge-with-CraftEngine.md`). After `PluginEndReloadEvent`, trigger CraftEngine's pack reload if BM rebuilt after CE generated (UNVERIFIED API, see §12).

---

## 8. Carrier (base) entity strategy

**Facts from the code:**
- The model attaches to **any** entity. `spawn` defaults to `HUSK` (`Commands.kt`).
- BM hides the carrier **only client-side**, by rewriting packets per player (§2.2). On the server it remains a normal mob: AI, pathfinding (Paper `MobGoals` / `Pathfinder` work unchanged), sounds, drops, breeding, leashing, riding.
- `walk` animation = `navigation.isInProgress()`. So any Paper Pathfinder or MobGoals-driven movement animates automatically, while velocity-only movement (`setVelocity`) does **not** (`Functions.kt isWalking`).
- Head bones (`h_`/`hi_`) follow `yHeadRot`. The body follows `yBodyRot` (`EntityBodyRotator`, `BaseEntityImpl.bodyYaw/headYaw`).
- Model position = carrier position, with the root at the carrier's feet. Displays are offset by `-passengerPosition` because they ride the entity (`EntityTracker` constructor `pipeline.defaultPosition`).

**Recommendations (partly UNVERIFIED design advice):**
- **Pick a carrier per role.** Choose a vanilla type whose **hitbox size, movement physics, sounds and AI** are closest:
  - `Cow`/`Sheep`/`Pig`/`Goat` for livestock;
  - `Wolf` for predators and pets;
  - `Horse`/`Donkey`/`Mule`/`Llama`/`Camel` for mounts;
  - `Cod`/`Salmon`/`TropicalFish` or `Dolphin` for fish (water pathing);
  - `Chicken` for small birds.
- **Clearing goals.** Our own AI can call `Bukkit.getMobGoals().removeAllGoals(mob)` and add custom `Goal`s. BM is unaffected.
- **Silence vanilla sounds.** Use `setSilent(true)` and play TFC sounds ourselves, because clients still hear the carrier's sounds. Particles such as hearts or death poof still show.
- **Babies.** Use `Ageable#setBaby()` on the carrier (so the hitbox halves) plus `ModelScaler.entity().multiply(0.5f)`, or the `generic.scale` attribute (read by `ModelScaler.entity()`).
- **`EntityHideOption`.**
  - Keep the `DEFAULT` (hide equipment, fire, invisible, glow).
  - **Set `equipment=false`** if you want vanilla armor or saddles visible. Usually keep it true and model the saddle as a bone toggled with `togglePart`.
  - Set it per tracker with `tracker.hideOption(...)` in the pre-update consumer. It is persisted.
- **Prevent vanilla look-through.** The carrier's vanilla nametag and leash rendering still exist (UNVERIFIED for nametags).

---

## 9. TFC bbmodel compatibility audit

These are the files in `scratchpad/src/tfc/resources/entity_models` (44 checked by script `scratchpad/scripts/bbcheck.py`).

**Fine as they are:**
- All have **embedded textures**, groups in the outliner, and no root-level cubes.
- No mesh elements.
- No bone names collide with BM tags. Bones such as `leg_q`/`tail_q` start with a non-tag word.
- No duplicate bone names.
- No bone is named `hitbox` or `shadow`.

**Formats:**
- `meta.format_version` is 4.0–4.10 or 5.0, which is fine (axis conversion picks LEGACY vs BB5).
- `model_format` is `modded_entity` or `bedrock`. It is ignored by BM, so this is fine.

**Animation names do not match BM built-ins.** For example `animation.deer.walk`, `animation.lion.run`, `animation.model.walk`, `animation_walk_mongoose`, `crawl`, `swim`. Some have only `once` loops (`Caribou: animation.caribou.walk once`).

**Many models have no animations at all:** Bluegill, Jellyfish, Lobster, Manatee, Orca, the windmill parts, etc. In the TFC mod, entity animation is mostly **procedural Java code** in the model classes (UNVERIFIED for every case). For BM we must:
- author keyframe animations (`walk`/`idle`/`swim`/...) into the bbmodels, or
- generate them at preprocessing time (Molang keyframes like `math.sin(query.anim_time*...)` are supported, wiki `Math-animation.md`).

**Preprocessing plan:**
- Rename animations to BM names (`walk`, `idle`, plus our own like `run`, `sleep`, `attack`, `eat`).
- Set loop types.
- Optionally rename `head` → `h_head` so it follows look direction. Also rename the matching animators (§4.2).

**Free cube rotations** (non-22.5°, or multi-axis) are present in many models (Caribou 50, Moose 38). BM supports them by splitting the bone into several sub-models. That means more JSON, but still one display per bone. Older (<1.21.4) ViaVersion clients will render them wrong.

**Bone counts** range from 2 to 21 (Peacock 21, Moose 16, Bongo 16). This is the per-entity display count.

**Variant parts** already exist as bones, which suits `togglePart`. For example `wool_body` / `wool_shey` in `Bactrian_camel.bbmodel`, and `manebone` in the lion.

**Texture variants** (male/female, colours) are separate PNGs in TFC, not in the bbmodel.
- Option A: build one variant bbmodel per texture at preprocessing, by swapping the embedded `textures[].source`, and `remap` or create per variant (§5.5).
- Option B: if variants are colour-only, use `tint`. Tint multiplies, so the base texture should be greyscale.

---

## 10. Gotchas

1. **Java 25 is required.** This applies to the server runtime on 1.21.x too, and to our compile toolchain (`changelog/v3/3.0.0.md`).
2. **Models are not available until BM's first reload**, which runs at `ServerLoadEvent(STARTUP)` or at Nexo pack generation (`BetterModelPlugin.kt`, `NexoCompatibility.kt`). Wait for `PluginEndReloadEvent` before spawning modelled entities at startup.
3. **Reload wipes visual state.** `/bm reload` closes and recreates every tracker from `TrackerData`, so tints, part visibility, item swaps and running animations are lost (`EntityTrackerRegistry.reload`). The same happens on restart or chunk reload. Issue #402 ("bone visibility reset upon plugin reload or server restart", open, no maintainer reply). → Re-apply in `CreateEntityTrackerEvent`.
4. **Entities persist with BM data in PDC.** If our plugin removes a model, call `registry.close()` (REMOVE), which clears the PDC. Otherwise BM re-creates it on chunk load. If the model id no longer exists, it is silently skipped.
5. **Event bus traps:** you must subscribe while enabled, and an exception in a handler unsubscribes the whole plugin from that event class (§5.10).
6. **`ModelAsset.of(name, ...)` requires `[a-z0-9_.]` names** and throws otherwise (`PackUtil.assertPackName`). A duplicate name in `addAsset` throws.
7. **Built-in animations need exact names.** `walk` is navigation-based, not velocity-based.
8. **No animation blending.** One active animation per bone, chosen by priority and recency. Use `override(true)` for partial-body layering.
9. **`AnimationModifier.toBuilder()` loses `priority`.**
10. **There is no texture swap API** (issue #387, open, "help wanted"). Use variant models plus remap/create, or tint.
11. **Hitbox network kick on 26.1.2.** Issue #445 (open) reports "Network Protocol Error near hitboxes: armor-stand metadata applied to ItemDisplay" with ProtocolLib and ViaVersion present. The workaround is to remove hitbox bones. Another reason to avoid hitbox bones.
12. **Child bone desync.** Issue #403 (open): child bones visually lag behind their parents during some animations. Keep hierarchies shallow where possible.
13. **Translucency** works on cubes but not on mesh (#452). Mixed per-texture UV sizes render wrong (#371). Float UV sizes are a problem (#386).
14. **`build-folder-location` is relative to the plugins folder**, despite the comment (`Packs.kt`).
15. **Hitbox bones double the server entity count** (ArmorStand + Interaction per hitbox bone).
16. **Mount via `p_` bones happens on any main-hand right-click** unless the event is cancelled before MONITOR. TFC taming and saddle rules would need cancellation, so prefer vanilla mount carriers.
17. **Default `damageTint`** (red flash) and `damage` animation are on (`TrackerModifier.DEFAULT`). Disable them per tracker if TFC wants its own.
18. **Death closes the tracker** after the `death` animation, or immediately if there is none. Do not re-create on a dead entity.
19. **The wiki is partly stale.** It shows the `item-namespace` key and `HitBoxInteractEvent`, which no longer exist. Trust the 3.5.x code.
20. **The base entity is still client-visible data**: sounds, particles, the nameplate (UNVERIFIED) and the vanilla client hitbox.

---

## 11. Recommended integration skeleton

This combines the documented API calls above. Method names are verified; the overall design is a recommendation.
```java
public final class BmBridge {
  private final Plugin plugin;
  BmBridge(Plugin plugin) { this.plugin = plugin; }

  void enable() {                                   // call from onEnable (BetterModel is a hard depend)
    var bus = BetterModelBukkit.platform().eventBus();
    bus.subscribe(plugin, ModelAssetsEvent.class, e -> { try {
        if (e.type() == ModelRenderer.Type.GENERAL) registerOurModels(e);
      } catch (Throwable t) { plugin.getLogger().severe("BM asset registration failed: " + t); } });
    bus.subscribe(plugin, CreateEntityTrackerEvent.class, e -> { try {
        reapplyState(e.tracker());                  // tint/togglePart/remap/scale from OUR PDC
      } catch (Throwable t) { plugin.getLogger().severe(t.toString()); } });
    bus.subscribe(plugin, PluginEndReloadEvent.class, e -> { /* re-ensure trackers; refresh pack plugin */ });
  }

  void attach(LivingEntity mob, String modelId, boolean baby) { // run on mob's owning thread (Folia)
    BetterModel.model(modelId).ifPresent(r -> r.getOrCreate(BukkitAdapter.adapt(mob),
        TrackerModifier.builder().damageTint(true).build(),
        t -> t.scaler(baby ? ModelScaler.entity().multiply(0.5f) : ModelScaler.entity())));
  }

  void play(Entity mob, String anim, boolean once) {
    BetterModel.registry(BukkitAdapter.adapt(mob)).map(EntityTrackerRegistry::first)
      .ifPresent(t -> t.animate(anim, once ? AnimationModifier.DEFAULT_WITH_PLAY_ONCE : AnimationModifier.DEFAULT));
  }

  void sheared(Entity mob, boolean sheared) {
    BetterModel.registry(BukkitAdapter.adapt(mob)).map(EntityTrackerRegistry::first)
      .ifPresent(t -> t.update(TrackerUpdateAction.togglePart(!sheared), BonePredicate.name("wool_body").withChildren()));
  }

  void detach(Entity mob) { BetterModel.registry(BukkitAdapter.adapt(mob)).ifPresent(EntityTrackerRegistry::close); }
}
```
Note: `BetterModel.registry(PlatformEntity)` calls `nms().adapt(entity)`, and the static `registry(...)` overloads return `Optional<EntityTrackerRegistry>` (`api/BetterModel.java`).

---

## 12. UNVERIFIED / open questions

- Is `bettermodel-bukkit-api:3.5.1` already on Maven Central? A search.maven.org query returned 0. Check `https://repo1.maven.org/maven2/io/github/toxicity188/bettermodel-bukkit-api/` before pinning.
- Can a Java-21-targeted plugin compile against the Java-25 API jars with `--release 21`? Assumed no; use toolchain 25.
- Does vanilla `LivingEntity#getScale()` include the baby age-scale? I believe it does not (`getAgeScale()` is separate), so `ModelScaler.entity()` would not shrink babies automatically. Test it.
- On Folia, does `Bukkit.isPrimaryThread()` return true on every region thread? This decides whether `initialLoad` runs when a tracker is created from an entity scheduler task.
- Is the vanilla custom-name nameplate of the invisible carrier hidden by BM? Only the shared-flags byte is rewritten.
- **CraftEngine:**
  - Exact config key and default for merging `BetterModel/build.zip` (from BM's wiki only; the CE wiki pages fetched didn't show it).
  - Whether CE re-merges automatically after a BM rebuild, or needs `/ce reload` or an API call.
  - Generation order at startup: CE pack vs BM's `ServerLoadEvent` reload.
- Spawn-chunk entities loaded **before** BM's first reload: their registry is created while no models exist. It is unclear whether trackers are restored without a chunk reload. Mitigate by re-ensuring our entities after `PluginEndReloadEvent`.
- Rider seat height and alignment when a vanilla Horse/Camel is the carrier with a BM model.
- Performance numbers for ~hundreds of modelled animals are not published. Benchmark with `debug.tracker` and a traffic profiler such as TrafficProfiler (wiki report).
- `BoneTag.REGISTRY.addTag(...)`: safe timing for custom tags, and whether tags affect already-loaded models (they would need a reload).
- No sound/particle keyframe support was verified beyond the "effects" script channel (`ModelAnimation.toScript`; wiki `Animation-Script.md`: `enchant`, `remap`, `partvis`, `tint`, `changepart`, `brightness`, `mm:<skill>`). Blockbench sound keyframes are probably ignored.
- Exact client behaviour for item models whose bone scale is >16 px from the origin (precision loss).
