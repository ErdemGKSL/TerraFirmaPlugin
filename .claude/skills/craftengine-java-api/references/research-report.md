# CraftEngine: Java API, block entities, storage, worldgen and threading (deep dive)

> Note for this repo: researched at CraftEngine commit b3079c5 (v26.9.2 source) against published API jars 26.9.1, 2026-10-16. Examples say `tfc:`; our provisional namespace is `tfp:` (see `CLAUDE.md`). Paths like `scratchpad/...`/`ce-dev` refer to the original research environment and are not kept.

Researched 2026-10-16 for the TerraFirmaPlugin (EUPL-1.2, Paper + Folia, MC 1.21.x / 26.x, Java 25).

**Sources**
- CE repo `github.com/Xiao-MoMi/craft-engine`, branch `main` at `b3079c5` (2026-09-29, `project_version=26.9.2`). Local clone: `scratchpad/src/ce`. Paths below are relative to the repo root. Common prefixes are abbreviated:
  - `core/…` = `core/src/main/java/net/momirealms/craftengine/core/…`
  - `bukkit/…` = `bukkit/src/main/java/net/momirealms/craftengine/bukkit/…`
  - `loader/…` = `bukkit/paper-loader/src/main/java/net/momirealms/craftengine/bukkit/plugin/…`
- CE branch `dev` at `982de0b` (2026-10-16, `project_version=26.10-SNAPSHOT`). Shallow read-only clone: `scratchpad/src/ce-dev`, used only for the APIs the wiki already documents.
- Published jars from `https://repo.momirealms.net/releases/`, inspected with `javap` / `unzip -l` only and stored in `scratchpad/jars/`: `craft-engine-core-26.9.1.jar`, `craft-engine-bukkit-26.9.1.jar`, `craft-engine-bukkit-proxy-26.9.1.jar`, `craft-engine-nms-helper-mojmap-1.0.240.jar`.
- Wiki: `https://xiao-momi.github.io/craft-engine-wiki/api/`, `/api/block_behavior/`, `/api/block_entity/`, `/api/block_entity_ticker/` (all "last updated Sep 29, 2026"). There are **no** wiki pages `api/events` or `api/item_behavior` (both 404).
- Prior general report: `scratchpad/research/craftengine.md` (§8, §9). This document supersedes it where they disagree.

**Markers:** **[SRC]** read in source · **[JAR]** confirmed by javap on the published jar · **[WIKI]** wiki page · **[INF]** my inference from source (reasoned, not run) · **[MEM]** from my knowledge of Paper/CraftBukkit internals, not re-read here · **UNVERIFIED** not confirmed.

---

## Table of contents
0. [TL;DR / decisions for TFC](#0-tldr--decisions-for-tfc)
1. [Gradle coordinates, artifacts, relocations](#1-gradle-coordinates-artifacts-relocations)
2. [Public API (`bukkit/api`): classes and events](#2-public-api-bukkitapi-classes-and-events)
3. [Writing a custom BlockBehavior](#3-writing-a-custom-blockbehavior)
4. [Block entities: controllers, persistence, tickers](#4-block-entities-controllers-persistence-tickers)
5. [Item behaviors](#5-item-behaviors)
6. [Other registries and custom config parsers](#6-other-registries-and-custom-config-parsers)
7. [Startup, lifecycle and reload](#7-startup-lifecycle-and-reload)
8. [Storage and chunk lifecycle; where TFC should keep per-block data](#8-storage-and-chunk-lifecycle-where-tfc-should-keep-per-block-data)
9. [Worldgen integration](#9-worldgen-integration)
10. [Threading and Folia](#10-threading-and-folia)
11. [Pitfalls](#11-pitfalls)
12. [Complete example: TFC crop with planted timestamp (ours)](#12-complete-example-tfc-crop-with-planted-timestamp-ours)
13. [UNVERIFIED / open questions](#13-unverified--open-questions)

---

## 0. TL;DR / decisions for TFC

1. **Dependency:** `compileOnly("net.momirealms:craft-engine-core:26.9.1")` and `compileOnly("net.momirealms:craft-engine-bukkit:26.9.1")`. 26.9.1 is the newest version published; 26.9.2 returns 404. The NBT class in every signature is the **relocated** `net.momirealms.craftengine.libraries.nbt.CompoundTag`. The wiki's `net.momirealms.sparrow.nbt` will not compile against the jar. [JAR]
2. **Startup:** CE is `load: STARTUP` with a Paper bootstrapper (`bukkit/paper-loader/build.gradle.kts` L46–55). TFC worlds are the default worlds and are generated during startup, so TFC requires:
   - (a) `misc.delay-configuration-load: false` in CE's `config.yml`. CE reads that file in its **bootstrap**, so if we write the setting from our plugin it only takes effect on the next start.
   - (b) our pack files must exist under `plugins/CraftEngine/resources/` before CE's `onEnable`.
   - (c) our behaviors, item behaviors and parsers must be registered in our **`onLoad()`**.
   - (d) our plugin must be `load: STARTUP` with CE as `load: BEFORE` and `join-classpath: true`.
   With the default `true`, CE neither has block definitions nor hooks worldgen until after the first tick, so CE blocks cannot be placed in startup chunks. See §7 and §9.
3. **Worldgen works through `ChunkData.setBlock` / `LimitedRegion.setBlockData` with `CraftEngineBlocks.getBukkitBlockData(state)`.** On Paper and Folia, CE wraps the world's NMS `ChunkGenerator`, including CraftBukkit's wrapper of a plugin generator. Before NOISE, SURFACE, CARVER, STRUCTURE and FEATURE it injects every chunk section, so writes of CE states are mirrored into CE storage on the worldgen thread. Confirmed by bytecode. Still test it (§9.6).
4. **Block entities fit TFC devices** (inventories, heat timestamps). They are a poor fit for millions of wild worldgen blocks. A block entity has **no ticking cost** unless a ticker is returned. Per-block data persists in CE's own region files, as NBT inside the CE chunk record. Controllers are created and serialized on whatever thread loads or saves the chunk, **including worldgen and chunk-IO threads**, so keep controller state simple and thread-safe to read.
5. **Lazy evaluation:** never return a ticker for crops. Store `plantedTick` / `lastTick` in the controller (or encode coarse stage in a block property). Catch up on interaction, on our own `ChunkLoadEvent` listener (CE restores at `LOWEST`, so we run after it), or in our own scheduler. Sleeping tickers (`SleepingBlockEntityTicker`) and `subscribeChunkLoad` exist **only on the `dev` branch** (26.10-SNAPSHOT). The wiki documents them, but they are **not in 26.9.x**. [SRC dev, JAR]
6. **Threading:** every API call that touches a `Location`/`Block` (`place`, `remove`, `getCustomBlockState(Block)`, `isCustomBlock`, block-entity lookup) must run on the owning region thread. `getCustomBlockState(BlockData)`, `getBukkitBlockData(state)`, `byId`, `loadedBlocks` and `ImmutableBlockState.with/get` are pure reads and thread-safe **except during `/ce reload`**. Reload nulls every custom state's binding from an async thread (§7.4).
7. **`/ce reload` hazards:** loaded chunks keep the *old* `ImmutableBlockState` objects in CE storage, and existing controllers keep references to *old* behavior instances. For a moment during reload, every custom block has `EmptyBlockBehavior` and `getCustomBlockState(...)` returns `null`. Re-resolve all cached `ImmutableBlockState` / `BlockData` on every `CraftEngineReloadEvent`. Prefer "restart, don't reload" in production.

---

## 1. Gradle coordinates, artifacts, relocations

### 1.1 Versions [SRC `gradle.properties`; repo `maven-metadata.xml` fetched 2026-10-16]
| Artifact | Latest published | Notes |
|---|---|---|
| `net.momirealms:craft-engine-core` | **26.9.1** (`lastUpdated 20260918`) | 26.9.2 → HTTP 404 |
| `net.momirealms:craft-engine-bukkit` | **26.9.1** | |
| `net.momirealms:craft-engine-bukkit-proxy` | 26.9.1 | NMS reflection proxies (`net.momirealms.craftengine.proxy.*`). Unstable, internal. |
| `net.momirealms:craft-engine-nms-helper(-mojmap)` | 1.0.240 | Closed-source injected NMS classes. We never compile against it. |

The source `main` branch is 26.9.2, but its API surface (`BlockEntityController`, `BlockEntity`, `EntityBlock`, `BlockEntityTicker`) is identical to the 26.9.1 jar [JAR javap]. `dev` = `26.10-SNAPSHOT` adds `SleepingBlockEntityTicker`, `ChunkSubscription`, `BlockEntityController.subscribeChunkLoad(BlockPos, Runnable)`, `BlockEntity.isValidForTick()`, and `BlockEntityTicker.isSleeping()/setTickingStateListener(...)` defaults [SRC `ce-dev/core/.../block/entity/tick/SleepingBlockEntityTicker.java`, `ce-dev/core/.../world/chunk/ChunkSubscription.java`].

```kotlin
repositories { maven("https://repo.momirealms.net/releases/") }
dependencies {
    compileOnly("net.momirealms:craft-engine-core:26.9.1")
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.1")
    // Optional, only if we call CE's NMS proxies (LevelProxy, LevelAccessorProxy...). UNSTABLE:
    compileOnly("net.momirealms:craft-engine-bukkit-proxy:26.9.1")
}
```
Wiki install snippet: [WIKI `api/`]. For `paper-plugin.yml` the wiki requires:
```yaml
dependencies:
  server:
    CraftEngine:
      load: BEFORE        # CE loads before us
      required: true      # (wiki shows false; TFC needs it)
      join-classpath: true
```

### 1.2 What each jar contains [JAR `unzip -l`]
- **core** (5.9 MB): `net/momirealms/craftengine/core/**` (2232 classes) plus shaded libraries under `net/momirealms/craftengine/libraries/{adventure,yaml,expression,message,nbt,option}`. It also contains **un-relocated** `com/google/gson/**` and `com/google/errorprone/**`, which can produce duplicate-class warnings on our compile classpath next to Paper's Gson.
- **bukkit** (2.2 MB): `net/momirealms/craftengine/bukkit/**` (872 classes) plus `libraries/antigrieflib`.
- **proxy** (0.96 MB): `net/momirealms/craftengine/proxy/**`.
- Class-file major version **65 (Java 21)** [JAR `od` on `BlockBehavior.class`]. That is fine for our Java 25 toolchain.
- Gradle module metadata declares **no transitive dependencies** [JAR `*.module`]. fastutil and the Bukkit/Paper API must come from `paper-api`.
- License in the POM: **GPL-3.0** [JAR pom; SRC `LICENSE`]. We depend on CE `compileOnly` and do not ship it. EUPL-1.2 lists GPLv3 as a compatible licence. This is a note, not legal advice.

### 1.3 Relocations that change our signatures [SRC `buildSrc/src/main/kotlin/net/momirealms/RelocationExtension.kt` L9–45]
| Original | Relocated to | Affects us |
|---|---|---|
| `net.momirealms.sparrow.nbt` | `net.momirealms.craftengine.libraries.nbt` | **Yes.** `CompoundTag`, `ListTag`, `Tag` in `BlockEntityController.save/loadCustomData`, `CraftEngineBlocks.place(..., CompoundTag, ...)`, `BlockDefinition.getBlockState(CompoundTag)`. |
| `net.kyori` (Adventure) | `net.momirealms.craftengine.libraries.adventure…` | Yes for any CE API that takes or returns `Component` (fonts/images). This is **not** Paper's Adventure, so convert via JSON/MiniMessage. |
| `org.snakeyaml`, `com.github.benmanes.caffeine`, `net.bytebuddy`, `org.incendo` (cloud) and others | `…libraries.*` | Not in the API we use. |

Paper's `BlockData`, `Location`, `Block`, `World` and `ItemStack` are **not** relocated.

---

## 2. Public API (`bukkit/api`): classes and events

The wiki says only the `api` package is stable: "Only the contents under the api package are considered stable…" [WIKI `api/`]. Everything else (behaviors, `CEWorld`, `BlockStateUtils`, proxies) is usable but may change between releases.

### 2.1 `net.momirealms.craftengine.bukkit.api.CraftEngineBlocks` [SRC `bukkit/api/CraftEngineBlocks.java` L37–402]
| Signature | Behaviour / notes | Thread |
|---|---|---|
| `static @NotNull Map<Key, BlockDefinition> loadedBlocks()` | Unmodifiable view of `byId`. **Empty during onEnable** with the default delayed load (javadoc L45–47). Cleared and refilled on reload. | any (read) |
| `static @Nullable BlockDefinition byId(@NotNull Key id)` | | any |
| `static boolean place(Location, ImmutableBlockState, boolean playSound)` | Uses `UpdateFlags.UPDATE_ALL`, `checkCanSurvive=false`. | owning region |
| `static boolean place(Location, ImmutableBlockState, boolean playSound, boolean checkCanSurvive)` | | owning region |
| `static boolean place(Location, Key blockId, boolean playSound)` / `(…, boolean checkCanSurvive)` | Default state of `blockId`. Returns false if the id is unknown. | owning region |
| `static boolean place(Location, Key blockId, CompoundTag properties, boolean playSound[, boolean checkCanSurvive])` | `block.getBlockState(properties)`. | owning region |
| `static boolean place(Location, Key blockId, CompoundTag properties, int flags, boolean playSound[, boolean checkCanSurvive])` | | owning region |
| `static boolean place(Location, ImmutableBlockState, int flags, boolean playSound[, boolean checkCanSurvive])` | Implementation L231–252: optional `canSurvive`, then `LevelWriter.setBlock(level,pos,state,flags)`, **then explicitly `onPlace(state, level, pos, oldState, false)`**, then an optional place sound. No scheduling. | owning region |
| `static boolean remove(@NotNull Block)` / `remove(Block, boolean movedByPiston)` | `Level.removeBlock` if custom. No drops. | owning region |
| `static boolean remove(Block, @Nullable Player, boolean movedByPiston, boolean dropLoot, boolean sendLevelEvent)` | Drops CE loot (with player context), level event 2001, then `removeBlock`. | owning region |
| `@Deprecated(forRemoval) remove(Block, Player, boolean isMoving, boolean dropLoot, boolean playSound, boolean sendParticles)` | **Bug:** forwards as `remove(block, player, dropLoot, isMoving, …)` (L344), which swaps `movedByPiston` and `dropLoot`. Do not use. | |
| `static boolean isCustomBlock(@NotNull Block)` | Reads the NMS state via `BlockGetter.getBlockState`. | owning region |
| `static @Nullable ImmutableBlockState getCustomBlockState(@NotNull Block)` | Same read. | owning region |
| `static @Nullable ImmutableBlockState getCustomBlockState(@NotNull BlockData)` | `CraftBlockData → NMS state → (DelegatingBlockState).blockState()`. Pure. | **any** (not during reload) |
| `static @NotNull BlockData getBukkitBlockData(@NotNull ImmutableBlockState)` | `BlockStateUtils.fromBlockData(state.customBlockState().minecraftState())` → Paper `asBlockData()`. This is the **real server-side `custom_N` state**, not the visual one. | **any** (not during reload) |
| `static boolean isVanillaBlockState(int id)` | | any |

`UpdateFlags` constants [SRC `core/.../block/UpdateFlags.java` L8–22]: `UPDATE_NEIGHBORS=1, UPDATE_CLIENTS=2, UPDATE_INVISIBLE=4, UPDATE_IMMEDIATE=8, UPDATE_KNOWN_SHAPE=16, UPDATE_SUPPRESS_DROPS=32, UPDATE_MOVE_BY_PISTON=64, UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE=128, UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS=256, UPDATE_SKIP_ON_PLACE=512, UPDATE_ALL=3, UPDATE_ALL_IMMEDIATE=11, UPDATE_NONE (1.21.5+: 260), UPDATE_SKIP_ALL_SIDEEFFECTS=816, UPDATE_NO_PHYS (1.21.5+: 530)`.

### 2.2 `BukkitAdaptor` [SRC `bukkit/api/BukkitAdaptor.java` L23–97]
| Signature | Notes |
|---|---|
| `static @Nullable BukkitServerPlayer adapt(Player)` | Online CE user, or null. |
| `static @NotNull BukkitWorld adapt(World)` | CE stores its `BukkitWorld` wrapper **in the CraftWorld's world-border field** (`CraftWorldProxy.getWorldBorder`). If missing and CE is not fully loaded, it calls `BukkitWorldManager.ensureStorageWorld(world)`. That **creates CE storage and injects the worldgen hook** as a side effect (L53–58). After full load it throws `IllegalStateException`. |
| `static @NotNull BukkitEntity adapt(Entity)` | |
| `static @NotNull BukkitExistingBlock adapt(Block)` | `isCustom()`, `customBlockState()`, `customBlock()`, `blockState()`, `id()`, `is(Key)`, `world()`, `x()/y()/z()` [JAR]. |
| `static @NotNull BukkitItem adapt(ItemStack)` | |

From a `BukkitWorld` (implements core `World`): `CEWorld storageWorld()`, `org.bukkit.World bukkitWorld()`, `Object minecraftWorld()`, `BlockStateWrapper getBlockState(x,y,z)`, `setBlockState(x,y,z, BlockStateWrapper, int flags)`, `getNoiseBiome(x,y,z)`, `playBlockSound`, `dropItemNaturally`, `spawnParticle`, `getTrackedBy(ChunkPos)` [JAR javap `BukkitWorld`].

### 2.3 `CraftEngineItems`, `CraftEngineFurniture`, `CraftEngineImages` [SRC]
- `CraftEngineItems` (L111–187): `loadedItems()`, `byId(Key)`, `byId(String)` (no colon → lookup by path), `byItemStack(ItemStack)`, `isCustomItem(ItemStack)`, `getCustomItemId(ItemStack)`. `BukkitItemDefinition` builds stacks: `buildBukkitItem()`, `buildBukkitItem(Player)`, `buildBukkitItem(ItemBuildContext,int)`.
- `CraftEngineFurniture` (L48–391): `loadedFurniture`, `byId`, `rayTrace(Location,double|Player[,double])`, `place(Location, Key[, String variant[, boolean playSound]])`, `place(Location, FurnitureDefinition, String|CompoundTag|FurniturePersistentData, boolean)`, `isFurniture/isCollisionEntity/isSeat(Entity)`, `getLoadedFurnitureByMetaEntity/BySeat/ByCollider`, `remove(Entity[, boolean dropLoot, boolean playSound])`, `remove(Entity, Player, boolean, boolean)`, `remove(Furniture, …)`. All touch entities, so they need the entity's or location's region thread.
- `CraftEngineImages`: `loadedImages()`, `byId(Key)`.

### 2.4 Events (`net.momirealms.craftengine.bukkit.api.event`) [SRC `bukkit/api/event/*.java`; fire sites from grep]
| Event | Superclass / cancellable | Fields (accessors) | Fired from / thread |
|---|---|---|---|
| `CraftEngineReloadEvent` | `Event`, no | `isFirstReload()`, `plugin()` (BukkitCraftEngine) | `BukkitCraftEngine.callReloadEvent` L120. First time: inside a nested `runDelayed` after startup (`CraftEngine.onPluginEnable` L539–543), i.e. **tick ≥2, main thread / Folia global region**. On `/ce reload`: the sync executor (`platform().run` → main / global region) after all managers reload. |
| `AsyncResourcePackCacheEvent` | async, no | `cacheData()`, `registerExternalResourcePack(Path folderOrZip)` | `BukkitPackManager` L43 |
| `AsyncResourcePackGenerateEvent` | async, no | `resourcePackFolder()`, `zipFilePath()` | `BukkitPackManager` L48 |
| `AsyncResourcePackPrepareEvent` | async, no | `playerId()`, `playerName()`, `packIds()` (mutable list) | `BukkitPackManager` L126 |
| `CustomBlockAttemptPlaceEvent` | `PlayerEvent`, **yes** | `player()`, `hand()`, `clickedBlock()`, `clickedFace()`, `customBlock()`, `location()` (clone), `blockState()` | `BlockItemBehavior` L140, before the world write. Player's region thread. |
| `CustomBlockPlaceEvent` | `PlayerEvent`, **yes** (CE reverts the placement) | `bukkitBlock()`, `hand()`, `customBlock()`, `location()`, `blockState()`, `player()` | `BlockItemBehavior` L166, **after** the Bukkit `BlockPlaceEvent` (also fired by CE, L153). |
| `CustomBlockBreakEvent` | `PlayerEvent`, **yes** | `player()` (BukkitServerPlayer), `dropItems()/setDropItems`, `bukkitBlock()`, `customBlock()`, `location()`, `blockState()` | Inside CE's **`BlockBreakEvent` MONITOR** handler (`bukkit/block/listener/BlockEventListener.java` L143–215). If cancelled, CE cancels the Bukkit event at MONITOR. |
| `CustomBlockInteractEvent` | `PlayerEvent`, **yes** | `clickedFace()`, `interactionPoint()` (nullable), `action()` (`LEFT_CLICK`/`RIGHT_CLICK`), `hand()`, `bukkitBlock()`, `customBlock()`, `location()`, `blockState()`, `item()` (@Experimental) | CE's `PlayerInteractEvent` HIGHEST handler (`bukkit/item/listener/ItemEventListener.java` L174, L221). Fired before block behavior `useOnBlock`. |
| `FurnitureAttemptPlaceEvent`, `FurniturePlaceEvent`, `FurnitureBreakEvent` (`dropItems`), `FurnitureHitEvent` (`hitBox`, `hitPoint`), `FurnitureInteractEvent` (`hitBox`, `interactionPoint`, `hand`) | `PlayerEvent`, yes | | Packet listeners hop to the region thread first (`AttackListener` L182, `InteractListener` L319 → `platform().run(task, location)`). |
| `BlockDispenseProjectileEvent` | `BlockEvent`, no | `getItem()`, `getProjectile()` | dispenser path |
| `ServerPreShutdownEvent` | `ServerEvent`, no | — | `ServerEventListener` L99–111: on the first `PluginDisableEvent` while the server is stopping. CE then **disables itself** (saves CE data) before other plugins unload. |

Wiki convention: "Please avoid directly using events like CustomBlockBreakEvent. Instead … listen to the corresponding Bukkit event … convert … using CraftEngineBlocks" [WIKI `api/`]. CE itself fires `BlockPlaceEvent` for custom placements and handles `BlockBreakEvent`, so Bukkit listeners see custom blocks with `getBlockData()` = the `custom_N` state.

---

## 3. Writing a custom BlockBehavior

### 3.1 Class hierarchy [SRC]
- `net.momirealms.craftengine.core.block.behavior.BlockBehavior` (abstract; `core/.../block/behavior/BlockBehavior.java`). It has a `protected final BlockDefinition blockDefinition`, `block()`, `let(Class,Consumer)` and `getFirst(Class)`. Abstract methods: `isPathFindable`, `fallOn`, `updateEntityMovementAfterFallOn`.
- `net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior extends BlockBehavior` (`bukkit/block/behavior/BukkitBlockBehavior.java`). It implements those three via the visual state and vanilla fall logic, plus pre-1.21.5 `affectNeighborsAfterRemoval`. It exposes protected arg-index constants: `updateShape$level`, `updateShape$blockPos`, `updateShape$neighborState`, `updateShape$direction`, `isPathFindable$type`. **Extend this one.**
- `AbstractCanSurviveBlockBehavior extends BukkitBlockBehavior` (`bukkit/block/behavior/AbstractCanSurviveBlockBehavior.java` L25–111). It handles "break when it can no longer survive" (schedules a tick in `onPlace`/`updateShape`, destroys in `tick`). Implement `protected abstract boolean canSurvive(Object thisBlock, Object state, Object level, Object blockPos)`. It also offers the `readTagsAndState(section, prefix)` helper.
- Optional mix-in interfaces in `core/.../block/behavior/`: `EntityBlock` (block entity), `RandomTickBlock` (`boolean canRandomlyTick(ImmutableBlockState)`), `BonemealableBlock`, `FallableBlock`, `PathFindingBlock`, `PrioritizedFallOnHandler`, `BucketPickup`, `LiquidBlockContainer`, `SimpleWaterloggedBlock`, `WorldlyContainerHolder`.

**Composition:** a block config with several behaviors is wrapped. Hard-coded behaviors come first (`axis`/`facing`/`facing_clockwise`/`rotation` properties → `HardcodedPropertyBlockBehavior`), then the configured list, then `WaterloggedBlockBehavior` if a boolean `waterlogged` property exists. One behavior is used as is, two become a `DualBlockBehavior`, three or more a `CompositeBlockBehavior` [SRC `bukkit/block/BukkitBlockManager.java` L169–205]. **Never cast `state.behavior()` to our class. Use `state.behavior().getFirst(OurBehavior.class)`** [WIKI `api/block_behavior`]. Note that adding a `facing` property silently turns our single behavior into a Dual.

### 3.2 Every overridable callback (arg layouts from javadoc) [SRC `core/.../block/behavior/BlockBehavior.java` L50–479]
The NMS-level callbacks are `(Object thisBlock, Object[] args)`. `thisBlock` is the NMS `Block` (CE's ByteBuddy `CraftEngineBlock`). The args are Mojang-mapped NMS objects. Paper 1.20.5+ runs Mojang-mapped, so with paperweight they can be cast (`(ServerLevel) args[1]`, `(BlockPos) args[2]`). Without NMS, use CE's proxy and util classes (§3.6).

| Method (return) | args (current versions) | Default |
|---|---|---|
| `Object rotate` (BlockState) | 0 state, 1 Rotation | `args[0]` |
| `Object mirror` (BlockState) | 0 state, 1 Mirror | `args[0]` |
| `Object updateShape` (BlockState) | **1.21.2+:** 0 state, 1 LevelReader, 2 ScheduledTickAccess, 3 pos, 4 Direction, 5 neighborPos, 6 neighborState, 7 RandomSource · *1.20–1.21.1:* 0 state, 1 Direction, 2 neighborState, 3 LevelAccessor, 4 pos, 5 neighborPos | `args[0]` |
| `void neighborChanged` | **1.21.2+:** 0 state, 1 Level, 2 pos, 3 neighborBlock, 4 Orientation (nullable), 5 movedByPiston · *older:* 4 = neighborPos | — |
| `void tick` (scheduled tick) | 0 state, 1 ServerLevel, 2 pos, 3 RandomSource | — |
| `void randomTick` | 0 state, 1 ServerLevel, 2 pos, 3 RandomSource | — |
| `void onPlace` | 1.20.5+: 0 state, 1 Level, 2 pos, 3 oldState, 4 movedByPiston | — |
| `boolean canSurvive` | 0 state, 1 LevelReader (**may be a `WorldGenRegion` during worldgen**, see `CropBlockBehavior.canSurvive` L129–137), 2 pos | `true` |
| `boolean isPathFindable` | 1.20.5+: 0 state, 1 PathComputationType | Bukkit impl: from the visual state |
| `boolean hasAnalogOutputSignal` | 0 state | false |
| `int getAnalogOutputSignal` | 0 state, 1 Level, 2 pos | 0 |
| `void preExplosionHit` / `postExplosionHit` (1.21+) | 0 state, 1 ServerLevel, 2 pos, 3 Explosion, 4 BiConsumer<ItemStack,BlockPos> | — |
| `void entityInside` | 0 state, 1 Level, 2 pos, 3 Entity, (1.21.5+) 4 InsideBlockEffectApplier, (1.21.10+) 5 isPrecise | — |
| `void affectNeighborsAfterRemoval` | 1.21.5+: 0 state, 1 ServerLevel, 2 pos, 3 movedByPiston · older: 3 newState, 4 movedByPiston | Bukkit impl (pre-1.21.5 only) removes the vanilla BE |
| `int getSignal` / `getDirectSignal` | 0 state, 1 BlockGetter, 2 pos, 3 Direction | 0 |
| `boolean isSignalSource` | 0 state | false |
| `Object playerWillDestroy` (BlockState) | 0 Level, 1 pos, 2 state, 3 Player | `args[2]` |
| `void spawnAfterBreak` | 0 state, 1 ServerLevel, 2 pos, 3 ItemStack, 4 dropExperience | — |
| `void stepOn` | 0 Level, 1 pos, 2 state, 3 Entity | — |
| `void onProjectileHit` | 0 Level, 1 state, 2 BlockHitResult, 3 Projectile | — |
| `void placeMultiState` | 0 Level/WorldGenLevel, 1 pos, 2 state, 3 LivingEntity placer, 4 ItemStack | — |
| `void fallOn` | 0 Level, 1 state, 2 pos, 3 Entity, 4 fallDistance (double on 1.21.5+, float before) | Bukkit impl: fall damage |
| `void updateEntityMovementAfterFallOn` (@Obsolete, ≤26.1.2) | 0 BlockGetter, 1 Entity | Bukkit impl |
| `boolean triggerEvent` | 0 state, 1 Level, 2 pos, 3 type, 4 data | false |
| `void attack` | 0 state, 1 Level, 2 pos, 3 Player | — |
| `void handlePrecipitation` | 0 state, 1 Level, 2 pos, 3 Biome.Precipitation | — |

High-level callbacks (CE types, no NMS):
| Method | Called from | Notes |
|---|---|---|
| `ImmutableBlockState updateStateForPlacement(BlockPlaceContext ctx, ImmutableBlockState state)` | CE `BlockItemBehavior` when a player places | Return the initial state. Return `null` to abort the placement (BlockItemBehavior returns PASS when the state is null, L100–103). |
| `InteractionResult useOnBlock(UseOnContext ctx, ImmutableBlockState state)` | CE `PlayerInteractEvent` HIGHEST handler, **right-click with a hit point** (`ItemEventListener` L264–290) | Default `TRY_EMPTY_HAND` → CE then calls `useWithoutItem` (main hand only). |
| `InteractionResult useWithoutItem(UseOnContext, ImmutableBlockState)` | as above | Default `PASS`. |
| `boolean canUseOnBlockIfSecondaryUseActive(UseOnContext, ImmutableBlockState)` | as above | If false and the player sneaks with an item, block use is skipped (L267–268). |
| `boolean canBeReplaced(BlockPlaceContext, ImmutableBlockState)` | placement | Default: `settings.replaceable()`, except the block's own item cannot replace it. |
| `boolean canPlaceMultiState(WorldAccessor, BlockPos, ImmutableBlockState)` / `hasMultiState(state)` | multi-block placement | |
| `Item itemToPickup(World, BlockPos, ImmutableBlockState, Player)` | pick block | |

`InteractionResult` [SRC `core/.../entity/player/InteractionResult.java`]: `FAIL, SUCCESS, PASS, TRY_EMPTY_HAND, SUCCESS_AND_CANCEL`. Only `SUCCESS` and `SUCCESS_AND_CANCEL` count as success. `SUCCESS_AND_CANCEL` also cancels the Bukkit `PlayerInteractEvent` (L271–273). `SUCCESS` does not, so vanilla item use can still run. Use `SUCCESS_AND_CANCEL` when we consumed the click.

`UseOnContext` [JAR]: `getHitResult()`, `getClickedPos()` (core `BlockPos`), `getClickedFace()`, `getClickLocation()`, `isInside()`, `getItem()` (core `Item`), `getPlayer()` (core `Player`, nullable), `getHand()`, `getLevel()` / `getWorld()` (core `World` → `.storageWorld()`, `.platformWorld()`), `getHorizontalDirection()`, `isSecondaryUseActive()`, `getRotation()`. `BlockPlaceContext extends UseOnContext` adds `getAgainstPos()`, `canPlace()`, `isWaterSource()`, `replacingClickedBlock()`, `getVerticalLookingDirection()`, `getNearestLookingDirection(s)()`.

### 3.3 Factory and registration [SRC `core/.../block/behavior/BlockBehaviorFactory.java`, `BlockBehaviors.java`, `BlockBehaviorType.java`]
```java
public interface BlockBehaviorFactory<T extends BlockBehavior> {
    T create(BlockDefinition block, ConfigSection section);
    static <A extends Comparable<A>> Property<A> getProperty(String path, BlockDefinition block, String name, Class<A> valueClass); // throws KnownResourceException
    static <A extends Comparable<A>> @Nullable Property<A> getOptionalProperty(BlockDefinition block, String name, Class<A> valueClass);
}
public record BlockBehaviorType<T extends BlockBehavior>(Key id, BlockBehaviorFactory<T> factory) {}
public static <T extends BlockBehavior> BlockBehaviorType<T> BlockBehaviors.register(Key key, BlockBehaviorFactory<T> factory);
```
- In YAML, `type: foo` without a namespace resolves to `craftengine:foo` (`Key.ce(...)`). Always write `type: tfc:crop`.
- **Factories run on CE's async config-loading pool** when `misc.multi-threaded-configuration-load: true`, the default (`AbstractBlockManager` BlockParser `async()` L447–449). So do not call the Bukkit world API in a factory. Throw `KnownResourceException` (or let `getProperty` throw) for config errors. CE then logs it and falls back to `BukkitBlockBehavior` (`AbstractBlockManager` L795–801).
- Factories are called **again on every `/ce reload`**, creating new behavior instances (§7.4).
- No registry freeze exists in `core/.../registry` [SRC grep "freeze"]. Registering late works, but blocks parsed earlier will not have used our type.

### 3.4 Config access helpers [SRC `core/.../plugin/config/ConfigSection.java` (974 lines), `ConfigKeys.java`]
- Keys: `ConfigKeys.of("grow_speed")` gives `{"grow_speed","grow-speed"}`. It also supports `"particle(s)"` (optional suffix) and `"a|b"` alternatives. Every getter has a `String` and a `String[]` overload.
- Getters (with `def` and `Supplier` variants): `getString/getNonEmptyString/getNonNullString`, `getBoolean`, `getInt`, `getLong`, `getFloat`, `getDouble`, `getEnum(key, Class)`, `getIdentifier`/`getKey` (→ `Key`), `getNumber` (→ `NumberProvider`, supports expressions/random), `getSection`/`getNonNullSection`, `getList`, `getStringList`, `getSectionList(key, Function<ConfigSection,T>)`, `getList(key, Function<ConfigValue,T>)`, `getVector3f`, `getQuaternion`, `getAABB`, `getSNBT` (→ relocated `Tag`). `path()` / `assemblePath(key)` give the error path.
- Properties: `BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class)` returns `Property<Integer>`, usually an `IntegerProperty` with public `min`/`max` [JAR]. Property classes: `BooleanProperty`, `IntegerProperty`, `EnumProperty`, `StringProperty`.

### 3.5 Reading and writing properties [SRC `core/.../block/ImmutableBlockState.java`; JAR]
- Read: `state.get(prop)`, `state.get(prop, fallback)`, `state.getNullable(prop)`, `state.contains(prop)`, `state.getProperty("age")`, `state.propertiesNbt()`.
- Derive: `state.with(prop, value)`, `state.cycle(prop)`, `state.with(CompoundTag props)`, `ImmutableBlockState.with(state, prop, Object)`. These are immutable lookups and allocate nothing.
- Other: `state.owner()` (`Holder<BlockDefinition>`, `.value()` = current definition), `state.behavior()`, `state.settings()`, `state.hasBlockEntity()`, `state.customBlockState()` / `visualBlockState()` (`BlockStateWrapper`: `minecraftState()`, `registryId()`).
- **Writing back to the world** (an `ImmutableBlockState` never changes in place [WIKI block_behavior]). Options:
  1. Inside an NMS callback: `LevelWriterProxy.INSTANCE.setBlock(level, pos, newState.customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL)` (proxy jar). With paperweight: `((Level) args[1]).setBlock((BlockPos) args[2], (BlockState) newState.customBlockState().minecraftState(), 3)`. CE's crop uses `CraftEventFactoryProxy.handleBlockGrowEvent(level, pos, newNms, UpdateFlags.UPDATE_CLIENTS)` so Bukkit `BlockGrowEvent` fires (`CropBlockBehavior` L117–121).
  2. From Bukkit code on the region thread: `block.setBlockData(CraftEngineBlocks.getBukkitBlockData(newState), false)`. This goes through the injected section, so CE storage, block entity and renderers update. If the owner block is the same, the block entity is **kept** and `preBlockStateChange(newState)` is called (`WorldStorageInjector` L137–151).
  3. `CraftEngineBlocks.place(loc, newState, flags, false)`. It calls `onPlace` explicitly after `setBlock`, so `onPlace` may run **twice** [INF: vanilla `LevelChunk.setBlockState` already calls `onPlace` unless `UPDATE_SKIP_ON_PLACE`]. Prefer (2) for property changes.

### 3.6 Scheduled ticks, neighbor updates, level/pos conversion
- Schedule a block tick (CE pattern): `LevelAccessorProxy.INSTANCE.scheduleTick$0(level, blockPos, thisBlock, delayTicks)` (`AbstractCanSurviveBlockBehavior` L43, L56). The proxy also has `scheduleTick$1`, `updateNeighborsAt(level,pos,block)` [JAR proxy]. The tick arrives in `tick(thisBlock, args)`. Paperweight equivalent: `level.scheduleTick(pos, (Block) thisBlock, delay)`.
  - Scheduled ticks are saved in the **vanilla** chunk keyed by the `craftengine:custom_N` block id, and vanilla only runs one if the state at the position still `is(block)` [MEM]. They survive restarts but are not "lazy". For TFC, prefer calendar timestamps.
- Random ticks: a state random-ticks if `behavior instanceof RandomTickBlock` and `canRandomlyTick(state)`, otherwise per `settings.is_randomly_ticking` (default false) (`BukkitBlockManager` L354–357). **Do not implement `RandomTickBlock`** for TFC lazy blocks.
- Level → Bukkit world: `LevelProxy.INSTANCE.getWorld(levelObj)` (proxy) → `BukkitAdaptor.adapt(world)`. NMS pos → CE pos: `LocationUtils.fromBlockPos(Object)`. CE pos → NMS: `LocationUtils.toBlockPos(BlockPos)` (`net.momirealms.craftengine.bukkit.util.LocationUtils`, bukkit jar, not `api`).
- NMS state → CE state: `BlockStateUtils.getOptionalCustomBlockState(Object nmsState)` / `getNullableCustomBlockState(Object)` (`bukkit/util/BlockStateUtils.java` L137–152). This is an `instanceof DelegatingBlockState` check plus a field read.

---

## 4. Block entities: controllers, persistence, tickers

### 4.1 Model [SRC `core/.../block/entity/*.java`; WIKI `api/block_entity`]
- `BlockEntity` is a **final** CE class (not a vanilla BE, nothing to register). Public fields: `final BlockPos pos`, `final DynamicBlockEntityRenderer renderer`, `ImmutableBlockState blockState`, `CEWorld world`, `BlockEntityController controller`. Methods: `isValid()`, `blockState()`, `world()`, `pos()`, `saveAsTag()`, `isValidBlockState(state)` (same owner), `updateConstantRenderers()` [JAR].
- A behavior opts in by implementing `EntityBlock`:
  ```java
  BlockEntityController createBlockEntityController(BlockEntity blockEntity);
  void initControllerId(int id);   // index of this behavior's controller inside a Dual/Composite
  ```
  If the (possibly composite) behavior contains an `EntityBlock`, **every state of the block** gets `hasBlockEntity=true` (`AbstractBlockManager` L803–813). The top-level behavior receives `initControllerId(0)`. Dual/Composite renumber their children 0, 1, … (`CompositeBlockBehavior.initControllerId`).
- `BlockEntityController` (abstract) overridables [JAR]:
  | Method | When / thread |
  |---|---|
  | constructor `(BlockEntity)` | BE creation: placement (region thread), **chunk deserialization (chunk-IO / preload worker or region thread)**, **worldgen (worldgen worker)**. Must not touch the world [WIKI "do not assume the world is available yet"]. |
  | `void saveCustomData(CompoundTag)` | Chunk serialization on the **calling** thread: chunk unload (region), `WorldSaveEvent` (`saveChunks` over all loaded chunks), worldgen stage end (worldgen worker) (`AsyncStorage.writeChunkAt` L88–92 serializes on the caller). |
  | `void loadCustomData(CompoundTag)` | During deserialization, same threads as the constructor (`DefaultBlockEntitySerializer.deserialize`). |
  | `void loadCustomDataFromItem(Item)` | After a player places the block with a CE block item (`BlockItemBehavior` L208–213). |
  | `void onLoad()` | BE becomes active: chunk activation (`CEChunk.activateAllBlockEntities` L507–532) or placed into an already active chunk (`addBlockEntity` L473–484). Region thread. |
  | `void onUnload()` | Chunk deactivation, or removal while active. "Unloading is not breaking." |
  | `void onRemove()` | Block replaced by another owner or vanilla state (`WorldStorageInjector` L115–129, L178–189). Drop items here. |
  | `void preBlockStateChange(ImmutableBlockState newState)` | Same owner, state change. |
  | `<C> BlockEntityTicker<C> createBlockEntityTicker(CEWorld, ImmutableBlockState)` | Sync ticker or `null` (default). |
  | `<C> BlockEntityTicker<C> createAsyncBlockEntityTicker(CEWorld, ImmutableBlockState)` | Async ticker or `null`. |
  | `boolean hasElement()` / `void gatherElements(Consumer<BlockEntityElement>)` | Dynamic renderers (display entities). |
  Helpers: `blockEntity()`, `let(Class, int id, Consumer)`, `let(Class, int id, Function)`, `let(Class, Consumer)`, `getAt(Class,int)`, `get(Class,int order)`, `static createTickerHelper(ticker)`, `static createFromList(BlockEntity, List)`.
- **Composite controllers share one `CompoundTag`**: Dual/Composite call every child's `saveCustomData(data)` on the same tag (`BlockEntityController` L187–197, L387–399). **Namespace our keys** (`tag.put("tfc:crop", sub)`), as the wiki says.

### 4.2 Lookup from outside [SRC `core/.../world/CEWorld.java` L188–202; WIKI]
```java
CEWorld ce = BukkitAdaptor.adapt(bukkitWorld).storageWorld();     // or context.getLevel().storageWorld()
BlockEntity be = ce.getBlockEntityAtIfLoaded(pos);                // create=true: lazily CREATES the BE if the state hasBlockEntity
BlockEntity be2 = ce.getBlockEntityAtIfLoaded(pos, false);         // no creation
if (be != null) be.controller.let(TfcCropController.class, this.controllerId, c -> { ... });
```
- Returns null if the chunk is not loaded in CE (`loadedChunkMap`) or the position is outside the height limits. Invalid entries are dropped on lookup (`CEChunk.getBlockEntity` L694–710).
- After mutating controller data: `blockEntity.world.blockEntityChanged(blockEntity.pos)` marks the CE chunk unsaved (`CEWorld` L307–312). Data restored in `loadCustomData` needs no mark [WIKI].
- Other `CEWorld` reads: `getBlockStateAtIfLoaded(x,y,z|BlockPos)` (CE storage view), `getChunkAtIfLoaded(...)` → `CEChunk.blockEntities()` (unmodifiable), `CEChunk.getBlockState`, `isChunkLoaded(long)`.

### 4.3 Persistence format and location [SRC `core/.../world/chunk/serialization/*`, `bukkit/world/BukkitStorageAdaptor.java`]
- Per world, under `<worldFolder>/craftengine/` (`CEWorld.REGION_DIRECTORY`), in `linear` (default) or `mca` region files. Alternatives: `pdc` (Bukkit chunk PDC key `craftengine:chunk_data`, world settings in world PDC `craftengine:world_settings`) or `none`. Worlds matching `chunk-system.blacklisted-worlds` always get `NoneStorage` (in-memory only). Config: `config.yml` L729–809.
- Chunk NBT: `{sections:[…palette by CE id…], block_entities:[{x,y,z, …our custom keys…}], block_entity_renderers:[…]}`. **If a chunk has no non-empty CE section, nothing is written, block entities included** (`DefaultChunkSerializer.serialize`: `if (sections.isEmpty()) return null`).
- A BE record carries **no type id**. On load, the controller class comes from the block state at that position. If the state no longer has a BE (definition removed or changed), the tag is kept verbatim in an `InactiveBlockEntityController` and re-saved, so the data is not lost (`DefaultBlockEntitySerializer.deserialize`, `InactiveBlockEntityController`).
- Positions are re-verified against the chunk (`BlockEntity.readPosAndVerify`).

### 4.4 Tickers [SRC `core/.../block/entity/tick/*`, `CEChunk` L567–602, `CEWorld` L64–86, L248–305, `bukkit/world/FoliaCEWorld.java`, `bukkit/world/chunk/FoliaCEChunk.java`]
- `interface BlockEntityTicker<T extends BlockEntityController> { void tick(CEWorld world, BlockPos pos, ImmutableBlockState state, T controller); }`
- Tickers are created when the chunk activates or the state changes (`replaceOrCreateTickingBlockEntity`) and wrapped in a `ReplaceableTickingBlockEntity`. On deactivation they are swapped for `DummyTickingBlockEntity`. Exceptions are caught and logged per tick (`DefaultTickingBlockEntity.tick`).
- **Paper:** each `CEWorld` runs a sync task every tick (`runRepeating(syncTick,1,1)` → main thread) and an async task every tick (`runAsyncRepeating`; it skips a round if the previous one is still running).
- **Folia:** the world sync task runs on the **global region**. Every tick it schedules `chunk::tick` on the **region scheduler for every chunk that has sync tickers** (`FoliaCEWorld.syncTick`), so the cost is one region task per ticking chunk per tick. Async tickers go into the world's async list.
- Async tickers run on Bukkit's async scheduler and **must not mutate the world** [WIKI]. They are for particles and packet-only work.
- **Sleeping tickers / chunk subscriptions:** `SleepingBlockEntityTicker<T>(delegate)` with `sleep()/wakeUp()/isSleeping()`, and `subscribeChunkLoad(BlockPos, Runnable) → ChunkSubscription{cancel(), cancelled()}`. Documented in [WIKI `api/block_entity_ticker`], **dev branch only** (26.10-SNAPSHOT), and absent from 26.9.1 [JAR `unzip -l` shows no `Sleeping*`]. Wiki rules for when they ship: keep one ticker instance per controller field; call `sleep`/`wakeUp` on the owning tick thread; subscription callbacks run directly on Paper's main thread and are scheduled on the subscriber's region thread on Folia.

### 4.5 Cost per block entity [INF from source]
- No ticker: one `BlockEntity` (5 fields), our controller, one entry in the chunk's `ConcurrentChainedLong2ReferenceHashTable`, plus the serialized NBT in CE region files. No per-tick cost. Roughly 100–300 bytes of heap plus our fields (UNVERIFIED estimate).
- With a ticker: also a `DefaultTickingBlockEntity` and a `ReplaceableTickingBlockEntity`, a map entry per chunk, a slot in the world ticker list, and a call every tick. On Folia it also adds a region task per chunk per tick.
- Worldgen-created BEs are constructed and serialized during generation (§9), which adds cost for every generated BE block.

### 4.6 Do controllers survive `/ce reload`? [SRC + INF]
- Reload does **not** rebuild BEs or CE sections. Search found no remap code in `core/.../world` or `bukkit/world`; `CESection` stores `ImmutableBlockState` object references directly (`CESection`, `PalettedContainer`).
- So in loaded (and cached) chunks, existing `BlockEntity.blockState` and controllers stay alive with references to the **old** behavior instance and old `ImmutableBlockState`. New placements and chunks later read from disk get new instances. `CachedStorage` may hand back the same `CEChunk` object for up to 30 s, or for the chunk-holder lifetime in `lifecycle` mode, so old objects can outlive an unload/load cycle.
- Guidance: a controller should hold **no behavior/config references**. Re-resolve config lazily: `blockEntity.blockState.owner().value()` returns the *current* `BlockDefinition` because the holder is reused (`AbstractBlockManager` L497–503). Then `.defaultState().behavior().getFirst(TfcCropBehavior.class)`. Even better, keep tunables in our own registry keyed by block id.

---

## 5. Item behaviors [SRC `core/.../item/behavior/*.java`]
```java
public abstract class ItemBehavior {
    public InteractionResult useOnBlock(UseOnContext context)                        { return PASS; }
    public InteractionResult useOnEntity(InteractEntityContext context)              { return PASS; }
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) { return PASS; }
    public void onBreakBlock(World world, Player player, BlockPos pos) {}
    public <T> void let(Class<T>, Consumer<T>);  public <T> T getFirst(Class<T>);
}
public interface ItemBehaviorFactory<T extends ItemBehavior> { T create(Pack pack, Path path, Key id, ConfigSection section); }
public static <T extends ItemBehavior> ItemBehaviorType<T> ItemBehaviors.register(Key key, ItemBehaviorFactory<T> factory);
```
- Dispatch [SRC `bukkit/item/listener/ItemEventListener.java`]:
  - `useOnBlock`: right-click on a block, `PlayerInteractEvent` HIGHEST, after the custom-block interaction (L386–405). It is skipped if the clicked vanilla block is interactable and the player is not sneaking. `SUCCESS_AND_CANCEL` cancels the event. Anything other than `PASS` stops further processing.
  - `use`: right-click air (L631, `PlayerInteractEvent` HIGH).
  - `useOnEntity`: `PlayerInteractEntityEvent` HIGHEST (L165).
  - `onBreakBlock`: inside CE's `BlockBreakEvent` MONITOR handler, if not cancelled (`BlockEventListener` L176–178).
- Built-in `block_item` handles CE block placement (events, `updateStateForPlacement`, `loadCustomDataFromItem`). For a TFC seed item that plants a crop, the simplest route is the built-in `block_item` with `block: tfc:crop/wheat` plus our `tfc:crop` block behavior. Use a custom `ItemBehavior` only for non-placement logic (knapping, firestarter and similar).
- Composition: several item behaviors are wrapped in `DualItemBehavior`/`CompositeItemBehavior`. Find ours with `getFirst`.
- `dev` branch head commit is "物品行为状态" (item behavior state), but `ItemBehavior`/`ItemBehaviorFactory` are unchanged in dev [SRC diff] (UNVERIFIED beyond those two files).

---

## 6. Other registries and custom config parsers

### 6.1 `BuiltInRegistries` [SRC `core/.../registry/BuiltInRegistries.java` L113–175] and their `register` helpers
| Registry | Register helper (all `static`, `(Key, Factory)`) | Use for TFC |
|---|---|---|
| `BLOCK_BEHAVIOR_TYPE` | `core.block.behavior.BlockBehaviors.register` | **Yes.** All TFC block logic. |
| `ITEM_BEHAVIOR_TYPE` | `core.item.behavior.ItemBehaviors.register` | **Yes.** Tools, knapping, firestarter. |
| `BLOCK_SETTINGS_TYPE` | `core.block.setting.BlockSettingsModifiers.register(Key, BlockSettingsModifierFactory)` | Maybe: custom `settings:` keys. |
| `ITEM_SETTINGS_TYPE` | `core.item.setting.ItemSettingsModifiers.register` | Maybe. |
| `PROPERTY_TYPE` | `core.block.property.Properties.register(Key, PropertyFactory)` | Rare. |
| `BLOCK_ENTITY_ELEMENT_TYPE` | `core.block.entity.render.element.BlockEntityElementConfigs.register` | Custom renderer elements. |
| `BLOCK_ENTITY_TINT_SOURCE_TYPE` | `core.block.entity.render.tint.BlockEntityTintSources.register` | |
| `COMMON_FUNCTION_TYPE` | `core.plugin.context.CommonFunctions.register(Key, FunctionFactory<Context,T>)` | YAML `events:` actions calling TFC code. |
| `COMMON_CONDITION_TYPE` | `core.plugin.context.CommonConditions.register(Key, ConditionFactory<Context,T>)` | e.g. `tfc:season`. |
| `EVENT_TRIGGER` | `core.plugin.context.EventTrigger.register(Key id, String... aliases)` | |
| `NUMBER_PROVIDER_TYPE` | `core.plugin.context.number.NumberProviders.register` | |
| `TEMPLATE_ARGUMENT_TYPE` | `core.plugin.config.template.argument.TemplateArguments.register` | |
| `LOOT_FUNCTION_TYPE` / `LOOT_ENTRY_CONTAINER_TYPE` / `LOOT_SOURCE_TYPE` / `FORMULA_TYPE` | `LootFunctions`, `LootEntryContainers`, `LootSources`, `Formulas` (`core.loot.*`) | TFC drops (crop yield from BE data via a function). |
| `RECIPE_SERIALIZER` | `core.item.recipe.RecipeSerializers.register(Key, RecipeSerializer)` | Probably no. TFC recipes are our own. |
| `ITEM_PROCESSOR_TYPE`, `ITEM_UPDATER_TYPE`, `CRAFT_REMAINDER_TYPE`, `POST_PROCESSOR_TYPE`, `DATA_COMPONENT_PREDICATE_TYPE`, `EQUIPMENT_TYPE` | `ItemProcessors`, `ItemUpdaters`, `CraftRemainders`, `PostProcessors`, `DataComponentPredicates`, `Equipments` | Item data. |
| Furniture: `FURNITURE_BEHAVIOR_TYPE`, `FURNITURE_ELEMENT_TYPE`, `FURNITURE_HITBOX_TYPE`, `FURNITURE_SETTINGS_TYPE` | `FurnitureBehaviors`, `FurnitureElementConfigs`, `FurnitureHitBoxConfigs`, `FurnitureSettingsModifiers` | |
| Pack/model: `ITEM_MODEL_TYPE`, `TINT_TYPE`, `SPECIAL_MODEL_TYPE`, `RANGE_DISPATCH/SELECT/CONDITION_PROPERTY_TYPE`, `PATH_MATCHER_TYPE`, `RESOLUTION_TYPE`, `PACK_WORKFLOW_TYPE`, `RESOURCE_PACK_HOST_TYPE` | `ItemModels`, `Tints`, `SpecialModels`, …, `ResourcePackHosts` | Not needed. |
| Attributes/damage, dialogs, `STORAGE_TYPE` (DB storage, *not* chunk storage), `CATEGORY_SOURCE_TYPE`, `PLAYER_SELECTOR_TYPE`, `CLICK_EVENT_TYPE`, packets | various | Not needed. |
| `BLOCK` | filled by the parser (`getOrRegisterForHolder`) | Never register into it. |
| `CONFIG_PARSER` | via `PackManager.registerConfigSectionParser` | see below |

The full list of helpers is in [SRC grep "public static <T …> … register(Key"], e.g. `core/.../loot/function/LootFunctions.java` L27 and `core/.../plugin/context/CommonFunctions.java` L73.

### 6.2 Custom top-level config sections [SRC `core/.../pack/AbstractPackManager.java` L464–475; `core/.../plugin/config/ConfigParser.java`]
```java
CraftEngine.instance().packManager().registerConfigSectionParser(ConfigParser parser); // false if a section id is taken
// ConfigParser: Key type(); String[] sectionId(); LoadingStage loadingStage(); List<LoadingStage> dependencies();
//               void addConfig(CachedConfigSection); void loadAll(); void clearConfigs(); void setErrorHandler(...);
//               default preProcess()/postProcess()/count()/silentIfNotExists()/async()
```
Extend `IdSectionConfigParser` (per-id entries, applies templates and `__ID__`/`__NAMESPACE__`) or `SectionConfigParser`. Stages are in `core/.../plugin/config/LoadingStages` (`TEMPLATE, CONFIG_FACTORY, BLOCK_STATE_MAPPING, …, ITEM, BLOCK, FURNITURE, …, RECIPE, …, CONFIGURED_FEATURE, PLACED_FEATURE, …`). Register it before configs load, in our `onLoad`. The `packManager` is created in CE's bootstrap (`BukkitCraftEngine.setUpConfigAndLocale` L129–132), so it exists by our `onLoad`. The wiki comment "延迟重载，以便其他依赖CraftEngine的插件能注册parser" (delayed reload so dependent plugins can register parsers) is at `CraftEngine.onPluginEnable` L486. Probably not needed for TFC: our data loads from our own files.

---

## 7. Startup, lifecycle and reload

### 7.1 Sequence [SRC]
| Phase | What CE does | Source |
|---|---|---|
| **Paper bootstrap** (`PaperCraftEngineBootstrap.bootstrap`) | Creates `BukkitCraftEngine`, loads dependencies and proxy, **reads `config.yml` + translations** (`setUpConfigAndLocale`, which also creates `BukkitPackManager`), registers a `DATAPACK_DISCOVERY` lifecycle handler → `injectRegistries()`, applies `RuntimePatcher`, migrates legacy world data (26.1+, may sleep 30 s). | `loader/PaperCraftEngineBootstrap.java` L48–74, L197–214 |
| `injectRegistries()` (datapack discovery, or onLoad fallback) | ByteBuddy-generates `CraftEngineBlock`/states, creates `BukkitBlockManager`: **all `custom_N` NMS blocks are registered now**, count = `block.serverside-blocks` (default 2000). Injects loot, features, biome filter, block state providers. | `bukkit/plugin/BukkitCraftEngine.java` L135–167 |
| **onLoad** (`onPluginLoad`) | Inits registries/types (`BukkitBlockBehaviors.init()`, `BukkitItemBehaviors.init()`, …), storage manager, all managers incl. `BukkitWorldManager`. | `BukkitCraftEngine` L170–246; `CraftEngine` L152–206 |
| **onEnable** (STARTUP, *before worlds load*) | Listeners and commands. If `delay-configuration-load: false`: `worldManager.delayedInit()` (registers `WorldInit/Load/ChunkLoad/Unload` listeners), runs `beforeEnableTaskRegistry`, **loads all packs/configs except recipes synchronously**, and runs the delayed tasks. Then `afterEnableTaskRegistry`. | `CraftEngine` L420–484 |
| first tick (`runDelayed`) | `platformDelayedEnable` (async tick task; main tick task on non-Folia). If delayed: `reloadPlugin(Runnable::run, Runnable::run, …)` loads **everything on the main/global thread**, then `worldManager.delayedInit()`. If not delayed: loads recipes. Then furniture/projectile init. | `CraftEngine` L487–538 |
| second tick (nested `runDelayed`) | `callReloadEvent()` → `CraftEngineReloadEvent(isFirstReload=true)`; `isFullyLoaded=true`. | `CraftEngine` L539–543 |

`delay-configuration-load` default `true`. Its comment: "Only disable if you require datapack-generated spawn chunks" (`config.yml` L859–862). Parsed from config at bootstrap (`Config.java` L404).

`CraftEngine.instance().beforeEnableTaskRegistry()` / `afterEnableTaskRegistry()` (@Experimental) accept `PluginTask.create(Runnable, String pluginName[, Priority.head()/tail()/before(p)/after(p)])`. They must be registered **during onLoad**, exceptions are swallowed, and they run inside CE's onEnable just before and after the (non-delayed) config load (`CraftEngine` L935–958, `PluginTaskRegistry`). Useful to write pack files at exactly the right moment.

### 7.2 What TFC must do (default worlds generated at startup)
1. `paper-plugin.yml`: `load: STARTUP` **[MEM]**. CraftBukkit only accepts a `bukkit.yml` default-world generator from an *enabled* plugin ("…is not enabled yet (is it load:STARTUP?)"). CE is also STARTUP (`bukkit/paper-loader/build.gradle.kts` L47). Dependency `CraftEngine: {load: BEFORE, required: true, join-classpath: true}`.
2. CE `config.yml`: `misc.delay-configuration-load: false`. CE parses it in bootstrap, so a value written by our plugin applies from the **next** restart. Ship it as an install step or a first-run restart (see the `dependency-bootstrap` skill). UNVERIFIED: Paper's bootstrapper ordering between two plugins.
3. In our `onLoad()`: write/refresh pack files in `plugins/CraftEngine/resources/tfc/`, then `BlockBehaviors.register(...)`, `ItemBehaviors.register(...)` and any `CommonFunctions`/parsers. CE's `onLoad` has already run because CE loads before us, so the registries exist. CE's `onEnable` (where configs are parsed) runs after all `onLoad`s.
4. In our `onEnable()`: `CraftEngineBlocks.loadedBlocks()` is **already populated** when not delayed, because CE's onEnable precedes ours. Resolve and cache our states then, and **again on every `CraftEngineReloadEvent`**.
5. With `delay=false`, CE registers `WorldInitEvent` before worlds load, so `ensureStorageWorld` → `injectWorldGeneration` happens at `WorldInitEvent` (priority HIGH) before any chunk is generated (`BukkitWorldManager` L331–336, L402–424).

### 7.3 With the default `delay-configuration-load: true` (what goes wrong) [SRC + INF]
- `WorldInitEvent`/`ChunkLoadEvent` listeners are registered only in `delayedInit()` **after** the first-tick reload (`CraftEngine` L519–520). Default worlds and their startup chunks are created before that: no generator injection and no CE states (`loadedBlocks()` empty, `DelegatingBlockState.blockState()` null).
- `delayedInit()` then adapts already loaded worlds and runs `handleChunkLoad` on loaded chunks. Chunks generated before it are **not** in CE storage, unless `sync-custom-blocks-on-chunk-load: true` scans palettes for `custom_N` states (`BukkitWorldManager` L663–732).
- Workaround if `true` must stay: have the TFC world generated later (non-default world created after `CraftEngineReloadEvent(firstReload)`), or call `BukkitAdaptor.adapt(world)` from our own `WorldInitEvent` handler to force the worldgen hook. That does not fix the missing block definitions.

### 7.4 Reload (`/ce reload`, `/ce reload all`) [SRC]
- `ReloadCommand` → `reloadPlugin(scheduler().async(), r -> platform().run(r), …)` (`bukkit/plugin/command/feature/ReloadCommand.java` L40, L68, L83). The **async** phase: `config.load()`, translations, `reloadManagers()` = `unload()`+`load()` of every manager, pack scan, `loadResources` (our factories run again here, possibly multi-threaded), delayed tasks. The **sync** phase (main / Folia global region): sounds, recipes, …, then `CraftEngineReloadEvent(isFirstReload=false)` (`CraftEngine` L304–418).
- `BukkitBlockManager.unload()` (async thread!) sets every custom NMS block's behavior to `EmptyBlockBehavior` and its shape to stone, and sets `DelegatingBlockState.blockState(null)` (`bukkit/block/BukkitBlockManager.java` L120–137). Behaviors are re-bound in `applyPlatformSettings` (L274–330) as the new configs are parsed. **While a reload runs, block callbacks and `getCustomBlockState` silently see "no custom block"**, on every thread including worldgen and region threads [INF].
- After reload: `BlockDefinition` holders are reused, `ImmutableBlockState` objects are **new**, and loaded CE sections keep the **old** objects (§4.6). Old `ImmutableBlockState.behavior()` still points to old behavior instances. `custom_N` ids are stable across reloads because they are cached in `plugins/CraftEngine/cache/custom_block_states.json` (`AbstractBlockManager` L129; `pack/allocator/IdAllocator`), unless block definitions change.
- Plugin reload or disable at runtime: CE refuses and **shuts the server down** (`BukkitCraftEngine` L258–320).

---

## 8. Storage and chunk lifecycle; where TFC should keep per-block data

### 8.1 Mechanics [SRC `bukkit/world/BukkitWorldManager.java`, `bukkit/plugin/injector/WorldStorageInjector.java`]
- **Section injection:** each NMS `LevelChunkSection` (`injection.target: section`, default) or its `PalettedContainer` (`palette`) is replaced by a closed-source wrapper (`FastNMS.createInjectedLevelChunkSection/PalettedContainer`). Every `setBlockState` calls `WorldStorageInjector.compareAndUpdateBlockState(x,y,z,new,prev,holder)`. If the new NMS state is a `DelegatingBlockState`, it writes the CE `ImmutableBlockState` into the CE section, manages BE create/keep/remove, constant renderers, destroy-stage displays and client-light fixes. If it is vanilla, it clears the CE slot and removes the BE and renderer (`WorldStorageInjector` L88–211). This makes **any** write path visible to CE: Bukkit API, NMS, WorldEdit, features, explosions.
- **Chunk load** (`ChunkLoadEvent`, **LOWEST**, region thread): `readChunkAt` (cache/disk), optional palette sync, uninject, and if `restoreCustomBlocksOnChunkLoad` write each stored CE state's `custom_N` NMS state back into the section (light check, constant renderers). Then reinject, `ceChunk.load()`, `activateAllBlockEntities()` (→ `onLoad`, tickers) (L434–439, L633–778).
- **Chunk unload** (`ChunkUnloadEvent`, **HIGHEST**): write the CE chunk if unsaved, uninject, and if `restoreVanillaBlocksOnChunkLoad` (the unload setting despite the field name) replace every CE state in the NMS section with `restoreBlockState()` (visual state; barrier → glass) and mark the vanilla chunk unsaved. Then `unload()` and `deactivateAllBlockEntities()` (→ `onUnload`) (L441–446, L525–589). **Vanilla region files therefore never contain `custom_N` for full chunks.**
- **World save** (`WorldSaveEvent` MONITOR): `CEWorld.saveChunks()` serializes all unsaved loaded CE chunks on the thread that fired the event (L320–329; `CEWorld` L100–112).
- **Settings:** global defaults `chunk-system.restore-vanilla-blocks-on-chunk-unload: true`, `restore-custom-blocks-on-chunk-load: true`, `sync-custom-blocks-on-chunk-load: false` (`config.yml` L782–790). Per-world overrides persist in the world's CE storage via `/ce feature world-settings` (`core/.../world/WorldSettings.java`). The per-world `sync` flag is stored but `handleChunkLoad` reads the **global** `Config.syncCustomBlocks()` (L663) [SRC].
- **IO:** `async-write: true` → `AsyncStorage` serializes on the caller and writes on IO threads (`AsyncStorage` L88–92). `async-read: true` + Paper 1.21.4+ → CE pre-reads CE chunk data on the chunk-loading worker via an advice on `SerializableChunkData#read`, before `ChunkLoadEvent` (`BukkitWorldManager.onChunkDataRead` L176–204). `cache-system: true` → `CachedStorage` (30 s expiring cache, 4096 entries; `CachedStorage`) or `LifecycleCachedStorage` (`cache-mode: lifecycle`, kept until Moonrise releases the chunk holder; needs Paper loader + async-read; falls back to timed). `compression-method` default 4 = LZ4.

### 8.2 Our own per-block data: options compared
| Option | Pros | Cons / evidence |
|---|---|---|
| **A. CE block entity controller** (`EntityBlock` + `saveCustomData/loadCustomData`) | Lifecycle is tied to the block itself: removed when the block is replaced (`onRemove`), survives state changes of the same owner (`preBlockStateChange`), and moves with CE's own storage and migrations. Data is read **before** `ChunkLoadEvent` (async-read) and saved with CE's chunk record. Interaction code gets it via `getBlockEntityAtIfLoaded`. No ticking unless we ask. | Constructor, `loadCustomData` and `saveCustomData` run on IO, worldgen and save threads (§4.1), so controller fields must be safely publishable (immutable/volatile or guarded). Controllers outlive `/ce reload` with stale behavior refs (§4.6). Data lives only in CE files (`<world>/craftengine/`): if CE storage is `none` or the world is blacklisted, it is **lost on unload** (`BukkitStorageAdaptor` L33–38). `DefaultChunkSerializer` writes no record when the chunk has no CE sections (fine, a BE implies a CE block). A BE is created for **every** state of the block, including worldgen-generated ones. Format and API are not in the stable `api` package. |
| **B. Bukkit chunk PDC** (our `NamespacedKey`, packed per-chunk map pos → data) | Fully under our control, stable Paper API, saved atomically with the vanilla chunk, independent of CE reloads. Natural for "snapshot → async compute → commit". | We must keep it in sync with block changes: listen to break/place/explode/piston/physics, or validate lazily ("does the block at pos still match?"). Region-thread access only. Paper serializes the chunk PDC on save. No automatic removal on replacement. |
| **C. Our own region store** (per-region files, our IO thread) | Full control of format, batching and async IO. Best for large lazily-evaluated datasets (soil nutrients, chunk climate). | Most code: our own lifecycle hooks (`ChunkLoadEvent` at NORMAL, after CE's LOWEST), our own crash-consistency handling. |
| **D. Block properties only** | Zero storage, read with the state (also off-thread from snapshots). | Finite and small (each property value multiplies real states, limited to `serverside-blocks`, default 2000, `config.yml` L377–380). |

**Recommendation [INF]:**
- **Devices** (firepit, forge, barrels, crucible, quern, anvil, pots: inventories plus heat timestamps): **A**. Store immutable snapshots (`long lastUpdateTick`, `float heat`, item arrays) and make `saveCustomData` read a consistent snapshot.
- **Player-planted crops:** **A**, with `plantedTick`/`lastTick` (§12) or **B**.
- **Wild worldgen plants, ores, loose rocks:** **D**, no BE. Treat "no data" as "wild/mature".
- **Chunk-scale TFC data** (climate cache, collapse supports): **C** or **B**.

Pick one owner per datum. Never mirror the same value in A and B.

---

## 9. Worldgen integration

### 9.1 How CE hooks generation [SRC `BukkitWorldManager.injectWorldGeneration` L472–512, `handleChunkGenerate` L592–630; JAR nms-helper `v1_21_11`/`v26_3` `InjectedCustomChunkGenerator`]
- On storage install (`previous == null && VersionHelper.hasPaperPatch`, i.e. Paper **and Folia**), CE replaces the generator in `ChunkMap.worldGenContext` (1.21+) with `InjectedCustomChunkGenerator(world, previousGenerator)`. The previous generator is whatever the world had. For a Bukkit `ChunkGenerator` world that is CraftBukkit's `CustomChunkGenerator` [MEM], so **our generator is wrapped**.
- Bytecode (1.21.11) [JAR javap -c]:
  - `fillFromNoise`: `handleChunkGenerate(…, NOISE)` **before** delegating, Runnable chained with `thenApply` after the target future completes.
  - `buildSurface` → SURFACE, `applyCarvers` → CARVER, `createStructures` → STRUCTURE: handle → delegate → `Runnable.run()`.
  - `applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)` (3-arg) → FEATURE: handle → delegate (3-arg) → CE's own custom placed features → Runnable. The 4-arg overload delegates without the hook.
  - 26.3: `buildTerrain(...)` covers NOISE/SURFACE/CARVER (`lambda$buildTerrain$0`).
- `handleChunkGenerate`: `readChunkAt` (cache/disk). If `sections[0]` is not yet injected, it injects **all** sections under `synchronized(sections)` and returns a Runnable that `writeChunkAt`s the CE chunk if unsaved. Stages can be disabled via `chunk-system.generation.{noise,surface,structure,carver,feature}` (all true) (`config.yml` L801–809; `ChunkGenerationStage`).

### 9.2 Bukkit `ChunkGenerator` / `BlockPopulator` writes: **CONFIRMED by code path, test on server**
- `ChunkData.setBlock(x,y,z, BlockData)` → `CraftChunkData` → `ChunkAccess.setBlockState` → `LevelChunkSection.setBlockState` [MEM]. That section object is CE's injected one (target `section` replaces the array element via `sections[i] = injected`; target `palette` swaps the container). So `compareAndUpdateBlockState` records the `DelegatingBlockState` into the CE section, marks the CE chunk unsaved, and the stage Runnable writes it. CraftBukkit calls `generateNoise/Surface/Bedrock/Caves` inside `fillFromNoise/buildSurface/applyCarvers` [MEM], i.e. inside CE's wrappers.
- `BlockPopulator.populate(…, LimitedRegion)` runs inside CraftBukkit's `applyBiomeDecoration(…, vanilla)`, called from the 3-arg one [MEM], i.e. inside the FEATURE wrapper. `LimitedRegion.setBlockData` → `WorldGenRegion.setBlock` → target chunk's section. **Neighbour chunk caveat [INF]:** writes into a *neighbour* chunk (inside the region's border) land in that neighbour's injected section and its `CEChunk` object. That object is only written by the neighbour's own later stage Runnable, or by unload/save after it loads, and only if it is the same object `readChunkAt` returns then. With `cache-system: true` it normally is. With `cache-system: false` the `RegionStorage` returns a new `CEChunk` per read, so neighbour writes could be lost. **Keep `cache-system: true`**, and test it (§9.6).
- Requirements:
  - (1) Paper or Folia (`hasPaperPatch`).
  - (2) CE storage installed before generation (`delay-configuration-load: false`, or a world created later).
  - (3) CE configs loaded before generation, so `CraftEngineBlocks.byId(...)` works and each `custom_N` NMS state has its `ImmutableBlockState` bound (`applyPlatformSettings`).
  - (4) Stages enabled.
  - (5) World not blacklisted and storage not `none` (with `none`, data is in-memory only).
  - (6) Do not use the deprecated `ChunkGenerator.generateChunkData` path (UNVERIFIED whether its legacy chunk-data copy bypasses the injected sections).

### 9.3 Thread safety on worldgen threads [SRC + INF]
- `CraftEngineBlocks.getBukkitBlockData(state)`: reads `state.customBlockState().minecraftState()` (a final-after-load field) and calls Paper `asBlockData()`. Safe off-thread after load. **Cache one `BlockData` per state at startup/reload** and never mutate it (CraftBlockData is mutable).
- `ImmutableBlockState.with/get/cycle`: immutable maps, safe.
- `CraftEngineBlocks.getCustomBlockState(BlockData)`: `instanceof` plus a plain (non-volatile) field read. Safe except during `/ce reload` (null) [§7.4].
- CE's injected write path is designed for these threads (`synchronized(sections)` during injection; CE section writes happen under the vanilla section's own access rules).
- **During `/ce reload` all of the above are unsafe** (bindings nulled). Treat reload during active worldgen as unsupported. Our generator should hold pre-resolved `BlockData` and never call CE lookups per block.

### 9.4 Behaviour binding on generated blocks [SRC + INF]
- Generated blocks are real `custom_N` states. The behavior is bound at the NMS block level (`behaviorHolder`), so callbacks work as soon as the chunk is live. **`onPlace` is not called for `ProtoChunk` writes** [MEM]. Do not rely on `onPlace` to initialise generated blocks: use `onLoad` of a controller, or our own chunk-load catch-up.
- Block entities: for a BE state, `compareAndUpdateBlockState` creates `new BlockEntity(pos, state)` → **our controller constructor on the worldgen thread**, and `chunk.addBlockEntity` does not call `onLoad` (chunk not activated). Its `saveCustomData` runs at the stage Runnable on the worldgen thread. `onLoad` runs later when the chunk loads (`activateAllBlockEntities`).
- `canSurvive` may be called with a `WorldGenRegion` (features like `simple_block` check it). Vanilla post-processing may later call `updateShape` with a real level when the proto chunk is promoted [MEM], so `AbstractCanSurvive`-style behaviors can break generated blocks that "cannot survive".

### 9.5 Datapack / CE features
CE also registers `craftengine:*` configured and placed features. The CE YAML sections `configured_feature`/`placed_feature` place CE blocks via datapack-style features (`BukkitWorldManager` L800+, `PlaceFeatureCommand`). Not needed if TFC worldgen is ours.

### 9.6 Test plan (real server; Paper 1.21.x, Paper 26.x, Folia)
1. **Setup:** fresh world folder, `delay-configuration-load: false`, `cache-system: true`, default storage `linear`. TFC generator as `bukkit.yml` default generator. Our test generator places `tfc:test/marker` (no BE) and `tfc:test/marker_be` (BE, controller writes `tfc:test.created=<thread name>` in its constructor) at deterministic positions per chunk:
   - (a) in `generateNoise` via `ChunkData`;
   - (b) in a `BlockPopulator` via `LimitedRegion`, including positions in the ±8 border, i.e. in the neighbour chunk.
2. **Live check** (command, region thread): for N generated chunks compare `block.getBlockData()` → `CraftEngineBlocks.getCustomBlockState(...)` with `BukkitAdaptor.adapt(world).storageWorld().getBlockStateAtIfLoaded(x,y,z)`. Both must be the marker. `/ce debug is-section-injected` must report true. `/ce debug target-block` should show the custom state and "Block Entity: Valid" for the BE marker.
3. **Persistence:** unload the chunks (teleport away / `/save-all`, wait past the cache TTL), restart the server, reload them. The markers must still be custom (proves CE storage was written; the vanilla file holds only the visual state). With `restore-custom-blocks-on-chunk-load: false` temporarily, the blocks must show as their visual vanilla state, proving restore-on-unload happened.
4. **BE data:** the controller tag survives the restart and the thread name recorded is a worldgen worker. `onLoad` fires once per load.
5. **Neighbour writes:** repeat (b) with `cache-system: false`. If markers in neighbour chunks are lost after restart, document "cache-system must stay true".
6. **Startup chunks:** with `delay-configuration-load: true`, confirm the spawn-area markers are **missing** from CE storage (expected failure, documents the requirement).
7. **Reload during generation:** pre-generate with Chunky while running `/ce reload` and log any `getCustomBlockState == null` or missing markers. Expected: hazards (§7.4).
8. **Folia:** same as 1–5. Additionally assert our commits run on `Bukkit.isOwnedByCurrentRegion(...)`.

---

## 10. Threading and Folia

| Call | Thread requirement | Evidence |
|---|---|---|
| `CraftEngineBlocks.place/remove/isCustomBlock/getCustomBlockState(Block)` | **Owning region thread** (main on Paper). Direct NMS `setBlock`/`getBlockState` with no scheduling. An off-thread `getBlockState` can sync-load chunks. | `CraftEngineBlocks` L231–252, L353–368 |
| `getCustomBlockState(BlockData)`, `getBukkitBlockData`, `byId`, `loadedBlocks`, `ImmutableBlockState` reads | any thread (not during reload) | §9.3 |
| `BukkitAdaptor.adapt(World)` | any (field read). Before full load it may install storage (side effect). | `BukkitAdaptor` L48–60 |
| `CEWorld.getBlockEntityAtIfLoaded(pos[, create])`, controller mutation, `blockEntityChanged` | owning region thread. `create=true` mutates the chunk. | `CEWorld` L188–202; `CEChunk` comments L43–47 ("从区域线程上访问", accessed from the region thread) |
| `CEWorld.getBlockStateAtIfLoaded` | owning region thread recommended (`PalettedContainer` is not thread-safe for resize) [INF] | `CESection`, `PalettedContainer` |
| Block behavior NMS callbacks (`tick`, `randomTick`, `neighborChanged`, `updateShape`, `onPlace`, `useOnBlock`…) | invoked by the server on the owning region thread. `canSurvive`/`updateShape`/`placeMultiState` may also run on **worldgen threads** with a `WorldGenRegion`. | `CropBlockBehavior.canSurvive` L132; javadoc `placeMultiState` "Level / WorldGenLevel" |
| Controller constructor / `loadCustomData` | chunk-IO worker (async-read preload), region thread, or worldgen worker | §4.1 |
| `saveCustomData` | region thread (unload), `WorldSaveEvent` thread, worldgen worker | `AsyncStorage` L88–92; `BukkitWorldManager` L320–329, L614–619 |
| Sync ticker | Paper main; Folia owning region (scheduled per chunk per tick from the global region) | `FoliaCEWorld.syncTick` |
| Async ticker | Bukkit async scheduler, at most one world-async round at a time. No world mutation. | `CEWorld.setTicking` L64–86 |
| `CraftEngineReloadEvent` | main / Folia global region | §2.4 |

CE scheduler [SRC `bukkit/plugin/scheduler/*`]: `CraftEngine.instance().scheduler()` → `platform()` (`BukkitExecutor` / `FoliaExecutor`) with `run(r[, world, chunkX, chunkZ | location | entity])`, `runLater`, `runRepeating`, `runAsyncLater/Repeating`, and `async()` (Executor). On Folia `run(r, world, x, z)` → `RegionScheduler.execute`, and `run(r)` with no world → `GlobalRegionScheduler`. On Paper `run` executes **inline** if already on the main thread. It is not part of the stable API. TFC should use its own scheduler abstraction (see the `paper-folia-threading` skill).

**Folia-specific CE notes:**
- `FoliaCEWorld`/`FoliaCEChunk` exist. `handleWorldLoad` schedules per-chunk catch-up via the region scheduler (`BukkitWorldManager` L350–368). `injectWorldCallback` is skipped on Folia (L420).
- `CEWorld.addAsyncBlockEntityTicker` appends to a plain `ArrayList` / `TickersList`, while `FoliaCEChunk.replaceOrCreateTickingBlockEntity` calls it from region threads. Concurrent region threads could race [INF]. One more reason to avoid async tickers.
- `WorldSaveEvent` → `saveChunks()` iterates **all** loaded CE chunks of the world on the save thread. On Folia this reads chunks owned by other regions [INF], so controllers must tolerate a concurrent `saveCustomData`.

---

## 11. Pitfalls
1. **Wrong NBT class:** the wiki says `net.momirealms.sparrow.nbt.CompoundTag`. Against the published jar it is `net.momirealms.craftengine.libraries.nbt.CompoundTag` [JAR].
2. **Wiki is ahead of releases:** `SleepingBlockEntityTicker`, `subscribeChunkLoad`, `ChunkSubscription` are dev-only (26.10-SNAPSHOT) [SRC dev; JAR 26.9.1].
3. **`delay-configuration-load: true` (default)** means no CE blocks in startup-generated chunks (§7.3). It is read at CE bootstrap, so changing it requires a restart.
4. **`/ce reload` nulls bindings asynchronously** and leaves stale states and controllers (§7.4, §4.6). Re-resolve caches on `CraftEngineReloadEvent`. Avoid reload on production TFC servers.
5. **Never cast `state.behavior()`**. Use `getFirst(Class)`. A `facing`/`axis`/`rotation`/`waterlogged` property silently wraps our behavior in a Dual/Composite.
6. **Composite controllers share the NBT compound.** Namespace keys.
7. **Controller methods run off-thread** (constructor, `loadCustomData`, `saveCustomData`). No Bukkit world calls there.
8. **`onPlace` is not called for worldgen writes.** Initialise generated BEs in `onLoad` (sentinel value).
9. **`CraftEngineBlocks.place` calls `onPlace` itself** after `setBlock`, which can double-fire [INF]. Use `Block#setBlockData(getBukkitBlockData(state), false)` for state changes.
10. **Deprecated 6-arg `CraftEngineBlocks.remove` swaps `movedByPiston`/`dropLoot`** (L344).
11. **`CustomBlockBreakEvent` fires at Bukkit MONITOR** and may then cancel the `BlockBreakEvent`. Our HIGH/HIGHEST break listeners see the event before CE's API event. Prefer Bukkit events [WIKI].
12. **`InteractionResult.SUCCESS` does not cancel the Bukkit event.** Use `SUCCESS_AND_CANCEL` when consuming.
13. **Random ticking:** implementing `RandomTickBlock` turns on vanilla random ticks for those states (`BukkitBlockManager` L354–357). Leave it off for lazy TFC blocks.
14. **Storage `none` / blacklisted worlds** keep CE data in memory only: BE data and custom blocks are lost when chunks unload (`BukkitStorageAdaptor` L33–38).
15. **Factories run multi-threaded** during config load (`multi-threaded-configuration-load: true`).
16. **`custom_N` budget:** `block.serverside-blocks` (2000) limits the total real states. TFC's matrix of blocks × properties must fit, and raising the limit needs a restart (`config.yml` L377–380).
17. **Partially generated (proto) chunks** saved at shutdown contain `custom_N` in vanilla files, because restore-on-unload only handles full chunks via `ChunkUnloadEvent` [INF]. The ids are stable via the id cache, but deleting `plugins/CraftEngine/cache/custom_block_states.json` could remap them.
18. **Plugin reload/disable of CE shuts the server down** (`BukkitCraftEngine` L258–320). Never `/reload`.
19. **Listener priorities:** CE restores blocks on `ChunkLoadEvent` LOWEST and saves/restores on `ChunkUnloadEvent` HIGHEST. Our load listeners should be ≥ LOW and our unload listeners ≤ HIGH to see CE data.
20. **Shaded un-relocated Gson** in the core jar may clash on our compile classpath. Exclude it or order paper-api first.

---

## 12. Complete example: TFC crop with planted timestamp (ours)

> **OURS (illustrative, not from CE).** Compiles against 26.9.1 core+bukkit (+ the `craft-engine-bukkit-proxy` jar only for `LevelProxy`). `TfcCalendar` and `TfcScheduler` are our own abstractions. Lazy design: no ticker and no random tick. Growth is computed from `plantedTick` when the block is interacted with, or when our chunk-load catch-up runs.

YAML (generated by our datagen into `plugins/CraftEngine/resources/tfc/configuration/…`):
```yaml
blocks:
  tfc:crop/barley:
    settings: { hardness: 0, replaceable: false }
    states:
      properties:
        age: { type: int, range: 0~7, default: 0 }
      appearances: { ... }       # see config-generation.md
      variants: { ... }
    behavior:
      type: tfc:crop             # ALWAYS namespaced
      growth_days: 24            # read by our factory
```

```java
package dev.tfp.ce.crop;   // OURS

import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigKeys;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;   // RELOCATED package

public final class TfcCropBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final Key TYPE = Key.of("tfc", "crop");
    private static final String[] GROWTH_DAYS = ConfigKeys.of("growth_days");

    public final IntegerProperty age;
    public final int growthDays;
    private int controllerId;                       // set by CE; index inside Dual/Composite

    private TfcCropBehavior(BlockDefinition def, IntegerProperty age, int growthDays) {
        super(def);
        this.age = age;
        this.growthDays = growthDays;
    }

    /** Call from our plugin's onLoad(), before CE parses configs. */
    public static void register() {
        BlockBehaviors.register(TYPE, (BlockDefinition block, ConfigSection section) ->
                new TfcCropBehavior(block,
                        (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                        section.getInt(GROWTH_DAYS, 24)));   // runs on CE's async config pool: no world access
    }

    // ---- EntityBlock ----
    @Override public BlockEntityController createBlockEntityController(BlockEntity be) { return new TfcCropController(be); }
    @Override public void initControllerId(int id) { this.controllerId = id; }

    // ---- interaction: lazy catch-up, then report ----
    @Override
    public InteractionResult useWithoutItem(UseOnContext ctx, ImmutableBlockState state) {
        CEWorld ce = ctx.getLevel().storageWorld();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = ce.getBlockEntityAtIfLoaded(pos);          // region thread (event thread)
        if (be == null) return InteractionResult.PASS;
        TfcCropController c = be.controller.getAt(TfcCropController.class, this.controllerId);
        if (c == null) return InteractionResult.PASS;
        CropGrowth.catchUp(ctx.getLevel().platformWorld(), pos, be, c);   // may change 'age' (see below)
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** Config lookup that survives /ce reload: resolve via the CURRENT definition, never cache in controllers. */
    public static TfcCropBehavior current(BlockEntity be) {
        return be.blockState.owner().value().defaultState().behavior().getFirst(TfcCropBehavior.class);
    }
}

final class TfcCropController extends BlockEntityController {
    private static final long UNSET = Long.MIN_VALUE;
    // volatile: constructor/load may run on IO or worldgen threads; save may run on the world-save thread
    private volatile long plantedTick = UNSET;
    private volatile long lastEvaluatedTick = UNSET;

    TfcCropController(BlockEntity be) { super(be); }   // NO world access here

    long plantedTick() { return plantedTick; }
    long lastEvaluatedTick() { return lastEvaluatedTick; }

    /** Region thread only. */
    void setEvaluated(long tick) {
        this.lastEvaluatedTick = tick;
        this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
    }

    @Override public void onLoad() {             // region thread; also first activation of worldgen-created BEs
        if (plantedTick == UNSET) {
            long now = TfcCalendar.ticksNow();
            plantedTick = now;
            lastEvaluatedTick = now;
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
        // Lazy catch-up AFTER the chunk finished loading; never setBlock inside ChunkLoadEvent handling.
        TfcScheduler.region(blockEntity.world, blockEntity.pos).runNextTick(() ->
                CropGrowth.catchUpIfValid(blockEntity));
    }

    @Override public void saveCustomData(CompoundTag tag) {      // any thread: only reads volatile longs
        CompoundTag d = new CompoundTag();
        d.putLong("planted", plantedTick);
        d.putLong("last", lastEvaluatedTick);
        tag.put("tfc:crop", d);                                    // namespaced: compound shared with other controllers
    }

    @Override public void loadCustomData(CompoundTag tag) {
        CompoundTag d = tag.getCompound("tfc:crop");
        if (d != null) {
            plantedTick = d.getLong("planted", UNSET);
            lastEvaluatedTick = d.getLong("last", UNSET);
        }
    }

    @Override public void onRemove() { /* drop seeds/yield via our loot; region thread */ }
    // No createBlockEntityTicker/createAsyncBlockEntityTicker overrides -> zero per-tick cost.
}

final class CropGrowth {   // OURS
    /** Region thread. Pure compute of the target age, then commit only if changed. */
    static void catchUp(Object platformWorld, BlockPos pos, BlockEntity be, TfcCropController c) {
        if (!be.isValid()) return;
        TfcCropBehavior cfg = TfcCropBehavior.current(be);
        if (cfg == null) return;
        long now = TfcCalendar.ticksNow();
        int target = Math.min(cfg.age.max,
                (int) ((now - c.plantedTick()) * (cfg.age.max + 1) / (cfg.growthDays * TfcCalendar.TICKS_PER_DAY)));
        // Re-read the state from the world: be.blockState can be stale after /ce reload.
        org.bukkit.World w = (org.bukkit.World) platformWorld;
        org.bukkit.block.Block b = w.getBlockAt(pos.x(), pos.y(), pos.z());
        ImmutableBlockState live = CraftEngineBlocks.getCustomBlockState(b);
        if (live == null || live.owner() != be.blockState.owner()) return;
        if (live.get(cfg.age) != target) {
            // Goes through CE's injected section: same owner -> BE kept, preBlockStateChange called.
            b.setBlockData(CraftEngineBlocks.getBukkitBlockData(live.with(cfg.age, target)), false);
        }
        c.setEvaluated(now);
    }

    static void catchUpIfValid(BlockEntity be) {
        if (!be.isValid()) return;
        BlockEntityController ctl = be.controller;
        TfcCropBehavior cfg = TfcCropBehavior.current(be);
        if (cfg == null) return;
        // controllerId lives on the behavior; for a single-behavior block it is 0
        TfcCropController c = ctl.get(TfcCropController.class, 0);   // first controller of that type
        if (c != null) catchUp(be.world.world().platformWorld(), be.pos, be, c);
    }
}
```
Notes on the example:
- Registration is in our `onLoad()`. The seed item can be the built-in `block_item` pointing to `tfc:crop/barley`. Placement then fires `CustomBlockAttemptPlaceEvent` → `BlockPlaceEvent` → `CustomBlockPlaceEvent`, and the BE is created through the injected section. Because the chunk is active, `onLoad()` runs immediately and stamps `plantedTick`.
- Worldgen-placed crops get a BE created on the worldgen thread. `plantedTick` stays `UNSET` until the chunk first loads, and `onLoad` stamps it. For wild crops that should already be mature, place `age=max` and **do not** give wild variants a BE (separate block id without `tfc:crop`), per §8.2.
- No NMS or proxy is needed in this example. If an NMS callback is required (e.g. `neighborChanged` to pop on soil removal), convert with `LevelProxy.INSTANCE.getWorld(args[1])` (proxy jar) and `LocationUtils.fromBlockPos(args[2])` (bukkit util), or with paperweight casts.

---

## 13. UNVERIFIED / open questions
1. **End-to-end worldgen capture** (§9.2) is inferred from CE source plus nms-helper bytecode plus my memory of CraftBukkit's `CustomChunkGenerator`/`CraftChunkData`/`CraftLimitedRegion`. Not run. Use the test plan in §9.6.
2. **Neighbour-chunk writes from `LimitedRegion` with `cache-system: false`** may be lost [INF]. Unverified.
3. **Paper bootstrapper ordering** between our plugin and CE (can our bootstrap edit CE's `config.yml` before CE's bootstrap reads it?). Unknown, so assume it cannot.
4. **CraftBukkit requires `load: STARTUP` for default-world generators** [MEM]. Verify on 26.x Paper.
5. **Startup chunk generation on 1.21.9+/26.x** (spawn chunks were removed in vanilla; how many chunks Paper/Folia generate during startup) is not checked. It is irrelevant if `delay-configuration-load: false`.
6. **Double `onPlace` from `CraftEngineBlocks.place`** [INF from vanilla `LevelChunk.setBlockState` semantics]. Not tested.
7. **Async ticker list race on Folia** (`CEWorld.pendingAsyncTickingBlockEntities` is an `ArrayList` fed from region threads) [INF].
8. **Stale controllers after `/ce reload`** and `CachedStorage` holding old `CEChunk`s [INF; no remap code found].
9. **Per-BE memory cost** is an estimate only.
10. **Exact Folia thread checks** on `CraftEngineBlocks.place` when called off-region (exception vs silent corruption). Not tested. Treat it as forbidden.
11. **`dev` item-behavior changes** ("物品行为状态"): only `ItemBehavior`/`ItemBehaviorFactory` were diffed (unchanged). Other item APIs may change in 26.10.
12. **PDC storage mode + `saveCustomData` thread:** `PersistentDataContainerStorage.writeChunkAt` writes into the Bukkit chunk PDC. If called from a worldgen thread on a proto chunk it is probably fine, but this was not checked.
