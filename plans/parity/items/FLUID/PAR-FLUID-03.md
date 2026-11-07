---
id: PAR-FLUID-03
title: "Salt water, spring water and river water in the world"
area: FLUID
status: done
wave: 9
deps: [PAR-FLUID-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/RiverWaterBlock.java"
  - "src/main/java/net/dries007/tfc/common/fluids/RiverWaterFluid.java"
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
  - "src/main/java/net/dries007/tfc/mixin/WaterMixin.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldwater/**"
  - "core/src/test/java/dev/tfp/core/worldwater/**"
  - "core/src/test/resources/dev/tfp/core/worldwater/**"
  - "paper/src/main/java/dev/tfp/paper/worldwater/**"
  - "paper/src/test/java/dev/tfp/paper/worldwater/**"
ui: false
in_game_steps: []
plan:
  - "Impossible on vanilla clients: custom world fluids. Closest: vanilla water everywhere; salt-ness decided by biome (ocean/salt marsh biome tags) for drinking/filling; river current applied server-side as entity velocity from RiverWaterRules flow (throttled, players and items in river biomes); spring water = vanilla water in hot spring features with heal rule (PAR-FLUID-04)."
  - "Record the final divergence rows."
  - "In-game check deferred to PAR-FLUID-06 (PROTOCOL §6.5): this worker session had no subagent tool to run the headless server and client, so the boat/item drift screenshots and their steps moved there. Players and player-driven boats are not pushed (client-simulated motion; ledger), so game modes, flying and vehicles ridden by players are unaffected by design; spring pools are not told apart yet (PAR-FLUID-04 owns spring water); open-water drinking still uses its ocean-biome rule until PAR-FLUID-07 switches it to WorldWaterService."
  - "Done: core worldwater/{WaterKind, WorldWaterRules (spring > salt > river > fresh, TFC fillColumn river rule, vanilla-biome fallback), RiverCurrent (vanilla fluid push with TFC river flows), ChunkWater, WaterTerrain (seeded region generator + surface heights as the chunk generator)}; paper worldwater/{WorldWaterModule (feature module worldwater, `/tfp debug water <name>`), WorldWaterService (provided service, CPU-pool chunk cache, salt classification by biome), RiverCurrentTracker (player-thread scan every 20 ticks, per-entity push each tick on items, undriven boats, mobs)}. Tests: core WorldWaterRulesTest 4, RiverCurrentTest 5, ChunkWaterTest 2 (RiverWaterRulesTest already in core/fluid); paper WorldWaterServiceMockBukkitTest 4, RiverCurrentTrackerTest 5. Ledger: feature row + 5 divergences. Follow-ups PAR-FLUID-06 (in-game check), PAR-FLUID-07 (drinking)."
tests:
  - "core: RiverWaterRulesTest exists"
  - "paper: salt classification by biome"
done_criteria:
  - "Deferred check tracked in PAR-FLUID-06; ledger rows final"
---

# PAR-FLUID-03: Salt water, spring water and river water in the world

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
