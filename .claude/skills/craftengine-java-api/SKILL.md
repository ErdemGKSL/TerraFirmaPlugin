---
name: craftengine-java-api
description: Writing the Java side of the CraftEngine integration (integration/craftengine). Gradle coordinates and relocated classes, the stable API (CraftEngineBlocks/Items/Furniture/Images, BukkitAdaptor, events), custom BlockBehaviors (callbacks, factories, registration in onLoad), block-entity controllers for TFC devices and crops (persistence, which threads call them, no-ticker lazy evaluation), where per-block data should live, CE's startup sequence and why delay-configuration-load must be false, /ce reload hazards, chunk storage lifecycle and listener priorities, placing CE blocks from our ChunkGenerator/BlockPopulator, and the thread requirement of every call on Paper/Folia. Use this whenever writing or reviewing Java that touches CraftEngine, registering behaviours, implementing a device/crop controller, wiring worldgen output to CE blocks, or debugging startup order, lost block data or wrong-thread errors involving CraftEngine.
---

# CraftEngine Java API (integration/craftengine)

Source: `references/research-report.md` (CE source `b3079c5` + published jars **26.9.1**, javap-verified). §-numbers refer to it. A complete worked example (TFC crop with a planted timestamp and no ticker) is in §12. Read it before writing the first behaviour.

## Dependency (§1)
```kotlin
repositories { maven("https://repo.momirealms.net/releases/") }
dependencies {
    compileOnly("net.momirealms:craft-engine-core:26.9.1")    // 26.9.2 isn't published yet
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.1")
}
```
- Relocations that appear in signatures: NBT is **`net.momirealms.craftengine.libraries.nbt.CompoundTag`** (the wiki's `sparrow.nbt` won't compile). Adventure is relocated too, so never exchange `Component`s with Paper.
- The core jar shades un-relocated Gson. Order paper-api first, or exclude it.
- Only the `api` package is stable. Behaviours, `CEWorld` and controllers are usable but may change: keep all of it inside `integration/craftengine`.
- The wiki is ahead of releases: `SleepingBlockEntityTicker` and `subscribeChunkLoad` are dev-branch only (26.10-SNAPSHOT). Don't use them until they're in a pinned release.

## Startup: the non-negotiables (§7)
TFC worlds are the default worlds, generated at startup:
1. CE `config.yml`: `misc.delay-configuration-load: false`. CE reads it in its **bootstrap**, so a value our installer writes takes effect on the **next** restart (the `dependency-bootstrap` first-run flow handles this).
2. `paper-plugin.yml`: we are `load: STARTUP`; `CraftEngine: {load: BEFORE, required: true, join-classpath: true}`.
3. In our **`onLoad()`**: write/refresh pack files, then `BlockBehaviors.register(...)`, `ItemBehaviors.register(...)`, functions/conditions/element types. CE's registries exist by then, and CE parses configs in its `onEnable`.
4. In `onEnable()`: `CraftEngineBlocks.loadedBlocks()` is already populated. Resolve and cache our `ImmutableBlockState`s and `BlockData`s, and re-resolve them on **every `CraftEngineReloadEvent`**.

## `/ce reload` is hazardous (§7.4, §4.6)
Reload unbinds every custom block from an **async** thread. During it, `getCustomBlockState` returns null and callbacks see an empty behaviour. Afterwards, loaded chunks keep the **old** state objects and controllers keep old behaviour references. Therefore:
- controllers hold **no behaviour/config references**. They look up tunables in our own registry by block id;
- our code tolerates null lookups;
- production guidance: restart, don't reload. Never hot-reload or disable CE (it shuts the server down).

## Custom block behaviour (§3)
- Extend CE's Bukkit block behaviour base. The factory has the form `(BlockDefinition block, ConfigSection section) -> behaviour`. Register with `BlockBehaviors.register(Key.of("tfp", "crop"), FACTORY)`. Use it in YAML as `type: tfp:crop`.
- NMS-level callbacks receive `(Object thisBlock, Object[] args)`; the arg layouts are in report §3.2. Higher-level hooks: `updateStateForPlacement`, `useOnBlock`, `useWithoutItem`, `canBeReplaced`.
- Find our behaviour with `state.behavior().getFirst(TfpCrop.class)`. **Never cast**: `facing`/`axis`/`waterlogged` silently wrap ours in a composite.
- Don't implement `RandomTickBlock` for lazy TFC blocks (it turns on vanilla random ticks).
- `onPlace` is **not** called for worldgen writes. Initialize generated block data in the controller's `onLoad`, or in our chunk-load catch-up.
- Use `SUCCESS_AND_CANCEL` to consume an interaction (`SUCCESS` doesn't cancel the Bukkit event).

## Block entities (controllers) for devices and crops (§4)
- A behaviour implementing `EntityBlock` gives every state of that block a `BlockEntity` with our `BlockEntityController`. Data persists as NBT in CE's chunk record (`<world>/craftengine/`).
- **Threads:** the constructor, `loadCustomData` and `saveCustomData` run on chunk-IO, worldgen, world-save **or** region threads. Keep controller state as immutable snapshots / volatile fields, and never call Bukkit world APIs there. `onLoad`/`onUnload`/`onRemove`/`preBlockStateChange` run on the region thread. `onRemove` is where contents drop.
- Composite controllers share one `CompoundTag`, so **namespace our keys** (`tfp:crop`).
- **No ticker = no per-tick cost.** TFC crops/devices store `plantedTick` / `lastUpdateTick` and catch up lazily: on interaction, in our `ChunkLoadEvent` listener (priority ≥ LOW, after CE restores at LOWEST), or via our scheduler for devices that are actively burning. A sync ticker on Folia costs one region task per ticking chunk per tick, so reserve it for the few blocks doing visible work. Async tickers must not mutate the world (and race on Folia), so avoid them.
- Lookup from outside (region thread): `BukkitAdaptor.adapt(world).storageWorld().getBlockEntityAtIfLoaded(pos)`, then `be.controller.let(TfpCropController.class, id, c -> ...)`. After mutating: `world.blockEntityChanged(pos)`.

## Where per-block data lives (§8.2; decision recorded in ARCHITECTURE spike #2)
| Data | Store |
|---|---|
| Devices (inventories, heat timestamps), player-planted crops | **CE block-entity controller** |
| Wild worldgen plants, ores, loose rocks | **Block properties only**. No BE; "no data" means wild/mature. |
| Chunk-scale data (climate cache, collapse supports, soil nutrients) | Our chunk PDC or our own region store |
Exactly one owner per datum. Never mirror the same value in two stores. With CE storage `none` or blacklisted worlds, BE data is lost on unload, so the installer must not set those for TFC worlds.

## Worldgen (§9)
- CE wraps the world's chunk generator (Paper and Folia), including our Bukkit `ChunkGenerator`, and injects sections before NOISE/SURFACE/CARVER/STRUCTURE/FEATURE. **Writing `CraftEngineBlocks.getBukkitBlockData(state)` via `ChunkData.setBlock` or `LimitedRegion.setBlockData` is recorded** (confirmed by code path; the test plan in §9.6 must still pass on a real server).
- Pre-resolve one `BlockData` per state at startup/reload, and never call CE lookups per block in the generator. Treat `/ce reload` during generation as unsupported.
- Keep CE `chunk-system.cache-system: true`, so that populator writes into *neighbour* chunks aren't lost. Don't use the deprecated `generateChunkData`.
- BE blocks placed in worldgen construct our controller **on the worldgen thread**. Prefer property-only blocks for generated content.

## Thread requirements (§10)
| Call | Thread |
|---|---|
| `CraftEngineBlocks.place/remove/isCustomBlock/getCustomBlockState(Block)`, `getBlockEntityAtIfLoaded`, controller mutation | **Owning region thread** |
| `getCustomBlockState(BlockData)`, `getBukkitBlockData`, `byId`, `loadedBlocks`, `ImmutableBlockState.with/get` | Any thread (not during reload) |
| Behaviour NMS callbacks | Region thread; `canSurvive`/`updateShape` may also run on worldgen threads with a `WorldGenRegion` |
| `CraftEngineReloadEvent` | Main / Folia global region |
For state changes prefer `block.setBlockData(cachedData, false)`: `CraftEngineBlocks.place` calls `onPlace` itself and may double-fire. Avoid the deprecated 6-arg `remove` (its arguments are swapped).

## Listener priorities (§8.1, §11)
CE restores custom blocks on `ChunkLoadEvent` **LOWEST** and saves/restores on `ChunkUnloadEvent` **HIGHEST**. Our load listeners should be ≥ LOW and our unload listeners ≤ HIGH. `CustomBlockBreakEvent` fires from CE's MONITOR handler, so prefer Bukkit events for gameplay logic.

## Registries we use (§6)
`BlockBehaviors`, `ItemBehaviors`, `BlockEntityElementConfigs`, `BlockEntityTintSources`, `CommonFunctions`, `CommonConditions`, `EventTrigger`, `LootFunctions`, `ItemProcessors`, `TemplateArguments`. All are static `register(Key, factory)` calls, made in `onLoad`.
