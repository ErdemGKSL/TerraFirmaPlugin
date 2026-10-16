---
name: tfc-feature-porting
description: End-to-end workflow for porting a TerraFirmaCraft (TFC) mod feature, mechanic or system into this Paper/Folia plugin — locating it in the TFC source, splitting it into platform-free core logic, data, Paper glue and client visuals, converting ticking code to calendar-based lazy evaluation, recording divergences, and wiring tests. Use this whenever the task mentions implementing, porting, re-creating or "adding" any TFC mechanic (food decay, heat, knapping, anvil/forging, crops, collapse, climate, calendar, animals, barrels, querns, metallurgy, worldgen pieces, etc.), whenever someone asks "how does TFC do X" with the intent of building it here, or when a new gameplay system is added to this repository even if TFC is not named.
---

# Porting a TFC feature

This repository rebuilds the TerraFirmaCraft mod (NeoForge, MC 1.21.1) as a **Paper + Folia** plugin for MC 1.21.x and 26.x. Visuals come from CraftEngine (blocks/items/furniture/GUI textures/resource pack), BetterModel (entity models) and BetterHud (HUD). The license is EUPL-1.2, the same as TFC, so TFC code, data and assets may be reused **with attribution** (see "Attribution" below).

A port is not a transliteration. NeoForge gives TFC things a plugin does not have (new block classes, block entities, capabilities, client renderers, mixins, custom packets). The job is to keep TFC's *rules and numbers* exactly, while re-homing every mechanism onto what Paper + our three client-side plugins actually provide, under our threading rules.

## Before you start

1. Read `CLAUDE.md` at the repo root for the locked-in project decisions (platform, license, module layout, threading principles).
2. Have the TFC source available. Clone it read-only outside the repo (e.g. into the scratchpad), pinned to the commit recorded in `docs/porting/ledger.md` (initially `c3b4d9536b82e5a9746dd793c586720a74108057`). Never vendor the whole TFC tree into this repo — only the files you port.
3. Find where the feature lives using `references/tfc-source-map.md`. It maps each gameplay area to TFC packages, data folders and asset folders.

## The workflow

### 1. Inventory the feature
Read the TFC code paths end to end and write a short inventory (in your scratch notes or the PR description) of every piece, sorted into these buckets:

| Bucket | What goes here | Where it lands in our repo |
|---|---|---|
| **Rules** | Pure math/state machines: decay formulas, heat transfer, forging rules, climate curves, alloy ranges, growth stages | `core/` (no Bukkit/Paper imports) |
| **Data** | JSON definitions (`data/tfc/tfc/**`, `data/tfc/recipe/**`, tags), constants tables | `content/data/` via the datagen tool |
| **World glue** | Block/entity/player interaction, events, persistence, scheduling | `paper/` |
| **Visuals** | Block/item models, textures, GUI screens, HUD, entity models, particles, sounds | CraftEngine / BetterModel / BetterHud configs (see `tfc-asset-migration`) |
| **Client/packet** | Anything TFC does in `client/` or via packets/mixins | Packet rewriting, HUD placeholders, or a documented divergence |
| **Config** | `TFCConfig` server/common options | Our YAML config, same keys where sensible |

The inventory is what keeps a port from silently dropping behaviour. If something in TFC has no bucket, it becomes a divergence (step 7).

### 2. Map NeoForge concepts to ours
Use `references/concept-mapping.md`. The short version:

- `Block` + `BlockState` properties → a **CraftEngine block** (real server block, client appearance chosen per state) + our behaviour class registered through the CraftEngine API.
- `BlockEntity` → our **per-chunk block data store** (compact, serialized off-thread) + CraftEngine `entity_renderer` for anything that must be *seen* (items on an anvil, ingot piles).
- Item `DataComponent`/capability (heat, food, forging, fluid, size) → **PersistentDataContainer** on the ItemStack with a versioned codec; only timestamps and base values are stored, derived values are computed on read.
- Recipes → our recipe registry loaded from the original TFC recipe JSON (same schema, including `tfc:and`, `tfc:not_rotten`, output modifiers).
- `Screen`/`Container` → inventory GUI with CraftEngine font-image background.
- Entities → a vanilla carrier mob (pathfinding/physics) + BetterModel model + our AI via Paper Mob Goals.
- Client HUD overlays → BetterHud elements fed by our placeholders.
- Mixins → Paper events, packet rewriting, or (last resort) NMS in an isolated module.

### 3. Re-design for our threading model
TFC runs on one server thread and ticks block entities. We do not. Apply `paper-folia-threading` and specifically:

- **Prefer lazy, calendar-based evaluation over ticking.** TFC already does this for food (`FoodComponent.creationDate` + decay modifier) and heat (`HeatComponent` stores `lastTemperature` + `lastTick` and extrapolates). Copy that pattern for everything that changes over time: crops, saplings, barrels, drying, charcoal pits, composters, nest boxes, animal aging. Store "when it started / last known value", compute the current value whenever someone looks.
- Anything that must actively scan the world (snow accumulation, crop random checks, collapse propagation) runs on a **snapshot off-thread** and commits block changes back on the owning region thread under a per-tick time budget.
- No passive/ambient custom particles. Particles only as feedback for an explicit action (pouring metal, hammering, a fire being lit).

Write down which parts run where before writing code.

### 4. Port the data
Do not hand-copy JSON. Extend the datagen tool (`tools/datagen`) so it reads the TFC generated tree (`src/generated/resources/data/tfc/**`) and emits our `content/data/**`. Keep TFC's schemas unchanged wherever possible. That way TFC addon datapacks and future TFC updates can be re-imported by re-running the tool. `references/data-formats.md` has the main schemas with real examples and counts.

### 5. Port the rules into `core/`
- Keep TFC's constants, formulas and enum orders identical, and keep the method names recognisable, so the next person can diff our code against TFC's.
- `core/` must not import `org.bukkit`, `io.papermc`, `net.minecraft` or any of the three client plugins. It is pure Java 21. That makes it unit-testable and safe to call from any thread.
- Make values immutable (records, `List.copyOf`) so they can be shared across region threads without locks.
- Put the attribution header on every ported file (see below).

### 6. Glue it into the world (`paper/`)
- Register block/item behaviours with CraftEngine through `integration/craftengine` (see the `craftengine` skill). Do not call CraftEngine directly from feature code; go through our adapter interface so features stay testable.
- Persist state in the per-chunk store or the item PDC, never in static maps keyed by `Location`.
- Interactions: Paper events on the region thread. Do only cheap validation there, and push heavy work to the async pool.

### 7. Record divergences
When a plugin can't reproduce TFC exactly (a custom fluid in the world, inventory tabs, sky rendering, client-side tint), do not silently approximate. Add a row to the **Divergences** table in `docs/porting/ledger.md`: what TFC does, what we do, why, and whether it could be revisited. Locked decisions already made:
- Player inventory tabs → clicking outside the inventory window (the "valhallatrinkets" trick, with a resource-pack button drawn there) opens our TFC GUI.
- Seasonal grass/foliage colours → custom biomes shipped as a datapack + async rewriting of biome ids in chunk packets.
- HUD → BetterHud.
- Particles → action-driven only.

### 8. Test
Follow `plugin-testing`. At minimum: unit tests for every ported rule in `core/` (port TFC's own tests from `src/test/java/net/dries007/tfc/test/**` when they exist, e.g. `ForgeRuleTest`, `KnappingPatternTest`, `HeatingItemTest`), plus one golden-value test that pins a number from TFC (e.g. "raw beef bought at day 0 is rotten at calendar tick N").

### 9. Update the ledger
Mark the feature's status in `docs/porting/ledger.md` (`not started` / `rules` / `playable` / `visual-complete`), list the TFC files it covers, and link the tests. If player-facing text changed, update lang keys; TFC ships `tr_tr.json` and other locales you can reuse.

## Attribution

Every source file that is ported or substantially derived from TFC starts with:

```java
/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/<path>.java @ <short-commit>
 */
```

Assets copied from TFC keep their relative path in `content/` and are covered by `NOTICE`. Assets with a different upstream license (some sounds are CC-BY-4.0 or CC0, `FastNoiseLite` is MIT) must be listed explicitly in `NOTICE`. Don't use the TerraFirmaCraft name or logo as our product branding.

## Anti-patterns to catch in review
- Ticking every block entity or item every tick. Use calendar deltas.
- Bukkit calls from an async thread, or touching a block outside the current region on Folia.
- Feature code importing CraftEngine/BetterModel/BetterHud classes directly instead of our adapters.
- "Close enough" numbers. If a value differs from TFC, it must be a recorded divergence.
- Hand-written copies of TFC JSON that the datagen tool should produce.
