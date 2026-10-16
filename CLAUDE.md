# TerraFirmaPlugin

A from-scratch reimplementation of the **TerraFirmaCraft** mod (TFC, MC 1.21.1 NeoForge) as a **Paper + Folia** plugin for **vanilla clients**. All visuals come from **CraftEngine** (blocks, items, furniture, GUI textures, the resource pack), **BetterModel** (entity models) and **BetterHud** (HUD), which our plugin installs and configures automatically. License: **EUPL-1.2**, the same as TFC.

**Status: planning phase.** There is no production code yet. The repo holds the design documents and the skills future work relies on.

## Documents

| File | What it is | Read it when |
|---|---|---|
| `PRD.md` | Product requirements: vision, users, scope (feature modules with P0/P1/P2), non-functional requirements, milestones, risks | Deciding *what* to build or prioritizing |
| `ARCHITECTURE.md` | System design: runtime topology, modules, threading model, data/persistence, worldgen, client-visual pipeline, packet layer, lifecycle, testing, open spikes | Before writing or reviewing any code. Keep it updated when the design changes. |
| `docs/porting/ledger.md` | Per-feature port status + every **divergence** from TFC | Starting or finishing a feature; whenever behaviour differs from TFC |
| `IMAGES.md` | Queue of 2D images we need generated (textures, GUI, HUD, icons) | You need art that TFC doesn't have. See the `image-requests` skill. |
| `AGENTS.md` | Instructions for Codex/ChatGPT, which generates the images listed in `IMAGES.md` | Not for Claude; don't follow it |
| `NOTICE`, `LICENSE` | Attribution and EUPL-1.2 | Copying any TFC/third-party file |

## Locked decisions (summary; details in `ARCHITECTURE.md`)
- Paper + Folia only. MC 1.21.x (1.21.4+, tested 1.21.11) and 26.x (26.2/26.3). **Java 25** everywhere.
- Lazy, calendar-based simulation instead of ticking. Snapshot → async compute → budgeted region-thread commit. Async worldgen and packet work.
- CraftEngine blocks: unlimited server-side states. Terrain uses `auto_state` pools + repurposed vanilla states that never occur in a TFC world. Small shapes reuse matching vanilla hitboxes (e.g. button for small ore pieces). Rare blocks use `entity_renderer`. Ore grades keep distinct textures.
- Inventory "tabs" open by clicking outside the inventory window, where the pack draws a button. Seasonal colours via datapack biomes + async chunk biome remap. HUD via BetterHud. Particles only on explicit actions.
- Provisional names: namespace `tfp`, package `dev.tfp`. The final product name must differ from "TerraFirmaCraft".

## Skills: which to load when
Skills live in `.claude/skills/`. Load the matching skill **before** acting; more than one often applies.

| Situation | Skill(s) |
|---|---|
| Porting/implementing any TFC mechanic, or "how does TFC do X" with intent to build it | `tfc-feature-porting` (the master workflow) |
| Moving TFC textures/models/blockstates/lang/sounds/bbmodels, extending `tools/datagen` | `tfc-asset-migration` |
| Anything CraftEngine: start with the hub, then the specific sub-skill | `craftengine` (hub) |
| ↳ block YAML, properties, built-in block behaviours | `craftengine-blocks` |
| ↳ appearances, `block_state_mappings`, auto_state pools, repurposed vanilla states, resource pack build/hosting/merging | `craftengine-visuals-pack` |
| ↳ items, tools, armor, recipes, loot, events/functions/conditions | `craftengine-items` |
| ↳ entity_renderer, furniture, images/fonts, textured GUIs | `craftengine-gui-furniture` |
| ↳ Java API, custom behaviours, block entities, storage, worldgen placement, startup timing | `craftengine-java-api` |
| ↳ templates, config_factory, YAML extensions, generating CE config at scale | `craftengine-config-generation` |
| Entities, animals, models, animations | `bettermodel` |
| HUD elements, placeholders, hiding vanilla bars | `betterhud` |
| Plugin startup, load order, installing content into the three plugins, their config edits, `/tfp reload` | `dependency-bootstrap` |
| Scheduling, async, Folia, packets, persistence, performance | `paper-folia-threading` |
| Tests, CI, benchmarks, profiling, "is this feature done?" | `plugin-testing` |
| Needing a 2D image (texture, GUI, HUD art, icon) that doesn't exist | `image-requests` |

## Working rules
- **Decide async, apply sync. This is the default for all gameplay code.** Events and callbacks only snapshot and enqueue. An async runner decides whether and what must change (pure code). A sync runner on the owning region re-validates and writes under a tick budget. Example: placing a connectable block (support beam, wattle, connected grass) stores a provisional state; the async runner computes the connections for the block and its neighbours; only real changes are applied by the sync runner. Details: `paper-folia-threading` → "decide async, apply sync".
- Reference TFC by path + the pinned commit in the ledger. Clone TFC outside the repo; never vendor the whole tree.
- `core/` and `worldgen/` stay platform-free and thread-safe. Feature code reaches CraftEngine/BetterModel/BetterHud only through `integration/*` adapters.
- Never edit generated content by hand. Fix `tools/datagen` or add an override.
- Every behaviour that differs from TFC gets a row in the ledger's Divergences table.
- Ported files carry the EUPL + "Ported from TerraFirmaCraft ... @ commit" header.
- Don't generate images yourself. Request them in `IMAGES.md` (`image-requests` skill).
- When a design decision changes, update `ARCHITECTURE.md` (and `PRD.md` if scope changes) in the same change.
