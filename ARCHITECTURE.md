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
| Minecraft | 1.21.x (1.21.4+, tested on 1.21.11) and 26.x (26.2/26.3) |
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

- **Sources:** `content/data/**`, mirroring TFC's `data/tfc/tfc/<kind>` definitions (item_heat, food, fuel, item_size, climate_range, deposit, fertilizer, drinkable, fauna, support, knapping_type, ...), `data/tfc/recipe/<type>`, and tags.
- **Loader:** parses off-thread with strict codecs (unknown types fail the load), resolves ids through the datagen-produced item/block id table, and builds an immutable `RegistrySnapshot`.
- **Access:** `Registries.get()` returns the current snapshot (`AtomicReference`). Reload = build new + atomic swap. Callers take one snapshot per operation.
- **Recipes:** our own engine in `core` with TFC's ingredient types (`tfc:and`, `tfc:not_rotten`, `tfc:lacks_trait`, `tfc:heat`, `tfc:fluid_content`, ...) and output modifiers (`tfc:copy_input`, `tfc:add_trait`, `tfc:copy_food`, `tfc:meal`, ...). Plain shaped/shapeless crafting registers through Paper. Everything else is intercepted.
- **Tags:** our own tag registry from TFC tag JSON. Vanilla block tags don't contain CraftEngine blocks.

## 7. State and persistence

| State | Where | Format / notes |
|---|---|---|
| Custom block identity + properties | CraftEngine storage (`<world>/craftengine/`) | Managed by CE, keyed by CE id |
| Per-block data (crop planted time, device inventories, heat timestamps) | **Decision pending spike:** CE `BlockEntityController` (persisted per chunk by CE, sleeping tickers) **or** our per-chunk store in chunk PDC | Compact, versioned codec; (de)serialize off-thread |
| Item state (heat, food, forging, contents) | ItemStack PDC (our namespace) | Base values + timestamps only; derived values never stored |
| Entity state (age, gender, familiarity, variant, sheared, pregnancy) | Entity PDC | BetterModel only persists model id/scale, so we re-apply visuals on tracker creation |
| Player state (thirst, nutrition, intoxication, chisel mode) | Player data store | Async load at pre-login, async save |
| Calendar / world climate offsets | World PDC / plugin data | Written on the global scheduler |
| Chunk climate/rock data | **Not stored**: recomputed from the seed (bounded cache), unless gameplay modified it | |

All codecs carry a version number and have migration tests.

## 8. World generation

- `worldgen/` ports TFC's `RegionGenerator` pipeline (continents, climate, biomes, rocks, rivers, lakes), layers, river fractal, rock layers, surface builders, veins, features and structures (tree `.nbt` templates).
- `paper/` exposes it as Bukkit `ChunkGenerator` (noise/surface/caves) + `BiomeProvider` + `BlockPopulator`s. TFC worlds are the server's **default worlds** (Folia can't create worlds at runtime), and the generator is set via `bukkit.yml`.
- CraftEngine blocks are written as `CraftEngineBlocks.getBukkitBlockData(state)`. CE's injected chunk generator records them. This requires CE `misc.delay-configuration-load: false` so CE is ready before spawn chunks generate (installer sets it).
- Biomes: custom biomes shipped as a datapack through the bootstrapper (no API to create biomes). Includes seasonal colour variants for the packet biome remap.

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
- `tools/datagen` precomputes one visual allocation per (profile × MC version family) and ships them all in the jar.
- At `onLoad` the installer writes the selected allocation into our CE pack, then the installer flow rebuilds the CE pack.
- At runtime, after `CraftEngineReloadEvent`, we read the real free counts of the CE pools (the installed `mappings.yml` may differ per server):
  - fail with a clear message, naming the next higher profile, if the selected profile doesn't fit;
  - warn if the free solid slots are below `solid-reserve-warning`.
- **Switching profiles is safe for existing worlds.** It only changes client-side visual mappings. CE block ids and saved data stay the same. It needs a restart and a pack rebuild, both done automatically.

## 10. Packet layer

- Candidate: PacketEvents 2.14.x (supports 1.21–26.3, Folia). Alternative: Paper's unofficial channel-initializer hook + per-version NMS.
- Responsibilities: biome palette remap (season), lore + custom-model-data injection for items, stripping client-only lore on creative echo.
- Constraints: runs on netty threads; never touches world state; allocation-light; reads only volatile snapshots (calendar, season tables, per-item caches).

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

## 12. Startup and reload lifecycle

1. **Bootstrap:** datapack discovery (biomes, tags, damage types), Brigadier commands.
2. **`onLoad`:** version/compat gate → install content into CraftEngine and BetterHud folders (hash manifest protects owner edits) → targeted config edits → register CraftEngine behaviours/functions/conditions.
3. **CraftEngine onEnable** (after us in order, but it parses configs immediately because `delay-configuration-load: false`).
4. **`onEnable`:** BetterModel event subscriptions (`ModelAssetsEvent`, `CreateEntityTrackerEvent`, `PluginEndReloadEvent`), BetterHud placeholders, listeners, scheduler, packet layer.
5. **Ready gate:** CE reloaded ∧ BetterModel models ready ∧ registries loaded.
6. **`/tfp reload data`:** registries only. **`/tfp reload content`:** installer → BetterModel reload → CraftEngine full reload (BetterHud merges inside it). Never hot-reload CE itself.

Details: `.claude/skills/dependency-bootstrap/`.

## 13. Testing architecture

Pyramid: pure unit/property/golden-value tests (core, worldgen; ported TFC tests) → data-load validation → worldgen determinism + jcstress → JMH budgets → MockBukkit for glue → in-server self-test harness via run-paper on {Paper, Folia} × {1.21.11, 26.3} → manual visual checklist with `/tfp debug showcase`. Folia is the primary thread-safety detector. Details: `.claude/skills/plugin-testing/`.

## 14. Open technical questions (spikes before feature work)

1. CraftEngine blocks written from a Bukkit `ChunkGenerator`/`BlockPopulator` are tracked by CE (Paper and Folia, 1.21.11 and 26.3).
2. Per-block data: CE `BlockEntityController` vs our own chunk-PDC store (persistence cost, reload behaviour, Folia safety).
3. A datapack biome returned from `BiomeProvider` works and persists; obtaining biome network ids for the packet remap without NMS.
4. PacketEvents listener thread and chunk biome palette rewriting cost.
5. Total CE state count vs `block.serverside-blocks`, and visual pool budgets per MC version. **Researched** (`docs/research/block-budget-workarounds.md`): supply is 1957 (1.21.11) / 2217 (26.3) opaque full-cube visuals vs ≈1790 core terrain + ≈150 deco demand. Remaining: implement the datagen allocator, measure exposed ore faces per chunk, and decide on Nether/End.
6. The fake "outside the inventory" button: how the pack draws it, and which click packet the server receives.
7. BetterModel carrier choices per animal family (hitbox/seat alignment), and baby scale behaviour.
8. Single jar across 1.21.11 and 26.x (Adventure 4 vs 5 binary compatibility).
