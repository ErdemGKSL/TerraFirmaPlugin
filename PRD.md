# Product Requirements Document: TerraFirmaPlugin

| | |
|---|---|
| Product | TerraFirmaPlugin (working name; final name must be distinct from "TerraFirmaCraft") |
| Status | Draft, planning phase |
| License | EUPL-1.2 (open source) |
| Related | `ARCHITECTURE.md` (how), `docs/porting/ledger.md` (feature status + divergences), `IMAGES.md` (art requests) |

## 1. Vision
Bring the full **TerraFirmaCraft** survival experience to **vanilla Minecraft clients** on Paper/Folia servers: no client mods and no modpack, just join the server and accept the resource pack. Everything TFC adds (geology, climate, seasons, primitive technology, metallurgy, food and nutrition, animal husbandry) is rebuilt server-side, with visuals delivered through CraftEngine, BetterModel and BetterHud.

## 2. Problem
TFC is one of the deepest survival overhauls, but it requires a NeoForge modpack, and that limits players (installation friction, Bedrock/console impossible, version lock-in) and servers (no Paper ecosystem, no Folia scaling). Existing plugin "survival overhauls" are shallow. No plugin reproduces TFC's systems faithfully.

## 3. Target users
| User | Needs |
|---|---|
| **Server owners/admins** | Install in minutes, configure safely, scale to many players (Folia), keep their other Paper plugins |
| **Players** | The TFC experience on a vanilla client: correct mechanics, readable HUD/GUIs, good performance |
| **Addon developers** | A documented API and TFC-compatible data formats, to add content |
| **Contributors** | A codebase where porting a TFC feature is a well-defined, testable workflow |

## 4. Goals and non-goals

**Goals**
- G1. Faithful reproduction of TFC mechanics and numbers. Every deviation is documented (ledger → Divergences).
- G2. Vanilla clients only. All visuals via resource pack + CraftEngine/BetterModel/BetterHud.
- G3. Paper + Folia, MC 1.21.x (1.21.4+) and 26.x, Java 25.
- G4. Minimal main/region-thread load: lazy calendar-based simulation, async compute, async worldgen, async packet work.
- G5. Zero-touch setup: our plugin installs and configures its content in the three client plugins automatically.
- G6. Data compatibility with TFC JSON (recipes, definitions, tags), so TFC data and addon datapacks can be imported.
- G7. Localization: all TFC locales, including Turkish (`tr_tr`).

**Non-goals**
- Spigot support. Bedrock/Geyser parity (best effort only). Client mods.
- Re-implementing TFC's addon ecosystem (only the data-format compatibility in G6).
- Pixel-perfect reproductions of client-only effects that need a mod: custom in-world fluids, custom sky/star map, dynamic tint beyond the biome approach.

## 5. Scope: feature modules

Priority: **P0** = required for the first playable release, **P1** = full TFC parity, **P2** = later/optional.

| # | Module | Key requirements | Priority |
|---|---|---|---|
| M1 | Core infrastructure | Registries from TFC JSON, scheduler abstraction, persistence, config, commands, i18n, auto-installer | P0 |
| M2 | Calendar & climate | TFC calendar (months, seasons, configurable month length), climate model (temperature, rainfall, Köppen), weather, snow/ice accumulation, seasonal colours via biome remap | P0 |
| M3 | World generation | TFC regions, continents, rivers/lakes, rock layers (21 rocks), biomes, surface builders, ore veins (graded), trees/plants/features, caves, volcanoes, hot springs | P0 |
| M4 | Geology & physics | Rock/soil/ore blocks with grades, collapse, landslide, support beams, prospecting (propick), panning, sluice, chisel, groundcover/loose rocks | P0 |
| M5 | Player survival | Food (5 nutrient groups, decay, traits: salted/smoked/brined/pickled/...), thirst and drinking, TFC health scale (e.g. "45/50"), item size/weight and encumbrance, physical damage types | P0 |
| M6 | Heat & fire | Item heat (lazy), fuels, fire starting, firepit/grill/pot (soups, jams), torch/lamp burnout | P0 |
| M7 | Primitive technology | Knapping (stone, clay, fire clay, leather, goat horn), pottery + pit kiln, molds/vessels, log pile + charcoal pit, quern, scraping, drying, thatch, sewing, loom, barrels (sealed recipes, preservation, alcohol), large vessel, composter | P0 |
| M8 | Flora & agriculture | 29 crops with NPK nutrients, farmland hydration, fertilizers, wild/dead crops, 214 plants, trees with timed sapling growth and TFC tree templates, leaves decay/fallen leaves, berry bushes, fruit trees | P0/P1 |
| M9 | Metallurgy | 27 metals and tiers, crucible, alloying ranges, casting, channels + mold table, charcoal forge + bellows, anvil forging minigame with rules/bonuses, welding, bloomery, blast furnace, steel chain, ingot/sheet piles, weathering | P1 |
| M10 | Fauna | ~70 animals: livestock (familiarity, gender, aging, breeding, milk/wool/eggs, nest box), predators, prey, aquatic, pets with commands, horses/camels, fishing, lobster traps, hunting with javelins | P1 |
| M11 | Mechanical power | Axles, gearboxes, clutches, water wheels, windmills, trip hammer, power loom, pumps/pipes | P2 |
| M12 | Glassworking | Blowpipe, glass basin, operations, poured glass | P2 |
| M13 | Tools, armor, combat | Metal tools/tool heads, chisel modes, javelin, scythe, mace, shields, armor with damage resistances | P0/P1 |
| M14 | Building & decoration | Wood/rock/metal decorative families, thatch, wattle, signs, chests, shelves, lamps, candles | P1 |
| M15 | GUIs & guide | All device screens, player tabs (calendar/nutrition/climate) via click outside the inventory, in-game field guide | P0 (core screens) / P1 |

Status per feature is tracked in `docs/porting/ledger.md`.

## 6. Non-functional requirements

| Area | Requirement |
|---|---|
| Performance | Plugin share of region-thread time per tick ≤ 2 ms at 50 players on Paper (target, to be validated). No per-tick loops over loaded blocks/items/players for time-based state. Worldgen throughput within 1.5× of vanilla at equal thread count (target). |
| Scalability | Works on Folia with no region violations. Self-tests pass on Folia. |
| Compatibility | MC 1.21.4+ (1.21.11 tested) and 26.2/26.3. Java 25. CraftEngine ≥ 26.9.1, BetterModel ≥ 3.5.1, BetterHud ≥ 2.0.0 (see `content/compat.yml`). |
| Install UX | One restart after dropping in the jars. Owner edits to installed content are never silently overwritten. Clear messages for missing/incompatible dependencies. |
| Client UX | No missing textures. HUD readable at GUI scales 1–4. GUIs aligned. Resource pack accepted once. |
| Reliability | Data codecs are versioned with migration tests. A corrupted chunk record degrades to defaults, never crashes. |
| Localization | All player-facing text is translatable. Ship every TFC locale. |
| Legal | EUPL-1.2. Attribution to TFC in `NOTICE` and in ported file headers. Third-party asset licenses listed. No TFC branding as product name. |
| Observability | Debug commands (`/tfp debug ...`), spark-friendly thread naming, structured warnings. |

## 7. Milestones

| Milestone | Content | Exit criteria |
|---|---|---|
| **M0: Spikes** | The open questions in `ARCHITECTURE.md` §14: CE blocks from our generator, block-data store choice, datapack biome + packet remap, PacketEvents thread, visual pool budget, inventory-outside button, BetterModel carrier | Written answers + prototype code, decisions recorded in `ARCHITECTURE.md` |
| **M1: Skeleton** | Gradle modules, bootstrap/installer, scheduler, registries + data-load test, datagen v1 (rocks, soils, ores), CI with self-test harness | Server starts on Paper + Folia with the three plugins; CE pack installs; data loads |
| **M2: Living world** | Calendar, climate, worldgen (regions → rocks → surface → ores → trees), seasonal colours | Explore a TFC-like world with correct geology and climate |
| **M3: Stone age** | Survival basics (food, thirst, health HUD), heat, knapping, groundcover, firepit, pottery, pit kiln, log pile, crops basics, collapse | Play from spawn to the copper age |
| **M4: Metal age** | Metallurgy chain, anvil, tools/armor tiers, bloomery, blast furnace | Reach steel |
| **M5: Husbandry** | Fauna with BetterModel models, breeding, products | Raise and breed livestock |
| **M6: Parity** | Mechanical power, glassworking, decoration families, field guide, remaining divergence work | All ledger rows `visual-complete` or a documented divergence |

## 8. Success metrics
- Ledger coverage: % of TFC features `playable` and `visual-complete`.
- Golden-value tests passing against TFC numbers (100% for ported rules).
- MSPT impact under the scripted load test, within budget on Paper and Folia.
- Fresh-install success: a new server reaches "ready" with zero manual config steps.
- Crash/region-violation count in CI self-tests: 0.

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| CraftEngine visual-state pools insufficient for TFC terrain | Missing distinct textures | Repurposed vanilla states + allocation report in CI; entity renderers for rare blocks |
| CE worldgen tracking doesn't work as inferred | Worldgen blocks untracked | M0 spike; fallback: populate CE blocks post-generation on chunk load (budgeted) |
| Folia 26.x still alpha | Instability | Test on Folia 1.21.11 (stable) too; record upstream issues |
| Three client plugins evolve or break APIs | Integration breakage | Adapter modules, compat matrix, pinned versions in CI |
| BetterHud core shader conflicts with other packs | HUD invisible | Document; detect conflicting shaders in the merged pack |
| Scope size (~1700 TFC classes) | Long timeline | Milestones, P0/P1/P2 priorities, porting workflow + skills |
| Licensing questions (EUPL-1.2 with GPL-3.0 dependencies) | Distribution doubts | EUPL compatibility clause; legal review before first release |

## 10. Open product questions
- Final product name, Java package and content namespace (provisional: `tfp`, `dev.tfp`).
- Bedrock (Geyser) players: unsupported, or a degraded mode?
- Server-owner configurability vs TFC defaults: which TFC config options are exposed in v1?
- Field guide form: CE-textured GUI book, written book, or a web guide?
