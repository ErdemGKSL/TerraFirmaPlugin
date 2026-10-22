---
id: PAR-FLUID-03
title: "Salt water, spring water and river water in the world"
area: FLUID
status: open
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
ui: true
in_game_steps:
  - "tp to a river (tfp debug chunk); drop an item into it; record 3 frames"
plan:
  - "Impossible on vanilla clients: custom world fluids. Closest: vanilla water everywhere; salt-ness decided by biome (ocean/salt marsh biome tags) for drinking/filling; river current applied server-side as entity velocity from RiverWaterRules flow (throttled, players and items in river biomes); spring water = vanilla water in hot spring features with heal rule (PAR-FLUID-04)."
  - "Record the final divergence rows."
tests:
  - "core: RiverWaterRulesTest exists"
  - "paper: salt classification by biome"
done_criteria:
  - "Boat in a river drifts downstream; ledger rows final"
---

# PAR-FLUID-03: Salt water, spring water and river water in the world

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
