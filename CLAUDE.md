# TerraFirmaPlugin

A from-scratch reimplementation of the **TerraFirmaCraft** mod (TFC, MC 1.21.1 NeoForge) as a server-side plugin. Vanilla clients get every visual through **CraftEngine** (blocks, items, furniture, GUI textures, resource pack), **BetterModel** (entity models) and **BetterHud** (HUD).

The repository is in the planning phase: no production code yet. Skills in `.claude/skills/` hold the working knowledge. Load the relevant one before acting.

## Locked decisions

| Topic | Decision |
|---|---|
| Platform | **Paper + Folia only** (no Spigot). Use Paper-specific APIs freely. Every feature must run on Folia. |
| MC versions | **1.21.x and 26.x**. In practice: 1.21.4+ (BetterModel/BetterHud floor), tested on 1.21.11 + 26.2/26.3. Compile shared code against the lowest supported Paper API and keep `api-version` at that lowest version. |
| Language / build | **Java 25 everywhere** (toolchain and server runtime, 1.21.x servers included), because BetterModel 3.x and BetterHud 2.x ship Java 25 class files and require Java 25. Gradle Kotlin DSL multi-module. Avoid NMS; isolate unavoidable NMS per MC version. |
| Platform reality (2026-10) | Paper supports 26.2/26.3. Folia 26.3 is alpha and Folia 1.21.11 is the last stable line. Test both; log upstream Folia issues in the ledger. |
| License | **EUPL-1.2** (same as TFC). TFC code/data/assets may be reused with attribution. Don't use the TFC name/logo as branding. |
| Client visuals | CraftEngine + BetterModel + BetterHud, installed and configured **automatically by our plugin** (`dependency-bootstrap` skill) |
| Blocks | Unlimited server-side blocks (CraftEngine). Full-cube terrain uses `auto_state` pools + repurposed vanilla states that never occur in a TFC world. Small shapes reuse matching vanilla hitboxes (e.g. button for small ore pieces). Rare decorative blocks use `entity_renderer`. Ore grades keep distinct textures. |
| Threading | Main/region thread does as little as possible: lazy calendar-based evaluation instead of ticking, snapshot → async compute → budgeted region-thread commit, fully async worldgen, async packet rewriting (`paper-folia-threading` skill) |
| Inventory tabs | Clicking outside the inventory window (fake button drawn by the resource pack) opens our TFC GUI |
| Grass/foliage colours | Custom biomes via datapack + async biome-id rewrite in chunk packets for seasons |
| HUD | BetterHud |
| Particles | Only for explicit actions. Never ambient/passive. |

Provisional (not yet decided, ask before cementing): the final product name (should be distinct from "TerraFirmaCraft" for trademark reasons), the Java root package, and the content namespace. Until decided, use namespace `tfp` and package `dev.tfp`.

## Planned module layout

```
api/                     public API + events for addons
core/                    pure Java: calendar, climate, food, heat, forging, recipes, registries (NO Bukkit/NMS/plugin imports)
worldgen/                pure Java port of TFC world generation (noise, regions, layers, rivers, chunk data)
paper/                   plugin bootstrap, listeners, scheduler abstraction, storage, packet layer, commands, GUIs
nms/                     isolated version-specific code (only when Paper API can't do it)
integration/craftengine  adapter: blocks, items, behaviours, pack content
integration/bettermodel  adapter: entity models, animations
integration/betterhud    adapter: HUD layouts, placeholders
content/                 generated + copied data and assets (TFC JSON, textures, models, bbmodels, plugin YAMLs)
tools/datagen            CLI that converts a TFC checkout into content/
docs/porting/ledger.md   feature status + divergences from TFC
```

## Skills (load before working in that area)

| Skill | Use it for |
|---|---|
| `tfc-feature-porting` | Any TFC mechanic → plugin port. The master workflow. |
| `tfc-asset-migration` | Moving TFC textures/models/blockstates/bbmodels/GUI/lang into CraftEngine/BetterModel/BetterHud content |
| `craftengine` | Blocks, items, furniture, appearances, hitboxes, behaviours, resource pack, CraftEngine Java API |
| `bettermodel` | Entity models, animations, carrier mobs, BetterModel API |
| `betterhud` | HUD elements, placeholders, BetterHud API |
| `dependency-bootstrap` | How our plugin auto-installs and auto-configures the three client plugins |
| `paper-folia-threading` | Schedulers, region ownership, async design, packet rewriting |
| `plugin-testing` | Unit, integration, concurrency and performance tests |

## Working rules
- Reference TFC by path + pinned commit (see `docs/porting/ledger.md`). Don't vendor the whole TFC tree.
- Every divergence from TFC behaviour goes into the ledger's Divergences table.
- `core/` and `worldgen/` must stay platform-free and thread-safe (immutable data, no Bukkit).
- Feature code talks to CraftEngine/BetterModel/BetterHud only through our `integration/*` adapters.
