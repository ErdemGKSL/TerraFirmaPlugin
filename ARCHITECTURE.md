# Architecture

How TerraFirmaPlugin is built: runtime topology, modules, threading model, data flow, persistence, client-visual pipeline and build tooling. Product scope and requirements are in `PRD.md`. Day-to-day rules for agents are in `CLAUDE.md` and the skills in `.claude/skills/`.

Status: **planning**. No production code exists yet. This document is the target design. When implementation deviates, update this file in the same change.

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
| Per-block data (crop planted time, device inventories, heat timestamps) | CE block-entity controller: `integration/craftengine/TfpBlockEntityController<T>` (persisted per chunk by CE, no ticker) | One immutable `T` in a volatile field, `VersionedCodec<T>` → NBT under `tfp:<name>`; unreadable/newer data is kept verbatim and written back |
| Chunk-scale data (climate overrides, support maps, soil nutrients) | Chunk PDC: `paper/blockdata/ChunkDataStore` (`PdcChunkDataStore`; in-memory fake in tests) | `byte[]` per `ChunkDataKey` under `tfp:<id>`; copy on the region thread, decode off-thread. Never per-block state |
| Item state (heat, food, forging, contents) | ItemStack PDC (our namespace) | Base values + timestamps only; derived values never stored |
| Entity state (age, gender, familiarity, variant, sheared, pregnancy) | Entity PDC | BetterModel only persists model id/scale, so we re-apply visuals on tracker creation |
| Player state (thirst, nutrition, intoxication, chisel mode) | Player data store (`paper/player`) | `<plugin>/playerdata/<uuid>.dat` (CRC32-framed, temp file + atomic move, `.bak` fallback). Load at `AsyncPlayerPreLoginEvent` (unreadable or newer data refuses the login, nothing is overwritten); save async on quit and every 5 min; per-player writes ordered, a rejoin waits for the pending save |
| Calendar / world climate offsets | World PDC / plugin data | Written on the global scheduler |
| Chunk climate/rock data | **Not stored**: recomputed from the seed (bounded cache), unless gameplay modified it | |

All codecs carry a version number and have migration tests. The shared layer is `core/persist`: `VersionedCodec<T>` (version key `v`, one migration per older version, newer versions refused with `PersistException.NewerVersion`) over `DataMap`, a neutral NBT-shaped tree with two storage forms: `DataMapBinary` (bytes: player files, chunk PDC) and CE NBT (`NbtDataMaps`).

The global clock is `core/clock/GlobalClockHolder`: one volatile immutable `ClockSnapshot` (player ticks, calendar ticks, month length, running), published by the calendar on the global scheduler (M2), read lock-free anywhere.

## 8. World generation

- `worldgen/` ports TFC's `RegionGenerator` pipeline (continents, climate, biomes, rocks, rivers, lakes), layers, river fractal, rock layers, surface builders, veins, features and structures (tree `.nbt` templates).
- `paper/` exposes it as Bukkit `ChunkGenerator` (noise/surface/caves) + `BiomeProvider` + `BlockPopulator`s. TFC worlds are the server's **default worlds** (Folia can't create worlds at runtime), and the generator is set via `bukkit.yml`.
- CraftEngine blocks are written as `CraftEngineBlocks.getBukkitBlockData(state)`. CE's injected chunk generator records them. This requires CE `misc.delay-configuration-load: false` so CE is ready before spawn chunks generate (installer sets it).
- Biomes: custom biomes shipped as a datapack through the bootstrapper (no API to create biomes). Includes seasonal colour variants for the packet biome remap Seasonal colour variants: `tfp:seasonal/<biome>/t<T>r<R>`, 16 bands per TFC biome (4 x 4 on TFC's colormap indices, `core/climate/SeasonalColour`), generated by `tools/datagen` into `datapack/tfp_seasonal_biomes/`; the variant is chosen from the instant temperature and groundwater, and the season is the resend trigger.

## 9. Client visual pipeline

| Visual | Mechanism |
|---|---|
| Terrain full cubes (rock, soil, ore with distinct grade textures, wood) | CE real blocks; visual = `auto_state` pools (note block, mushroom) + **repurposed vanilla states** that never occur in a TFC world (explicit `state:` + model, real instances hidden via `block_state_mappings`) |
| Small/odd shapes | Visual vanilla state chosen *for its hitbox* (button, carpet, tripwire, snow layer, ladder, barrier, bed...). CE takes collision/outline from the visual state |
| Device contents, piles, racks | CE `entity_renderer` (item/text/block displays), dynamic where needed |
| Items | CE items, 1.21.4 item model definitions. State visuals (heat glow, pan stages) via custom model data set in outgoing packets |
| Tooltips | Lore rewritten in outgoing packets from PDC timestamps (server stack untouched) |
| Entities | BetterModel: invisible vanilla carrier mob (vanilla pathing/physics) + packet item_display bones |
| HUD | BetterHud (health "45/50", thirst, hunger, date/climate, item temperature popup). Vanilla hearts/food/air hidden with transparent sprites in our pack |
| GUIs | Chest-style inventories with CE font-image backgrounds in the title. TFC "player tabs" open on a click outside the inventory window (button drawn by the pack) |
| Grass/foliage/water colour by climate and season | Datapack biomes with colour variants + async biome-id rewrite in chunk packets |
| Particles | Only for explicit actions |
| Not reproducible | In-world custom fluids (vanilla visuals + server logic), custom sky. See ledger divergences |

The resource pack is a single pack built and hosted by CraftEngine. BetterModel's `build.zip` and BetterHud's output are merged into it. Plugin-only 2D art is generated by Claude through the imagegen MCP (`.mcp.json`, codex-imagegen-mcp) and post-processed to exact pixel art (`image-generation` skill); raws and prompts are kept in `art/raw/<id>/`.

### 9.1 Visual budget profiles (decided 2026-10-16)

Other CraftEngine packs (installed by server owners, or written by us later) draw from CE's `auto_state: solid` pool. TFC therefore takes its own visuals from non-pool sources first (identity-duplicate states, vanilla blocks absent from TFC worlds, optionally Nether/End blocks) and limits its solid-pool use. All profiles are implemented. The owner picks one per MC version in config.

**Always on, in every profile:**
- metal blocks and mechanical blocks (clutch, encased axle, ...) are drawn with `entity_renderer` (approved);
- hardened rock shares raw rock's visual;
- ore textures are flattened (base + overlay → one PNG);
- groundcover/small ores use vanilla button states, never the tripwire pool;
- we take from the leaves pool only what TFC leaves need.

| Profile | Adds (cumulative) | Gameplay impact | Our demand | Solid left for others, 1.21.11 (N/E disabled) | Solid left for others, 26.3 (N/E disabled) |
|---|---|---|---|---|---|
| `level-0` | nothing | none | 1881 | 9 (76) | 269 (336) |
| `level-1` | crate/workbench → renderer; wood & stripped_wood axis variants share one visual | none | 1761 | 129 (196) | 389 (456) |
| `level-2` | deposits rock-agnostic (gravel + mineral specks); 9 gem ores (amethyst, diamond, emerald, lapis, opal, pyrite, ruby, sapphire, topaz) shown via exposed-only overlay | none (gems look like rock until exposed, an anti-xray bonus) | 1492 | 398 (465) | **658 (725)** |
| `level-3a` | poor + normal ore grades share a visual (rich stays distinct) | small: grade read via propick/tooltip/loose pieces | 1240 | **650 (717)** | 910 (977) |
| `level-3b` | all ore grades share a visual | grade not visible on the block | 988 | 902 (969) | 1162 (1229) |

**Defaults:** `level-3a` on 1.21.x, `level-2` on 26.x. An owner who needs more room for other packs raises the level; one who wants maximum fidelity lowers it.

Config (`plugins/<us>/config.yml`):
```yaml
visuals:
  profile:                    # per MC version family; level-0 | level-1 | level-2 | level-3a | level-3b
    "1.21": level-3a
    "26": level-2
  solid-reserve-warning: 500  # warn at startup if fewer free solid-pool slots remain for other CE packs
  use-nether-end-blocks: auto # auto = only if the server has no vanilla Nether/End (allow-nether / allow-end off)
```

**Mechanics:**
- `tools/datagen` precomputes one visual allocation per (profile × MC version family × Nether/End off/on) and ships them all in the jar (M1-DGN-04, `dev.tfp.datagen.visuals`):
  - Supply: `tools/datagen/src/main/resources/dev/tfp/datagen/visuals/fullcube-states.csv`, the per-state export of `docs/research/vanilla-fullcube-supply.csv` (same classification; a test checks the per-block counts agree). A state counts for a family only if it is a free full cube in every researched version (1.21: 1.21.4 + 1.21.11; 26: 26.1.2 + 26.3). Terrain-grade only (clean, minor caveat).
  - Order: identity duplicates → Nether/End (when on) → absent-in-TFC clean → absent minor → CE `auto_state: solid` (with a stable share `id`). Non-pool sources go first so the solid pool stays free for other packs; overflow fails datagen.
  - Demand: every emitted appearance; profile rules make an appearance reuse another's look; byte-identical model sets share one state (hardened = raw).
  - Block entries are profile-independent: `state: {template: tfp:visual/<id>}` or `states.appearances: {template: tfp:visual/<id>}`. Each allocation dir `visuals/<family>/<profile>/nether-end-<off|on>/` holds `visual_templates.yml` (those templates), `visual_mappings.yml` (`block_state_mappings`: identity duplicate → its twin if unclaimed, else a family look-alike: stone/dirt/oak_planks are reserved for this, coloured blocks → concrete powder, sandstone → sand) and `suppressed_blocks.json` (M1-DGN-05). `visuals/index.json` lists pool use per allocation. Reports: `reports/visual-allocation-<profile>-<family>.md`.
  - Datapack `datapack/tfp_visuals/` (one for all profiles; tags are global): ambient-sound tags and `#crystal_sound_blocks` minus every vanilla block used as a visual in any allocation; `#impermeable` only if a waterlogged state is ever claimed (none today).
- At `onLoad` the installer writes the selected allocation (`core/visuals/VisualSelection`: family from the running version, `visuals.profile`, Nether/End) into our CE pack as `configuration/visuals/{visual_templates,visual_mappings}.yml`. The pack's fingerprint (installed files + selection, `core/visuals/PackBuild`) is compared with the one recorded after the last *successful* rebuild (`plugins/<us>/install/pack-build.json`); any difference asks for one rebuild. After CraftEngine's first reload, `paper/install/PackRebuildTrigger` requests it through `paper.integration.PackRebuilder` (CE adapter: the `/ce reload all` equivalent) and records the fingerprint on success, so a failed rebuild is retried at the next start.
- At runtime, after every `CraftEngineReloadEvent`, we read the real counts of the CE `solid` pool (`paper.integration.VisualPools`; the installed `mappings.yml` may differ per server) and run the pure `core/visuals/ProfileCheck`:
  - fail with a clear message, naming the next higher profile (the first one that fits), if the selected profile doesn't fit;
  - warn if the free solid slots are below `solid-reserve-warning`.
  - CE's visual-state allocation cache survives restarts and marks every cached share id used, so after a profile switch the previous profile's `tfp_` ids keep their slots. `ProfileCheck.staleClaims` finds them; the CE adapter counts them as ours (`stale`, never "other packs") and drops them from the cache, so they are free from the next CE reload (the switch's pack rebuild) on.
- Vanilla-block suppression (M1-INST-04): `core/visuals/Suppression` decides per creation path from the allocation's `suppressed_blocks.json` + mappings (lava + water → the look-alike, e.g. `stone`; everything else cancelled); `paper/suppression/SuppressionListener` applies it in TFC worlds (form/spread/fade/fertilize/entity-change/place events, drops, loot, crafting, smelting; dispensers and `/setblock`/`/fill` best effort).
- **Switching profiles is safe for existing worlds.** It only changes client-side visual mappings. CE block ids and saved data stay the same. It needs a restart and a pack rebuild, both done automatically.

## 10. Packet layer

- Library: **PacketEvents 2.14.x** (M0-NET-01), as an optional plugin dependency (`packetevents` in `paper-plugin.yml` and `compat.yml`). Without it the server runs without seasonal colours and item tooltips.
- Structure (`paper/packet`): one PacketEvents listener (`PacketEventsLayer`, NORMAL priority) feeds a `PacketHandlerRegistry` (lock-free dispatch by direction + packet type name, per-handler metrics: calls, total/max ns, modified, cancelled, errors). Features implement `PacketHandler` (`onSend`/`onReceive` → `PacketDecision` PASS/MODIFIED/CANCEL). Only `dev.tfp.paper.packet` imports PacketEvents (ArchitectureTest); `PacketLayers` loads it reflectively.
- Responsibilities: biome palette remap (season), lore + custom-model-data injection for items, stripping client-only lore on creative echo (`CreativeReturnHandler` calls every registered `ClientItemDecoration.strip`).
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
- **Manifest:** TFC commit plus aggregate sha256 per input tree and per output tree, no timestamps.
- **Data (M1-DGN-02):** TFC's generated `data/tfc/{tfc,recipe,tags}` plus every `c:`/`minecraft:` tag file in the checkout are exported to `data/**` with `tfc:` → `tfp:`. Two id tables sit at the top of `data/`:
  - `id_map.json`: our blocks (TFC blockstates), items (item models), fluids, entity types and food traits (parsed from `TFCFluids`/`TFCEntities`/`FoodTraits` in the checkout; the parsers fail on unknown registration shapes), plus `external_ids`: the vanilla/NeoForge ids the data uses (`minecraft:` items, blocks, entity types, fluids incl. NeoForge's `minecraft:milk`).
  - `external_tags.json`: tags in the external namespaces `c` (NeoForge common tags) and `minecraft` that the data references but does not define. Datagen never invents their members.
  Both lists of external things are found by running core's own `DataLoader` over the exported tree with a recording id table, so they match exactly what the loader checks. The loader (`KnownIds.fromDataRoot`) accepts an id/undefined tag only if it is listed; an undefined `tfp:` tag is never listed, so it fails. `core` `ContentDataLoadTest` (part of `check`) loads the whole tree this way. The external lists are checked only for consistency (namespace, no duplicates), not against Minecraft's or NeoForge's registries, which are not in the checkout.
- **Assets:** TFC blockstates drive the CE `state(s)` sections and are not copied (vanilla clients never load blockstates for `tfp:` blocks). Two-layer ore models (`tfc:block/ore`, `tfc:block/ore_column`) are flattened into one PNG per ore × rock (Java `javax.imageio`, so no Python in CI) and a plain `cube_all`/`cube_column` model.

## 12. Startup and reload lifecycle

1. **Bootstrap:** datapack discovery (biomes, tags, damage types), Brigadier commands.
2. **`onLoad`:** version/compat gate → install content into CraftEngine and BetterHud folders (hash manifest protects owner edits) → targeted config edits → register CraftEngine behaviours/functions/conditions.
   - **Installer (M1-INST-01/02, `paper/install/Installer`):** copies the jar's `/content/craftengine/tfp` → `plugins/CraftEngine/resources/tfp/` and `/content/betterhud/tfp` → `plugins/BetterHud/packs/tfp/`. Each target holds `.tfp-manifest.json` (sha256 per file as we wrote it, tombstones for files the owner deleted). The per-file rule is the pure `core/install/FileDecision`: untouched → overwrite, owner-edited → ours goes to `<name>.tfp-new`, deleted → not recreated (unless `auto-install.restore-deleted`); a file we no longer ship is deleted only if untouched. Writes are temp + atomic move, only for changed files (CE caches by mtime + size). Opt-outs: `auto-install.craftengine|betterhud: false`, BetterHud `packs/-tfp`. The installer never creates `plugins/CraftEngine/resources/` itself (CE extracts its default packs only while it is absent).
   - **Config edits:** the `dependency-bootstrap` §6 table lives in `core/install/ThirdPartyEdits`; `core/install/YamlKeyEditor` changes single keys/list items line by line (comments and everything else byte-identical), the result must still parse before it is written. What we changed is recorded in `plugins/TerraFirmaPlugin/install/edits.json` (original + ours); a third value set later by the owner is left alone with a warning. `auto-install.config-edits: false` only checks.
   - **Restart / degraded:** `core/install/InstallStatus.decide` (pure): a changed restart-only CE key (`delay-configuration-load`, `cache-system`, `storage-type`, `serverside-blocks`) → restart banner + degraded; a mandatory key the owner kept wrong, or a failed CE install → "setup incomplete" banner + degraded. Degraded means TFC worlds must not be generated in this run (`TfpPlugin.install().status().degraded()`, consulted by the generator from M2). `packRebuild` is set when our CE pack's fingerprint (content + visual selection) differs from the last successful rebuild or a pack setting changed; `PackRebuildTrigger` starts the rebuild once after CraftEngine's first reload (M1-INST-03, §9.1).
3. **CraftEngine onEnable** (after us in order, but it parses configs immediately because `delay-configuration-load: false`).
4. **`onEnable`:** BetterModel event subscriptions (`ModelAssetsEvent`, `CreateEntityTrackerEvent`, `PluginEndReloadEvent`), BetterHud placeholders, listeners, scheduler, registries (async load of the jar's `/content/data`, signals the ready gate), player data store, packet layer.
   - **BetterHud first start (`paper/install/PendingHudEdit`):** BetterHud has no `load:` key, enables after us and writes its `config.yml` only then, so its edits (`default-hud` without `test_hud` and with `tfp_date_hud`, merge/pack-type/self-host) end as `FILE_MISSING` in `onLoad`. If they did, `onEnable` registers a `PluginEnableEvent` listener (plus a `ServerLoadEvent` fallback if BetterHud was already enabled): once, `Installer.applyPending(BETTERHUD_CONFIG)` applies them on the io executor and records them in `edits.json`; if something was applied, `PackRebuildTrigger.requestRebuild` asks for a CE pack build (covered by the planned rebuild if that has not started yet; the CE adapter waits until CE is idle), and BetterHud reloads inside that build (M0-BH-01). We do not reload BetterHud directly when CE is present: on the first start that reload ran while BetterHud/BetterModel were still starting, failed with `GraphicsEnvironment$LocalGE` and broke BetterHud's merge in the next CE build too (hud-paper-* logs). Only without CE does `HudReloader.reload()` (`BetterHudAPI.inst().reload()`) run, unless BetterHud `isOnReload()`. So the demo HUD is gone in the first run, with no restart. Not yet verified in a server. **Merge ordering:** BetterHud adds its zip only from a reload it runs inside CE's build and skips it ("still on reload!") while one is already running (CE's startup `initCachedAssets` starts BetterHud's first, slow reload), so `PackRebuildTrigger` starts every rebuild only once `HudReloader.isReloading()` is false (polled every 0.5 s on the io executor, at most 10 min) and `settled()` follows that build (client-paper-1.21.11: a build started earlier was served with 0 `assets/betterhud/` entries). BetterHud can still start that reload after the wait; if it is still reloading when the build has finished, the build missed the merge, so the rebuild runs again once BetterHud has merged (at most once per run; `settled()` follows that second build; m2 date-paper-1.21.11).
5. **Ready gate:** CE reloaded ∧ BetterModel models ready ∧ registries loaded. BetterHud placeholder registration is synchronous in `onEnable` and is not a gate condition.
6. **`/tfp reload data`** (also bare `/tfp reload`): registries only. **`/tfp reload config`:** config.yml. **`/tfp reload content`:** installer → BetterModel reload → CraftEngine full reload (BetterHud merges inside it); answers "not available" until the installer (M1-INST) exists. Never hot-reload CE itself. Command replies are translated server-side (`paper/i18n/Messages`, `lang/<locale>.properties`, fallback `en_us`).

Details: `.claude/skills/dependency-bootstrap/`.

## 13. Testing architecture

Pyramid: pure unit/property/golden-value tests (core, worldgen; ported TFC tests) → data-load validation → worldgen determinism + jcstress → JMH budgets → MockBukkit for glue → in-server self-test harness via run-paper on {Paper, Folia} × {1.21.11, 26.3} → manual visual checklist with `/tfp debug showcase`. Folia is the primary thread-safety detector. Details: `.claude/skills/plugin-testing/`.

CI (`.github/workflows/ci.yml`, M1-BUILD-03): `check` (TFC fetched at the pinned commit as `TFC_ROOT`; `generateContent` because `content/generated/` is gitignored, `check`, then `verifyContent` as a determinism gate), `content` (up-to-date gate, active only if `content/generated/` is ever committed), then `selftest` = `./gradlew :itest:runSelfTest -Pmc=… -Pplatform=…` over {paper, folia} × {1.21.11, 26.3}. `runSelfTest` (itest, run-paper, plugin ids from `gradle/libs.versions.toml`) runs in `itest/build/selftest-run/<platform>-<mc>/`, refuses to start without the self-test plugin, and fails unless `selftest-report.xml` exists with no failures. The self-test also makes the server process exit 1 when a case failed (2 if no report could be written), via a shutdown hook that halts after the server thread finished stopping. Cases whose plugin or content is missing in that run are reported as skipped with the reason (`SelfTestCase.Skip`), never as passed; the self-test installs its own CraftEngine pack (`tfpitest`, one marker block) the same way the main installer does.

Self-test plugin (M1-TEST-01, `itest`, active only with `-Dtfp.selftest=true`): a Log4j appender on the server's root logger (attached in `onLoad`; `logs/latest.log` scan as fallback) watches for thread-ownership violations (`ThreadAssertionException`, Folia/Paper main-thread checks) and CraftEngine issues for `tfp` (`core.diagnostics.CraftEngineIssueMatcher`); after `ServerLoadEvent` a dedicated thread runs the cases with per-case timeouts (ready gate first; later cases are skipped if it fails), adds the two log cases, writes the JUnit report and shuts the server down from the global region thread. Unit-level Paper glue tests use MockBukkit (`mockbukkit-v1.21`, base `MockServerTest`) plus fakes of our adapter interfaces (M1-TEST-02).

## 14. Open technical questions (spikes before feature work)

1. ~~CraftEngine blocks written from a Bukkit `ChunkGenerator`/`BlockPopulator` are tracked by CE.~~ **Resolved (M0-CE-01):** yes on all four targets, including cross-chunk populator writes, persisting across unload and restart. `chunk-system.cache-system: true` is **mandatory**: with `false`, CE doesn't track worldgen-written blocks at all. Controllers are constructed on worldgen threads. `docs/spikes/M0-CE-01.md`.
2. Per-block data: CE `BlockEntityController` vs our own chunk-PDC store (persistence cost, reload behaviour, Folia safety).
3. A datapack biome returned from `BiomeProvider` works and persists; obtaining biome network ids for the packet remap without NMS.
4. PacketEvents listener thread and chunk biome palette rewriting cost.
5. Total CE state count vs `block.serverside-blocks`, and visual pool budgets per MC version. **Researched** (`docs/research/block-budget-workarounds.md`): supply is 1957 (1.21.11) / 2217 (26.3) opaque full-cube visuals vs ≈1790 core terrain + ≈150 deco demand. In-game check done (M0-VIS-01): every visual source renders on 1.21.11 and 26.3 clients, mappings hide the originals, and runtime pool counts match. Remaining: implement the datagen allocator, measure exposed ore faces per chunk, decide on Nether/End, and test client prediction (buttons, redstone_ore).
6. The fake "outside the inventory" button: how the pack draws it, and which click packet the server receives.
7. BetterModel carrier choices per animal family (hitbox/seat alignment), and baby scale behaviour.
8. Single jar across 1.21.11 and 26.x (Adventure 4 vs 5 binary compatibility).
