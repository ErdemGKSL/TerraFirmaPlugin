# Architecture

How TerraFirmaPlugin is built: runtime topology, modules, threading model, data flow, persistence, client-visual pipeline and build tooling. Product scope and requirements are in `PRD.md`. Day-to-day rules for agents are in `CLAUDE.md` and the skills in `.claude/skills/`.

Status: **M0 and M1 done, M2 in progress** (see `plans/README.md`). Production code exists; this document is the target design. When implementation deviates, update this file in the same change.

## Contents
1. Runtime topology
2. Platform and version targets
3. Module layout
4. Core design principles
5. Threading model
6. Data: registries, definitions, recipes
7. State and persistence
8. World generation
9. Client visual pipeline
10. Packet layer
11. Content pipeline (datagen)
12. Startup and reload lifecycle
13. Testing architecture
14. Open technical questions (spikes)

---

## 1. Runtime topology

```
┌───────────────────────────── Paper / Folia server (Java 25) ──────────────────────────────┐
│                                                                                            │
│  CraftEngine ──────── real server blocks (craftengine:custom_N), items, furniture,         │
│      ▲                 block entities, merged resource pack (hosting + delivery)           │
│      │ behaviours, events, getBukkitBlockData()                                            │
│  ┌───┴──────────────────────────── TerraFirmaPlugin ──────────────────────────────────┐    │
│  │ paper/ (bootstrap, listeners, scheduler, storage, GUIs, commands, packet layer)    │    │
│  │   ├── core/      pure rules: calendar, climate, food, heat, forging, recipes...    │    │
│  │   ├── worldgen/  pure TFC world generation (regions, layers, rivers, rocks)        │    │
│  │   └── integration/{craftengine,bettermodel,betterhud}  adapters                    │    │
│  └───┬───────────────────────────────┬────────────────────────────────────────────────┘    │
│      │ ModelAssetsEvent, trackers    │ placeholders, listeners                             │
│  BetterModel ── entity models        BetterHud ── HUD (boss bar + core text shader)        │
│  (packet item_displays on carrier mobs)                                                    │
│                                                                                            │
│  PacketEvents (candidate) ── chunk biome remap, item lore/model-data rewrite               │
└────────────────────────────────────────────────────────────────────────────────────────────┘
            │ one merged resource pack (CE builds; BetterModel + BetterHud merged in)
            ▼
     Vanilla clients (no mods)
```

Server-authoritative everything. Clients are vanilla: every visual is a resource-pack asset bound to vanilla protocol constructs (block states, item models, display entities, boss bar text, container titles).

## 2. Platform and version targets

| Item | Target |
|---|---|
| Server | Paper and Folia (no Spigot) |
| Minecraft | 1.21.11 and 26.3 only (`content/compat.yml`). 1.21.1-1.21.3 are refused by the DATAPACK_DISCOVERY bootstrap; 1.21.4-1.21.10 are unsupported because BetterHud 2.2.0-SNAPSHOT-449 (Java 25 bytecode) never loads on the Paper 1.21.4 remapper |
| Java | **25** everywhere: BetterModel 3.x and BetterHud 2.x ship Java 25 bytecode |
| Plugin format | `paper-plugin.yml` with bootstrapper + loader, `folia-supported: true`, `load: STARTUP` |
| NMS | Avoided. Unavoidable code goes in per-version `nms-*` modules (26.x is unobfuscated, with no reobf) |
| Shared API level | Compile shared code against the lowest supported Paper API; `api-version` = lowest supported |

Reality check (2026-10): Paper supports 26.2/26.3. Folia 26.3 is alpha and Folia 1.21.11 is the last stable line. CI tests both.

## 3. Module layout (Gradle Kotlin DSL)

| Module | Depends on | Contents | Rules |
|---|---|---|---|
| `api` | – | Public API + events for addons | Stable, documented |
| `core` | – | Calendar, climate, food/nutrition, heat, forging rules, alloying, knapping patterns, recipe model + ingredient/output types, registries, codecs | **No** Bukkit/NMS/plugin imports. Immutable data. Thread-safe. |
| `worldgen` | `core` | Pure Java port of TFC worldgen: noise, region generator, layers, rivers, rock layers, chunk data, surface rules, feature placement plans | Deterministic from the seed, concurrent caches, no Bukkit |
| `paper` | `core`, `worldgen`, `api` | Bootstrap/loader, scheduler abstraction, listeners, block-data store, player data, GUIs, commands, Bukkit `ChunkGenerator`/`BiomeProvider`/populators, packet layer | Talks to client plugins only via `integration/*` interfaces |
| `integration/craftengine` | `paper` interfaces | CE behaviours (registered in onLoad), block/item lookups, renderer control, pack install | Only module importing CE |
| `integration/bettermodel` | `paper` interfaces | Model registration, tracker lifecycle, animation/visual-state control | Only module importing BetterModel |
| `integration/betterhud` | `paper` interfaces | Placeholders/listeners, HUD snapshot cache, pack install | Only module importing BetterHud |
| `nms-<ver>` | – | Version-specific internals, if ever needed | Loaded by runtime version check |
| `content` | – | Generated + copied data and assets (TFC JSON, CE pack, BetterHud pack, bbmodels) | Produced by `tools/datagen`, never hand-edited |
| `tools/datagen` | `core` (schemas) | CLI: TFC checkout → `content/` + reports | Deterministic output |
| `itest` | `paper` | In-server self-test plugin | Runs in CI under run-paper |
| `dist` | all | Shaded plugin jar | Relocate Caffeine/JCTools (or use the loader); never shade fastutil/Adventure |

**Adapter wiring (M1-INT-01).** `paper` defines the interfaces (`dev.tfp.paper.integration`: `BlockVisuals`, `CustomBlocks`, `CustomItems`, `EntityModels` (incl. `tint`/`modelCount`; features keep tints in the entity PDC and re-apply them from `onModelCreated`), `HudBridge`, `HudPlaceholders` (our placeholders evaluated through BetterHud's own registry, for diagnostics and the self-test)) and an `IntegrationProvider` SPI. Because `integration/*` depend on `paper`, `TfpPlugin` finds the adapters with `ServiceLoader` (the shaded jar merges their service files; `:dist:verifyIntegrationServices` checks it) and creates one only when its plugin is installed. A missing or failed adapter yields a degraded no-op service and waives its ready signal. `ArchitectureTest` enforces that only `dev.tfp.integration..` imports CE/BetterModel/BetterHud and nothing else depends on an adapter class.

## 4. Core design principles

1. **Lazy, calendar-based simulation.** State that changes over time is stored as *(start/last time, parameters)* and evaluated on access. TFC already does this for food (`creationDate`) and heat (`lastTemperature` + `lastTick`). We extend it to crops, saplings, barrels, drying, charcoal pits, animal aging and devices. Unloaded chunks and inventories cost nothing.
2. **Platform-free rules.** All gameplay math lives in `core`/`worldgen`, so it can be unit-tested and called from any thread.
3. **Faithful numbers.** TFC constants and formulas are kept exactly. Every deviation is listed in `docs/porting/ledger.md` → Divergences.
4. **Server authority, vanilla clients.** All visuals go through CraftEngine/BetterModel/BetterHud or our packet rewriting. There is no client mod.
5. **Adapters at the edges.** Feature code never imports third-party plugin APIs directly.
6. **Decide async, apply sync.** Every reaction to a world event follows: owner-thread callback (snapshot + enqueue, nothing else) → async runner (pure decision on the snapshot, may cascade) → sync runner on the owning region (re-validate, write under a budget, re-queue if stale). Synchronous API answers (e.g. placement state) return a cheap provisional value, and the async pass refines it.
7. **Data-driven.** TFC's JSON schemas are our on-disk format, so TFC data (and addon datapacks) can be re-imported mechanically.

## 5. Threading model

Rule of thumb: **the region/main thread only validates and commits.** Details and code patterns: `.claude/skills/paper-folia-threading/`.

| Work | Thread | Mechanism |
|---|---|---|
| Player interaction validation, block/entity mutation | Owning region thread | Paper events; `Scheduler.region()` / `entity()` |
| Multi-block computations (collapse, landslide, snow/ice, crop checks, rotation networks, tree felling) | Async CPU pool | Snapshot on owner → compute → per-chunk `BlockChange` batches → **budgeted** commit queue per region (≈1–2 ms/tick), re-validated before applying |
| Time-based state (food, heat, growth, devices) | Any (pure) | Evaluated on access from immutable records + calendar |
| World generation | Paper worldgen workers | Bukkit `ChunkGenerator`/`BiomeProvider`/`BlockPopulator`, fully thread-safe |
| Packet rewriting (biome palette, lore, item model data) | Netty event loop | Precomputed lookup tables, bucketed caches, no world access |
| Disk / DB I/O | Virtual threads | `Scheduler.io()` |
| Global state (calendar, season) | Global region scheduler writes; lock-free reads | Volatile/atomic snapshots |
| Animation of entity models | BetterModel's own pool | Driven through the adapter |
| HUD placeholder evaluation | BetterHud async timer | Reads a per-player immutable snapshot only |

Forbidden everywhere (Folia): `BukkitScheduler`, scoreboards, runtime `createWorld`/`unloadWorld`, sync `teleport`, cross-region block/entity access.

## 6. Data: registries, definitions, recipes

- **Sources:** the datagen output `content/generated/content/data/**` (datapack layout, `tfc` renamed to `tfp`): TFC's definition managers under `data/<ns>/tfp/<kind>` (item_heat, food, fuel, item_size, climate_range, deposit, fertilizer, drinkable, fauna, support, knapping_type, lamp_fuel, fluid_heat, item/entity_damage_resistance, worldgen/rock_settings), `data/<ns>/recipe/<type>`, and tags. Vanilla datapack folders (loot_table, worldgen, damage_type tags, ...) stay in the shipped datapack and are skipped by the loader.
- **Loader (`core/registry/DataLoader`):** pure, blocking, run off-thread. Strict JSON (`json/StrictJson`: RFC syntax, duplicate keys fail) and strict codecs (`json/Codec`, `MapCodec`, `ObjectReader`: unknown keys, unknown `type`s and wrong JSON types fail). Every id/tag a file names is recorded as a `Reference` and resolved after the load against the loaded registries, the resolved tags and datagen's `data/id_map.json` (`KnownIds.fromIdMap`; today it covers our item and block ids, not yet fluids, entity types, food traits or vanilla ids). `neoforge:conditions` are honoured (false → file skipped; disabled recipe ids are kept in the snapshot). Every problem is collected; any problem fails the load (`DataLoadException`).
- **Types:** registered under TFC's names (`tfc:and`, `tfc:heating`, ...); the renamed `tfp:` form written by datagen is accepted too. Recipe types and definition kinds that no feature has ported yet decode to a strict `SchemaRecord` (`recipe/RecipeSchemas`, `registry/DefinitionKinds`); each feature replaces its entry with a typed record.
- **Access:** `Registries.get()` returns the current immutable `RegistrySnapshot` (held in `concurrent/Snapshot`, an `AtomicReference` with versions). Reload = build new off-thread + atomic swap; an older reload never replaces a newer one; a failed reload keeps the current snapshot. Callers take one snapshot per operation.
- **Recipes:** our own engine in `core` with TFC's ingredient types (`recipe/ingredient/Ingredient`: vanilla item/tag/list, `neoforge:compound|intersection|difference`, `tfc:and`, `tfc:not_rotten`, `tfc:rotten`, `tfc:has_trait`, `tfc:lacks_trait`, `tfc:heat`, `tfc:fluid_content`) and output modifiers (`recipe/output/ItemStackModifier`, all 22 TFC types), as sealed hierarchies generic over the platform stack type. Item-data effects call `recipe/hooks/*` interfaces that the food/heat/forging systems implement. Plain shaped/shapeless crafting registers through Paper. Everything else is intercepted.
- **Tags:** our own tag registry (`tag/TagResolver` → `TagRegistry`) for block/item/fluid/entity_type tags: nested tags flattened, cycles reported, NeoForge `remove` applied, unresolved required entries fail. Vanilla block tags don't contain CraftEngine blocks.

## 7. State and persistence

> **Decided by M0-CE-02 (`docs/spikes/M0-CE-02.md`):** player-placed or device blocks with state use CE block-entity controllers (CE removes the data with the block, saves with the chunk). Wild worldgen blocks use block properties only. A chunk-PDC store is rejected for per-block data because it leaves orphan entries after explosions, vanilla `setType` and pistons.


| State | Where | Format / notes |
|---|---|---|
| Custom block identity + properties | CraftEngine storage (`<world>/craftengine/`) | Managed by CE, keyed by CE id |
| Per-block data (crop planted time, device inventories, heat timestamps) | CE block-entity controller: `integration/craftengine/TfpBlockEntityController<T>` (persisted per chunk by CE, no ticker) | One immutable `T` in a volatile field, `VersionedCodec<T>` → NBT under `tfp:<name>`; unreadable/newer data is kept verbatim and written back. Temporary exception until the CE pile blocks exist (M2-WTH-03): the hidden states of weather snow/ice piles, which are vanilla placeholders, sit in the chunk PDC `tfp:snow_piles` (`paper/weather/WorldBlocks`), one string per position, rewritten or cleared by every weather write there; orphans after player breaks are accepted (ledger) |
| Chunk-scale data (climate overrides, support maps, soil nutrients) | Chunk PDC: `paper/blockdata/ChunkDataStore` (`PdcChunkDataStore`; in-memory fake in tests) | `byte[]` per `ChunkDataKey` under `tfp:<id>`; copy on the region thread, decode off-thread. Never per-block state |
| Item state (heat, food, forging, contents) | ItemStack PDC (our namespace) | Base values + timestamps only; derived values never stored |
| Entity state (age, gender, familiarity, variant, sheared, pregnancy) | Entity PDC | BetterModel only persists model id/scale, so we re-apply visuals on tracker creation |
| Player state (thirst, nutrition, intoxication, chisel mode) | Player data store (`paper/player`) | `<plugin>/playerdata/<uuid>.dat` (CRC32-framed, temp file + atomic move, `.bak` fallback). Load at `AsyncPlayerPreLoginEvent` (unreadable or newer data refuses the login, nothing is overwritten); save async on quit and every 5 min; per-player writes ordered, a rejoin waits for the pending save |
| Calendar / world climate offsets | World PDC / plugin data | Written on the global scheduler |
| Chunk climate/rock data | **Not stored**: recomputed from the seed (bounded cache), unless gameplay modified it | |

All codecs carry a version number and have migration tests. The shared layer is `core/persist`: `VersionedCodec<T>` (version key `v`, one migration per older version, newer versions refused with `PersistException.NewerVersion`) over `DataMap`, a neutral NBT-shaped tree with two storage forms: `DataMapBinary` (bytes: player files, chunk PDC) and CE NBT (`NbtDataMaps`).

The global clock is `core/clock/GlobalClockHolder`: one volatile immutable `ClockSnapshot` (player ticks, calendar ticks, month length, running), published by the calendar on the global scheduler (M2), read lock-free anywhere.

### 7.1 Device blocks (PAR-INFRA-02)

A TFC device (`DeviceBlock` + `InventoryBlockEntity` + `ICalendarTickable`) is a CraftEngine block whose behaviour is **`tfp:device`** plus a **`DeviceKind<S>`** registered on the Paper side. Feature code never sees CraftEngine:

| Piece | Where | Role |
|---|---|---|
| `DeviceRecord<S>` | `core/device` | Immutable: the kind's state `S`, the fixed slot list (`ItemBlob` = Paper-serialized stack bytes), `lastUpdateTick` (player ticks, stamped when the block entity is created; `NEVER` only when no router was installed then, and the first access stamps). `DeviceRecordCodec`: envelope `{v, last, slots, state}`, the state nested with its own `VersionedCodec`; slot lists are padded, never truncated |
| `CatchUp` | `core/device` | Pure lazy substitute of `ICalendarTickable`: first access stamps, later accesses call the kind's `Advance.advance(record, last, now)` once for the whole gap |
| `DeviceKind<S>` / `DeviceKinds` | `paper/device` | Block id or id prefix (longest wins) → state codec, initial state, slots, per-slot limit (`getSlotStackLimit(slot)`, default 64), `SlotFilter` (`isItemValid`), `Advance`, `DeviceHandler` (useWithItem for either hand, also empty → use for the main hand only, as vanilla `useItemOn`/`useWithoutItem`; attack, placed with the placed stack, neighbourChanged, removed), `RemoveBehaviour` (`DROP`/`NOOP`) |
| `DeviceService` | `paper/device` (module `device`, service `DeviceService`) | Per callback on the region thread: block id via `CustomBlocks` → kind → record from `DeviceStore` → catch-up → handler → write back only if changed. `catchUp(Block)` is the hook for chunk load (PAR-INFRA-11) and GUI open (PAR-INFRA-03). Unreadable records are never overwritten |
| `DeviceInventory<S>` | `paper/device` | Immutable ItemStack view of the slots with TFC/NeoForge insert/extract (`core/itemdata/ItemSlotRules`; an overfilled slot refuses inserts, extract is capped at the max stack size) |
| `tfp:device` behaviour, `DeviceController`, `CraftEngineDeviceStore` | `integration/craftengine/device` | The behaviour gives every state a `DeviceController` (opaque record map under NBT `tfp:device`, a `TfpBlockEntityController<DataMap>`), routes `useOnBlock` (consumed → `SUCCESS_AND_CANCEL`, else `PASS`) and `neighborChanged` (level → world lookup cached weakly); `onLoad` stores a fresh record stamped now when none is stored; `onRemove` routes the last record (inventory drop, pickup delay 0 as TFC's `new ItemEntity`). No ticker, no random tick |
| `DeviceBridge` | `paper/device` | Process-wide meeting point: the CE adapter installs the store when it registers `tfp:device` (onLoad), the module installs the router (onEnable); CE behaviours read it per callback, so `/ce reload` is harmless |

Left click and placement come from Bukkit events (`DeviceListener`). Left click skips creative and denied/cancelled clicks, as vanilla's `Block.attack`. Placement copies the placed stack during the event and runs the handler in a region task after it, so it sees the event's final outcome; the timing against CE's `BlockItemBehavior` (which places before firing `BlockPlaceEvent`) and Folia's region scheduling is checked in game by PAR-INFRA-18. Handlers run synchronously on the region thread, stay short and never change other blocks: anything that changes the world around the device goes through an `AsyncDecisionPipeline`. The test-only `ProbeDevice` kind is registered by the self-test, not by the module. `/tfp debug device` shows the looked-at device. The behaviour registration line in `CraftEngineProvider`, registering `/tfp debug device` and the in-server self-test `device.roundtrip` are PAR-INFRA-18.

## 8. World generation

- `worldgen/` ports TFC's `RegionGenerator` pipeline (continents, climate, biomes, rocks, rivers, lakes), layers, river fractal, rock layers, surface builders, veins, features and structures (tree `.nbt` templates).
- `paper/` exposes it as Bukkit `ChunkGenerator` (noise/surface/caves) + `BiomeProvider` + `BlockPopulator`s. TFC worlds are the server's **default worlds** (Folia can't create worlds at runtime), and the generator is set via `bukkit.yml`.
- Surface heights and biomes (M2-GEN-06): the pure decision is `core/worldgen/height/SurfaceHeights`, TFC's `ChunkHeightFiller` per column: the quart biome layer (`BiomeLayers`, TFC's `createRegionBiomeLayer`, seeded from the shared stream after the chunk data generator), the 7 x 7 biome weights per chunk (`ChunkBiomeSampler`, cached per chunk, sums in ascending biome id instead of TFC's identity-hash order), each biome's height map (`HeightNoise`, `HeightBiomes`), the shore samplers (`ShoreHeights`), the tide clamp, the river samplers weighted by the biome weights, then the cinder cones; the surface height is `(int)` of the result. It also gives TFC's aquifer surface heights and the world biome (`getBiomeExtension`: layer plus river edges) that the `BiomeProvider` and the packet remap read; the surface builders use the column's `localBiomes`. The per-block terrain density is ported (`core/worldgen/density/`): `SurfaceHeights.Column.density` is TFC's `calculateNoiseAtHeight` with the biome carving stage (carved lakes `LakeCarving`, cenotes `CenoteCarving`, weighted by the biome weights), the shore densities (`ShoreDensity`, the column state of each shore sampler), the no-carve zone, the river stage weighted by the river blend weights, the air threshold and the slide; `solid(y)` adds `maxFilledY` and TFC's solid rule. It is pure per column: TFC evaluates it per block and interpolates (4 x 8 x 4 cells) only the cave noises, so no neighbouring chunk is read. `TfcTerrain.fill` writes every block of its own chunk by `solid` (opened blocks: water up to the sea-level block, air above) and builds the surface on the column's terrain top (`terrainTop`, the highest kept block), which the decorations also read; the rock layers, slopes and climate keep reading the integer surface height. Not ported: the noise and noodle caves and the beardifier (M2-GEN-09).
- Ore veins (M2-GEN-10): the pure decision is `core/worldgen/ore/OreVeins` (TFC's cluster vein geometry and weights, read from the datagen-exported vein features in tag order). It runs in the noise stage for the generating chunk's own rock positions only; veins of neighbouring chunks reach in, nothing is written outside the chunk. The density roll and grade pick use a positional random per vein type, so the result does not depend on chunk or position order (ledger divergence). Ore ids are part of the terrain palette and follow the same refuse rule as the rocks.
- Groundcover and deposits (M2-GEN-11): the pure decisions are `core/worldgen/groundcover` (loose rocks, vein indicators, shore groundcover, climate filter) and `core/worldgen/deposit` (soil discs: ore deposits of rivers and lakes, clay, peat). They read the terrain through a `GroundView` of any column (heights, chunk data, biome, surface rock, tide) and return only the generating chunk's blocks; features of the 3 x 3 neighbouring chunks are evaluated to find what reaches in. Each placed feature draws from a positional random per (feature, chunk), with TFC's draws in TFC's order after seeding. `TfcTerrain.fill` applies them right after the noise stage in TFC's step order (deposits, indicators, shore, loose rocks); a groundcover block is written only on a position that is still air or water. Until the CE blocks exist they are vanilla placeholders (floor buttons for the small pieces, as the visual plan says).
- Plants, wild crops and corals (M2-GEN-13): the pure decision is `core/worldgen/plant/Plants`, an interpreter of TFC's own placed and configured feature JSON (exported by datagen) for the parts the port runs (patches, simple/fluid blocks, tall, emergent and submerged plants, wild crops, noisy multiple, coral shapes, the climate/intertidal/shallow-water/no-solid-neighbour modifiers); a feature that needs anything else is left out at load and listed. Each biome's large feature (corals) and surface decoration lists run per chunk with a positional random per (feature, chunk); the features of one chunk see each other's blocks (`PlantLevel`), the 3 x 3 neighbours are evaluated and only the generating chunk's blocks are returned. `TfcTerrain` writes the corals, then the plants, after the trees and before the shore groundcover, re-validating each block against the chunk (`PlantBlocks`); vanilla placeholders until the CE plant blocks exist.
- Volcanoes and hot springs (M2-GEN-14): the pure decisions are `core/worldgen/volcano`. `CinderCones` is TFC's cinder sampler (cell, rarity by the biome at the cell center, cone and crater shape); `SurfaceHeights` applies it as the last height step (after the rivers, as TFC), and every height consumer reads `TfcTerrain.heights`/`heightAt`. `Volcanoes` runs, per chunk and with a positional random per (feature, chunk), the cone biomes' caldera (lava flood fill in the crater) and volcano fissure (lava tube in basalt) at the `tfc:volcano` center, then the biome's hot spring list (spring water, air or lava pools with hardened rock and fissures). The features of one chunk see each other's blocks (`VolcanoLevel`) over a `VolcanoView` of the terrain; the 3 x 3 neighbours are evaluated and only the generating chunk's blocks are returned. `TfcTerrain` writes them after the trees and before the corals and plants (`VolcanoBlocks`: rock kinds through the CE rock palette, the rest vanilla placeholders; tree blocks are kept).
- CraftEngine blocks are written as `CraftEngineBlocks.getBukkitBlockData(state)`. CE's injected chunk generator records them. This requires CE `misc.delay-configuration-load: false` so CE is ready before spawn chunks generate (installer sets it). A chunk generated before CE loaded (the degraded first start, while that setting is still `true`) or with an unknown rock id is refused: left empty and logged (first, then every 256th), never filled with vanilla stone and never thrown, because an exception out of `generateNoise` is a fatal chunk-system error. Paper cannot regenerate chunks (`World.regenerateChunk` is unsupported), so such chunks stay empty.
- Biomes: custom biomes shipped as a datapack through the bootstrapper (no API to create biomes). Includes seasonal colour variants for the packet biome remap Seasonal colour variants: `tfp:seasonal/<biome>/t<T>r<R>`, 16 bands per TFC biome (4 x 4 on TFC's colormap indices, `core/climate/SeasonalColour`: temperature bands even, rainfall bands uneven at indices 128, 179, 217 so the band edges follow the colour map's change), generated by `tools/datagen` into `datapack/tfp_seasonal_biomes/`; the variant is chosen from the instant temperature and groundwater sampled at the colour phase (the calendar month, 12 phases a year: the month's middle day at noon), and a month change is the resend trigger. Phases add no variants (a variant is a band, the phase only picks the band), so the set stays 2,000 biomes (8.4 MB datapack) for any phase count.

## 9. Client visual pipeline

| Visual | Mechanism |
|---|---|
| Terrain full cubes (rock, soil, ore with distinct grade textures, wood) | CE real blocks; visual = `auto_state` pools (note block, mushroom) + **repurposed vanilla states** that never occur in a TFC world (explicit `state:` + model, real instances hidden via `block_state_mappings`) |
| Small/odd shapes | Visual vanilla state chosen *for its hitbox* (button, carpet, tripwire, snow layer, ladder, barrier, bed...). CE takes collision/outline from the visual state |
| Device contents, piles, racks | CE `entity_renderer` (item/text/block displays), dynamic where needed |
| Items | CE items, 1.21.4 item model definitions. State visuals (heat glow, pan stages) via custom model data set in outgoing packets |
| Tooltips | Lore rewritten in outgoing packets from PDC timestamps (server stack untouched) |
| Entities | BetterModel: invisible vanilla carrier mob (vanilla pathing/physics) + packet item_display bones |
| HUD | BetterHud (health "850/850", thirst, hunger, air bar above thirst while under water, date/climate with a thermometer on a plate, item temperature popup). Every bar is two pixel-aligned layers (background frame + empty interior, interior-only fill clipped by BetterHud `split-type: left`) derived from one generated master by `tools/images/hud_layers.py`; layouts use `offset: left` and compensate BetterHud's vertical trim (`plans/hud-layered-redesign.md`, `docs/hud/catalogue.md` §5). Vanilla hearts/food/air hidden with transparent sprites in our pack; BetterHud's demo compass switched off by the installer |
| GUIs | Chest-style inventories with CE font-image backgrounds in the title. TFC "player tabs" open on a click outside the inventory window (button drawn by the pack) |
| Grass/foliage/water colour by climate and season | Datapack biomes with colour variants + async biome-id rewrite in chunk packets |
| Particles | Only for explicit actions |
| Not reproducible | In-world custom fluids (vanilla visuals + server logic), custom sky. See ledger divergences |

The resource pack is a single pack built and hosted by CraftEngine. BetterModel's `build.zip` and BetterHud's output are merged into it. Plugin-only 2D art is generated by Claude through the imagegen MCP (`.mcp.json`, codex-imagegen-mcp) and post-processed to exact pixel art (`image-generation` skill); raws and prompts are kept in `art/raw/<id>/`.

### 9.1 Visual budget profiles (decided 2026-10-16; ores revised 2026-10-22)

Other CraftEngine packs (installed by server owners, or written by us later) draw from CE's `auto_state: solid` pool. TFC therefore takes its own visuals from non-pool sources first (identity-duplicate states, vanilla blocks absent from TFC worlds, optionally Nether/End blocks) and limits its solid-pool use. All profiles are implemented. The owner picks one per MC version in config.

What CraftEngine offers beyond these pools: there is no CE setting that raises the vanilla full-cube cap. The options are entity overlays, `destroy_stages`, Premium culling and the CE client mod, ranked in `docs/research/ce-block-visual-workarounds-2.md`.

**Decision 2026-10-22 (owner): ores are drawn as exposed-face overlays.** Every TFC ore block keeps its CE block id, state,
hardness, tool tier, loot and sounds, but its appearance is the host rock's raw-rock visual (0 extra visual states; ore
demand 1113 → 0). The ore texture is a packet-only `item_display` per exposed face (neighbour is air, fluid or
non-occluding), placed in the neighbour cell and pulled back to 0.01 in front of the face, sent only to viewers within a
4-chunk radius (≈48 m spherical client cutoff). Buried ores look exactly like rock (anti-xray bonus). Expected ≈1.8 k
overlay entities per player typical, ≈7.8 k worst average. Design, budget, data flow (decide async on the CPU pool,
publish on the chunk's region thread, same-tick fast path on the block-change packet), feel checklist and spikes:
`docs/research/ore-entity-renderer-design.md`. Backlog: PAR-WORLD-17, PAR-INFRA-15/16/17, PAR-REL-12. `level-3a`/`3b`
merged only ore grades and are removed.

**Always on, in every profile:**
- ores: host-rock visual + exposed-face overlays (above); grades stay distinct;
- metal blocks and mechanical blocks (clutch, encased axle, ...) are drawn with `entity_renderer` (approved);
- hardened rock shares raw rock's visual;
- groundcover/small ores use vanilla button states (hitbox selector, PAR-INFRA-13), never the tripwire pool;
- we take from the leaves pool only what TFC leaves need.

Demand is all of TFC (datagen today emits 261 visuals in every profile: rock, soil, sand families). "Solid left" = CE
`solid` pool slots left for other packs after our non-pool sources (identity duplicates, absent-in-TFC, and Nether/End
blocks when used) are taken first; value without Nether/End blocks (with them, for servers without those dimensions).

| Profile | Adds (cumulative) | Gameplay impact | Our demand | Solid left, 1.21.11 | Solid left, 26.3 |
|---|---|---|---|---|---|
| `level-0` | nothing | none | 768 | 1122 (1189) | 1382 (1449) |
| `level-1` | crate/workbench → renderer; wood & stripped_wood axis variants share one visual | none | 648 | 1242 (1289) | 1489 (1489) |
| `level-2` | deposits rock-agnostic (gravel + mineral specks) | deposits look the same in every rock | 568 | **1289 (1289)** | **1489 (1489)** |

The shipped allocations count a state only if it is free in every researched version of the family (1.21.4 + 1.21.11;
26.1.2 + 26.3), which leaves slightly less: `level-0` 1113 (1180) / 1317 (1384), `level-1` 1233 (1289) / 1437 (1489),
`level-2` the whole pool.

**Defaults:** `level-2` on 1.21.x and on 26.x (it fits everywhere with the whole solid pool free). `level-0` gives maximum
fidelity and also fits everywhere. **Migration:** `level-3a`/`level-3b` in an existing config are read as `level-2` with a
warning at every start (the owner's value is never rewritten).

Config (`plugins/<us>/config.yml`):
```yaml
visuals:
  profile:                    # per MC version family; level-0 | level-1 | level-2
    "1.21": level-2
    "26": level-2
  solid-reserve-warning: 500  # warn at startup if fewer free solid-pool slots remain for other CE packs
  use-nether-end-blocks: auto # auto = only if the server has no vanilla Nether/End (allow-nether / allow-end off)
ore-overlay:
  cutoff-blocks: 48           # client view_range cutoff; faces are sent for chunks within ceil(cutoff/16)+1
  max-per-player: 8000        # nearest first
  surface-outcrops-full-range: true
```
The config, `ProfileCheck` and the allocator change with PAR-INFRA-15; until it is done the code still carries the
old five levels.

**Mechanics:**
- `tools/datagen` precomputes one visual allocation per (profile × MC version family × Nether/End off/on) and ships them all in the jar (M1-DGN-04, `dev.tfp.datagen.visuals`):
  - Supply: `tools/datagen/src/main/resources/dev/tfp/datagen/visuals/fullcube-states.csv`, the per-state export of `docs/research/vanilla-fullcube-supply.csv` (same classification; a test checks the per-block counts agree). A state counts for a family only if it is a free full cube in every researched version (1.21: 1.21.4 + 1.21.11; 26: 26.1.2 + 26.3). Terrain-grade only (clean, minor caveat).
  - Order: identity duplicates → Nether/End (when on) → absent-in-TFC clean → absent minor → CE `auto_state: solid` (with a stable share `id`). Non-pool sources go first so the solid pool stays free for other packs; overflow fails datagen.
  - Demand: every emitted appearance; profile rules make an appearance reuse another's look; byte-identical model sets share one state (hardened = raw).
  - Block entries are profile-independent: `state: {template: tfp:visual/<id>}` or `states.appearances: {template: tfp:visual/<id>}`. Each allocation dir `visuals/<family>/<profile>/nether-end-<off|on>/` holds `visual_templates.yml` (those templates), `visual_mappings.yml` (`block_state_mappings`: identity duplicate → its twin if unclaimed, else a family look-alike: stone/dirt/oak_planks are reserved for this, coloured blocks → concrete powder, sandstone → sand) and `suppressed_blocks.json` (M1-DGN-05). `visuals/index.json` lists pool use per allocation. Reports: `reports/visual-allocation-<profile>-<family>.md`.
  - Datapack `datapack/tfp_visuals/` (one for all profiles; tags are global): ambient-sound tags and `#crystal_sound_blocks` minus every vanilla block used as a visual in any allocation; `#impermeable` only if a waterlogged state is ever claimed (none today).
- At `onLoad` the installer writes the selected allocation (`core/visuals/VisualSelection`: family from the running version, `visuals.profile`, Nether/End) into our CE pack as `configuration/visuals/{visual_templates,visual_mappings}.yml`. The pack's fingerprint (installed files + selection, `core/visuals/PackBuild`) is compared with the one recorded after the last *successful* rebuild (`plugins/<us>/install/pack-build.json`); any difference asks for one rebuild. After CraftEngine's first reload, `paper/install/PackRebuildTrigger` requests it through `paper.integration.PackRebuilder` (CE adapter: the `/ce reload all` equivalent) and records the fingerprint on success, so a failed rebuild is retried at the next start.
- At runtime, after every `CraftEngineReloadEvent`, we read the real counts of the CE `solid` pool (`paper.integration.VisualPools`; the installed `mappings.yml` may differ per server) and run the pure `core/visuals/ProfileCheck`:
  - fail with a clear message, naming the next higher profile (the first one that fits), if the selected profile doesn't fit; `level-2` is the highest, so if even it does not fit, fail with the shortfall per pool and the options (shrink other CE packs, `use-nether-end-blocks` when the dimensions are off, `/ce clean-cache visual_block_states`), never degrading silently;
  - warn if the free solid slots are below `solid-reserve-warning`.
  - CE's visual-state allocation cache survives restarts and marks every cached share id used, so after a profile switch the previous profile's `tfp_` ids keep their slots. `ProfileCheck.staleClaims` finds them; the CE adapter counts them as ours (`stale`, never "other packs") and drops them from the cache, so they are free from the next CE reload (the switch's pack rebuild) on.
- Vanilla-block suppression (M1-INST-04): `core/visuals/Suppression` decides per creation path from the allocation's `suppressed_blocks.json` + mappings (lava + water → the look-alike, e.g. `stone`; everything else cancelled); `paper/suppression/SuppressionListener` applies it in TFC worlds (form/spread/fade/fertilize/entity-change/place events, drops, loot, crafting, smelting; dispensers and `/setblock`/`/fill` best effort).
- **Switching profiles is safe for existing worlds.** It only changes client-side visual mappings. CE block ids and saved data stay the same. It needs a restart and a pack rebuild, both done automatically.

## 10. Packet layer

- Library: **PacketEvents 2.14.x** (M0-NET-01), as an optional plugin dependency (`packetevents` in `paper-plugin.yml` and `compat.yml`). Without it the server runs without seasonal colours and item tooltips.
- Structure (`paper/packet`): one PacketEvents listener (`PacketEventsLayer`, NORMAL priority) feeds a `PacketHandlerRegistry` (lock-free dispatch by direction + packet type name, per-handler metrics: calls, total/max ns, modified, cancelled, errors). Features implement `PacketHandler` (`onSend`/`onReceive` → `PacketDecision` PASS/MODIFIED/CANCEL). Only `dev.tfp.paper.packet` imports PacketEvents (ArchitectureTest); `PacketLayers` loads it reflectively.
- Responsibilities: biome palette remap (season), lore + custom-model-data injection for items, stripping client-only lore on creative echo (`CreativeReturnHandler` calls every registered `ClientItemDecoration.strip`).
- Seasonal biome remap (M2-SEA-02): `SeasonalBiomeHandler` on `CHUNK_DATA` (rewrite) and `CHUNK_BIOMES` (`/fillbiome`: passes, the chunks are queued for a resend). Per chunk the Netty thread does one lookup of a `SeasonalChunkTable` (per 4x4x4 cell: the vanilla placeholder the server sends and the variant to send; computed on the CPU pool by `SeasonalBiomeRemap.compute` from the seed, the TFC biome and the climate sampled at the middle day of the current month, the colour phase) and swaps only cells that carry the expected placeholder, then writes a fresh palette per section (singleton, list of 1-3 bits, or global at `ceil(log2(registry size))`; PacketEvents' own resize would give a global palette of the wrong width). Network ids come from the user's synchronized biome registry, resolved once per user. A miss passes the packet and queues compute + resend. `paper/climate/SeasonalBiomeService` checks the phase (month) every 20 ticks; at a change (12 a year) it drops the tables and recomputes and resends the chunks in every player's view, nearest first, through `ResendLimiter` (8 chunks/tick, 100-tick cooldown per chunk) into `World.refreshChunk` on the region thread. The variants ship as the bootstrap datapack `tfp_seasonal_biomes` (jar `content/datapack/`).
- Constraints: runs on netty threads; never touches world state; allocation-light; reads only volatile snapshots (calendar, season tables, per-item caches). A throwing handler is counted, logged (rate-limited) and the packet passes. `/tfp debug packets` shows the metrics.

## 11. Content pipeline (datagen)

```
TFC checkout @ pinned commit ──▶ tools/datagen ──▶ content/
   data/tfc/**  (JSON)              │ rename tfc:→tfp:   ├── data/**                    (registries)
   assets/tfc/** (tex/models/...)   │ convert loaders    ├── craftengine/tfp/**         (pack: configuration + resourcepack)
   entity_models/*.bbmodel          │ flatten multipart  ├── betterhud/tfp/**           (HUD YAML + art)
                                    │ allocate visuals   ├── bettermodel/*.bbmodel      (preprocessed)
                                    │ validate           └── reports/ (visual allocation, state count, pack size, unmapped assets)
```

Deterministic and re-runnable. Hand fixes go in `tools/datagen/overrides/`. See `.claude/skills/tfc-asset-migration/`.

- **Location:** `content/` above is the content root `content/generated/content` in the repo, shipped as `/content` in the jar. Datagen owns `data/`, `craftengine/`, `visuals/` (§9.1 allocations), `datapack/` (visual tag edits), `reports/`, `.datagen-manifest.json` and `third-party.json` there and rewrites them wholesale.
- **CLI:** `datagen <tfc-checkout> <content-dir> [--check]` refuses a checkout whose HEAD is not the pinned commit. Gradle: `:tools:datagen:generateContent`, and `:tools:datagen:verifyContent` as the CI up-to-date gate.
- **Worktrees:** `content/generated` may be a symlink to a read-only, content-addressed store entry shared by all git worktrees (`tools/worktree/share-build.sh`, keyed by the hash of every datagen input plus the TFC checkout state). Builds only read it. The `:content` jar packs it directly, with no copy in `build/resources`. `generateContent` replaces the link with a private directory before writing (`-Ptfp.contentDir` redirects the output; the script uses it to build entries) and refuses to write into an entry. Per-commit Gradle outputs (`*/build`, `.gradle/`) are never shared; cross-worktree reuse of those goes through the shared Gradle build cache. See `tools/worktree/README.md`.
- **Manifest:** TFC commit plus aggregate sha256 per input tree and per output tree, no timestamps.
- **Data (M1-DGN-02):** TFC's generated `data/tfc/{tfc,recipe,tags}` plus every `c:`/`minecraft:` tag file in the checkout are exported to `data/**` with `tfc:` → `tfp:`. Two id tables sit at the top of `data/`:
  - `id_map.json`: our blocks (TFC blockstates), items (item models), fluids, entity types and food traits (parsed from `TFCFluids`/`TFCEntities`/`FoodTraits` in the checkout; the parsers fail on unknown registration shapes), plus `external_ids`: the vanilla/NeoForge ids the data uses (`minecraft:` items, blocks, entity types, fluids incl. NeoForge's `minecraft:milk`).
  - `external_tags.json`: tags in the external namespaces `c` (NeoForge common tags) and `minecraft` that the data references but does not define. Datagen never invents their members.
  Both lists of external things are found by running core's own `DataLoader` over the exported tree with a recording id table, so they match exactly what the loader checks. The loader (`KnownIds.fromDataRoot`) accepts an id/undefined tag only if it is listed; an undefined `tfp:` tag is never listed, so it fails. `core` `ContentDataLoadTest` (part of `check`) loads the whole tree this way. The external lists are checked only for consistency (namespace, no duplicates), not against Minecraft's or NeoForge's registries, which are not in the checkout.
- **Assets:** TFC blockstates drive the CE `state(s)` sections and are not copied (vanilla clients never load blockstates for `tfp:` blocks). Two-layer ore models (`tfc:block/ore`, `tfc:block/ore_column`) are flattened into one PNG per ore × rock (Java `javax.imageio`, so no Python in CI) and a plain `cube_all`/`cube_column` model. Planned change (PAR-INFRA-15, §9.1 decision 2026-10-22): ore blocks take the host rock's visual, the 53 overlay textures become one-quad item models for the exposed-face overlays, block items use a `composite` item model, and the flattened PNGs are dropped.

## 12. Startup and reload lifecycle

1. **Bootstrap:** datapack discovery (biomes, tags, damage types), Brigadier commands.
2. **`onLoad`:** version/compat gate → install content into CraftEngine and BetterHud folders (hash manifest protects owner edits) → targeted config edits → register CraftEngine behaviours/functions/conditions.
   - **Installer (M1-INST-01/02, `paper/install/Installer`):** copies the jar's `/content/craftengine/tfp` → `plugins/CraftEngine/resources/tfp/` and `/content/betterhud/tfp` → `plugins/BetterHud/packs/tfp/`. Each target holds `.tfp-manifest.json` (sha256 per file as we wrote it, tombstones for files the owner deleted). The per-file rule is the pure `core/install/FileDecision`: untouched → overwrite, owner-edited → ours goes to `<name>.tfp-new`, deleted → not recreated (unless `auto-install.restore-deleted`); a file we no longer ship is deleted only if untouched. Writes are temp + atomic move, only for changed files (CE caches by mtime + size). Opt-outs: `auto-install.craftengine|betterhud: false`, BetterHud `packs/-tfp`. The installer never creates `plugins/CraftEngine/resources/` itself (CE extracts its default packs only while it is absent).
   - **Config edits:** the `dependency-bootstrap` §6 table lives in `core/install/ThirdPartyEdits`; `core/install/YamlKeyEditor` changes single keys/list items line by line (comments and everything else byte-identical), the result must still parse before it is written. What we changed is recorded in `plugins/TerraFirmaPlugin/install/edits.json` (original + ours); a third value set later by the owner is left alone with a warning. `auto-install.config-edits: false` only checks.
   - **Restart / degraded:** `core/install/InstallStatus.decide` (pure): a changed restart-only CE key (`delay-configuration-load`, `cache-system`, `storage-type`, `serverside-blocks`) → restart banner + degraded; a mandatory key the owner kept wrong, or a failed CE install → "setup incomplete" banner + degraded. Degraded means a TFC chunk would be refused in this run: the generator refuses any chunk asked for before CraftEngine's table is loaded, and refused chunks stay empty (`TfpPlugin.install().status().degraded()`). A restart-only key's first start is therefore cleared in-run once CraftEngine has loaded and no chunk asked before that (`InstallStatus.restartApplied`, `TfpPlugin.restartSettingTookEffect`); a setup problem or a failed install stays degraded until fixed. `packRebuild` is set when our CE pack's fingerprint (content + visual selection) differs from the last successful rebuild or a pack setting changed; `PackRebuildTrigger` starts the rebuild once after CraftEngine's first reload (M1-INST-03, §9.1).
3. **CraftEngine onEnable** (after us in order, but it parses configs immediately because `delay-configuration-load: false`).
4. **`onEnable`:** BetterModel event subscriptions (`ModelAssetsEvent`, `CreateEntityTrackerEvent`, `PluginEndReloadEvent`), BetterHud placeholders, listeners, scheduler, registries (async load of the jar's `/content/data`, signals the ready gate), player data store, packet layer.
   - **BetterHud first start (`paper/install/PendingHudEdit`):** BetterHud has no `load:` key, enables after us and writes its `config.yml` only then, so its edits (`default-hud` without `test_hud` and with `tfp_date_hud`, merge/pack-type/self-host, and `compasses/default_compass.yml` `default: false`) end as `FILE_MISSING` in `onLoad`. If they did, `onEnable` registers a `PluginEnableEvent` listener (plus a `ServerLoadEvent` fallback if BetterHud was already enabled): once, `Installer.applyPending(BETTERHUD_CONFIG)` applies them on the io executor and records them in `edits.json`; if something was applied, `PackRebuildTrigger.requestRebuild` asks for a CE pack build (covered by the planned rebuild if that has not started yet; the CE adapter waits until CE is idle), and BetterHud reloads inside that build (M0-BH-01). We do not reload BetterHud directly when CE is present: on the first start that reload ran while BetterHud/BetterModel were still starting, failed with `GraphicsEnvironment$LocalGE` and broke BetterHud's merge in the next CE build too (hud-paper-* logs). Only without CE does `HudReloader.reload()` (`BetterHudAPI.inst().reload()`) run, unless BetterHud `isOnReload()`. So the demo HUD is gone in the first run, with no restart. Not yet verified in a server. **Merge ordering:** BetterHud adds its zip only from a reload it runs inside CE's build and skips it ("still on reload!") while one is already running (CE's startup `initCachedAssets` starts BetterHud's first, slow reload), so `PackRebuildTrigger` starts every rebuild only once `HudReloader.isReloading()` is false (polled every 0.5 s on the io executor, at most 10 min) and `settled()` follows that build (client-paper-1.21.11: a build started earlier was served with 0 `assets/betterhud/` entries). BetterHud can still start that reload after the wait; if it is still reloading when the build has finished, the build missed the merge, so the rebuild runs again once BetterHud has merged (at most once per run; `settled()` follows that second build; m2 date-paper-1.21.11).
5. **Ready gate:** CE reloaded ∧ BetterModel models ready ∧ registries loaded. BetterHud placeholder registration is synchronous in `onEnable` and is not a gate condition.
6. **`/tfp reload data`** (also bare `/tfp reload`): registries only. **`/tfp reload config`:** config.yml. **`/tfp reload content`:** installer → BetterModel reload → CraftEngine full reload (BetterHud merges inside it); answers "not available" until the installer (M1-INST) exists. Never hot-reload CE itself. Command replies are translated server-side (`paper/i18n/Messages`, `lang/<locale>.properties`, fallback `en_us`).

Details: `.claude/skills/dependency-bootstrap/`.

### 12.1 Feature modules (PAR-INFRA-01)

New gameplay features are wired through `dev.tfp.paper.module.FeatureModule`, not by editing `TfpPlugin` or `TfpCommands`: one class (normally in the feature's own package, public no-argument constructor) plus one appended line in `paper/src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule` (the shaded jar merges service files).

- **Discovery:** `FeatureModules.shared()` loads the modules once per class loader with `ServiceLoader`, in file order. The bootstrapper's `/tfp` tree and `TfpPlugin` share that instance. A broken line, an invalid id (`[a-z0-9_]+`) or a duplicate id costs only that module and is logged at enable.
- **Debug commands (bootstrap):** `debugCommands(DebugTree)` adds `/tfp debug <literal>` nodes; `TfpCommands` merges them after the built-ins, which win a name clash (the module literal is skipped and reported). Commands made with `DebugTree.run`/`player` answer `command.not_enabled` while the plugin or the module is off and reply like the built-ins (translated, the console waits up to 2 s).
- **Enable (`onEnable`):** after the plugin's own services and the packet handler registry exist, before the packet layer is installed. `ModuleContext` gives the plugin, logger, `Scheduler`, `Integrations`/`IntegrationContext`, `RegistryService`, `ConfigService`, `Messages`, `PlayerDataStore`, `ChunkDataStore`, the clock, the HUD cache, the packet registry, `listen(Listener)`, `regions(World)` and `pipeline(...)` (an `AsyncDecisionPipeline` on the CPU pool: decide async, apply sync) and `ModuleServices`, a typed lookup in which the plugin provides `ItemFoodHooks`, `FoodDefinitionsCache`, `ItemHeatHooks`, `ItemHeater` and `ItemHeatDefinitionsCache`, and a module may provide services for the modules after it. A module whose `enable` throws stays off; the others continue.
- **Disable (`onDisable`):** enabled modules in reverse order, before the integrations, calendar, player data and scheduler stop.
- **Worked example:** `FoodModule` registers M3-SURV-01's `FoodMergeListener` on the provided `ItemFoodHooks`. Features wired before PAR-INFRA-01 stay in `TfpPlugin` until they are touched.

## 13. Testing architecture

Pyramid: pure unit/property/golden-value tests (core, worldgen; ported TFC tests) → data-load validation → worldgen determinism + jcstress → JMH budgets → MockBukkit for glue → in-server self-test harness via run-paper on {Paper, Folia} × {1.21.11, 26.3} → manual visual checklist with `/tfp debug showcase`. Folia is the primary thread-safety detector. Details: `.claude/skills/plugin-testing/`.

CI (`.github/workflows/ci.yml`, M1-BUILD-03): `check` (TFC fetched at the pinned commit as `TFC_ROOT`; `generateContent` because `content/generated/` is gitignored, `check`, then `verifyContent` as a determinism gate), `content` (up-to-date gate, active only if `content/generated/` is ever committed), then `selftest` = `./gradlew :itest:runSelfTest -Pmc=… -Pplatform=…` over {paper, folia} × {1.21.11, 26.3}. `runSelfTest` (itest, run-paper, plugin ids from `gradle/libs.versions.toml`) runs in `itest/build/selftest-run/<platform>-<mc>/`, refuses to start without the self-test plugin, and fails unless `selftest-report.xml` exists with no failures. The self-test also makes the server process exit 1 when a case failed (2 if no report could be written), via a shutdown hook that halts after the server thread finished stopping. Cases whose plugin or content is missing in that run are reported as skipped with the reason (`SelfTestCase.Skip`), never as passed; the self-test installs its own CraftEngine pack (`tfpitest`, one marker block) the same way the main installer does.

Self-test plugin (M1-TEST-01, `itest`, active only with `-Dtfp.selftest=true`): a Log4j appender on the server's root logger (attached in `onLoad`; `logs/latest.log` scan as fallback) watches for thread-ownership violations (`ThreadAssertionException`, Folia/Paper main-thread checks) and CraftEngine issues for `tfp` (`core.diagnostics.CraftEngineIssueMatcher`); after `ServerLoadEvent` a dedicated thread runs the cases with per-case timeouts (ready gate first; later cases are skipped if it fails), adds the two log cases, writes the JUnit report and shuts the server down from the global region thread. Unit-level Paper glue tests use MockBukkit (`mockbukkit-v1.21`, base `MockServerTest`) plus fakes of our adapter interfaces (M1-TEST-02).

## 14. Open technical questions (spikes before feature work)

1. ~~CraftEngine blocks written from a Bukkit `ChunkGenerator`/`BlockPopulator` are tracked by CE.~~ **Resolved (M0-CE-01):** yes on all four targets, including cross-chunk populator writes, persisting across unload and restart. `chunk-system.cache-system: true` is **mandatory**: with `false`, CE doesn't track worldgen-written blocks at all. Controllers are constructed on worldgen threads. `docs/spikes/M0-CE-01.md`.
2. Per-block data: CE `BlockEntityController` vs our own chunk-PDC store (persistence cost, reload behaviour, Folia safety).
3. A datapack biome returned from `BiomeProvider` works and persists; obtaining biome network ids for the packet remap without NMS.
4. PacketEvents listener thread and chunk biome palette rewriting cost.
5. Total CE state count vs `block.serverside-blocks`, and visual pool budgets per MC version. **Researched** (`docs/research/block-budget-workarounds.md`): supply is 1957 (1.21.11) / 2217 (26.3) opaque full-cube visuals vs ≈1790 core terrain + ≈150 deco demand. In-game check done (M0-VIS-01): every visual source renders on 1.21.11 and 26.3 clients, mappings hide the originals, and runtime pool counts match. Remaining: measure exposed ore faces per chunk and the overlay entity cost (PAR-WORLD-17, S1; ores move to overlays per the §9.1 decision of 2026-10-22), decide on Nether/End, and test client prediction (buttons, redstone_ore).
6. The fake "outside the inventory" button: how the pack draws it, and which click packet the server receives.
7. ~~BetterModel carrier choices per animal family (hitbox/seat alignment), and baby scale behaviour.~~ **Decided (M5-ENT-01):** one vanilla carrier per TFC living type in `core/src/main/resources/dev/tfp/core/animal/carriers.tsv` (closest hitbox and movement among mobs that do not attack players or change blocks; TFC and carrier hitboxes and the delta per row, deltas above 0.25 blocks flagged with a note). Scale: the carrier's SCALE attribute base is TFC's `AGE_SCALES[size-1]` (as `setGeneticSize`), BetterModel's `ModelScaler.entity()` reads it, and the tracker multiplies by TFC's render scale times 0.5 for a child; the carrier's vanilla baby flag (age-locked) follows TFC's `isBaby`, which halves its hitbox. State lives in the carrier PDC `tfp:animal`; the stage is derived lazily on a staggered 20-tick entity task and on model creation. Seat alignment for mounts stays open (M5-MNT-01).
8. Single jar across 1.21.11 and 26.x (Adventure 4 vs 5 binary compatibility).
