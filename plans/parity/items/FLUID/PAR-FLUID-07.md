---
id: PAR-FLUID-07
title: "Open-water drinking reads WorldWaterService: salt water in every salty TFC biome, river water fresh"
area: FLUID
status: open
wave: 10
deps: [PAR-FLUID-03]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java"
  - "src/main/java/net/dries007/tfc/network/PlayerDrinkPacket.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/player/WaterDrink*"
  - "paper/src/test/java/dev/tfp/paper/player/WaterDrink*"
ui: false
in_game_steps: []
plan:
  - "Follow-up of PAR-FLUID-03: `WaterDrinkListener.fluidOf` still decides salt by the vanilla ocean placeholder biome, so TFC's shores, tidal flats, salt marshes and other `salty()` land biomes drink as fresh water. Use `WorldWaterService.kindOf(block).containerFluid()` (from `ModuleServices`, falling back to the current rule when the worldwater module is absent); drop the shore divergence row of PAR-SURV-04 once it holds."
tests:
  - "paper: WaterDrinkListener salt in a salty TFC land biome via a WorldWaterService with a seeded terrain"
done_criteria:
  - "Drinking in a salty TFC land biome drinks salt water; ledger rows updated"
---

# PAR-FLUID-07: Open-water drinking reads WorldWaterService: salt water in every salty TFC biome, river water fresh

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
