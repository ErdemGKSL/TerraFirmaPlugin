---
id: PAR-FLUID-04
title: "Hot spring healing and bubble columns"
area: FLUID
status: done
wave: 10
deps: [PAR-FLUID-03]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBubbleColumnBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldwater/hot/**"
  - "paper/src/test/java/dev/tfp/paper/worldwater/hot/**"
ui: false
in_game_steps: []
plan:
  - "Players standing in hot spring water heal hotWaterHealAmount periodically (entity scheduler, throttled); bubble columns give air when configured."
  - "In-game check deferred to PAR-FLUID-11 (PROTOCOL §6.5): this worker session had no subagent tool to run the headless server and client, so the hot spring screenshot and its steps moved there. Spring water is told apart by the pool's shape (NP-FLUID-01: vanilla water); players only (mobs neither healed nor drowned in drag columns); TFC config values fixed at their defaults until config keys exist (ledger)."
  - "Done: paper worldwater/hot/{HotSpringRules (TFC entityInside rolls 1 in 10 per tick per block, heal 0.08, spring pool shape, active spring biomes, vanilla air loss with respiration and drowning), SpringWater (provided service: is a block spring water, spring blocks in a box), HotSpringTracker (every 10 ticks on each player's thread: heal; with bubbleColumnProvidesAir off, air loss in drag-down columns), HotSpringModule (feature module hotspring, cancels vanilla air gain in drag-down columns)}. Tests: paper HotSpringRulesTest 7, HotSpringTrackerTest 6. Ledger: feature row + 6 divergences. Follow-up PAR-FLUID-11 (in-game check); PAR-FLUID-10 can read SpringWater."
tests:
  - "paper: heal tick"
done_criteria:
  - "Health rises in a hot spring (MockBukkit HotSpringTrackerTest); deferred check tracked in PAR-FLUID-11"
---

# PAR-FLUID-04: Hot spring healing and bubble columns

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
