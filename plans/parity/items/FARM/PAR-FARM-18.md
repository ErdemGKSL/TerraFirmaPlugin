---
id: PAR-FARM-18
title: "Worldgen placement of stationary berry bushes"
area: FARM
status: open
wave: 31
deps: [PAR-FARM-14, PAR-WORLD-13]
size: S
aliases: ["split from PAR-FARM-14"]
tfc_refs:
  - "src/generated/resources/data/tfc/worldgen/placed_feature/plant"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/berry/**"
  - "paper/src/test/java/dev/tfp/paper/berry/**"
ui: false
in_game_steps: []
plan:
  - "Place tfp:berry_bush/<species> (lifecycle from the month, stage 0) through the worldgen feature runner where TFC's stationary bush placed features put them (find them in the PAR-WORLD-13 gap ids), using CustomBlocks."
tests:
  - "paper: a generated chunk holds bushes in the biomes TFC lists"
done_criteria:
  - "Bushes generate in the TFC biomes"
---

# PAR-FARM-18

> Claimed: farm-worker-b, 2026-11-25: Worldgen placement of stationary berry bushes

Follow-up of PAR-FARM-14. Follow `plans/parity/PROTOCOL.md`.
