---
id: PAR-FARM-22
title: "Creeping and floating plant spread, rising rocks under grass, plant regrowth verification"
area: FARM
status: done
wave: 34
deps: [PAR-FARM-11, PAR-WORLD-46]
size: M
aliases: ["found while doing PAR-FARM-11: only the short and tall grass spread rule is done"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/regrowth/**"
  - "core/src/test/java/dev/tfp/core/regrowth/**"
  - "paper/src/main/java/dev/tfp/paper/regrowth/**"
  - "paper/src/test/java/dev/tfp/paper/regrowth/**"
  - "docs/verification/PAR-FARM-22/**"
ui: true
in_game_steps:
  - "tfp time add days 10 in a meadow of generated plants; screenshot more grass"
plan:
  - "Add the creeping plants (direction draw, updateStateFromSides), the floating water plants (spreadSelf(1, 2, 1), no fluid 5 below, not on river water) and PlantRegrowth.placeRisingRock to PlantSweepRules and PlantSweeper."
  - "In game, once PAR-WORLD-46 puts the CE plants in generation: harvested grass regrows (PAR-FARM-11 done criterion), and the sweep stamps and spreads are visible."
  - "Done: core regrowth/{CreepingRules,FloatingRules,CreepKind,FloatKind} and PlantKinds.creeping/floating; paper PlantSweeper sweeps creeping and floating plants beside the grasses (snapshot, CPU decision, region re-validation and placement). placeRisingRock was already ported and wired by PAR-GATHER-14 (grass/RisingRocks, outside files_owned). Tests: CreepingFloatingRulesTest (6), PlantRegrowthTest golden extended, RegrowthMockBukkitTest (2 new). Pending in-game check (container cannot run client). Ledger row and 2 divergences."
tests:
  - "core: golden for the new rules"
done_criteria:
  - "Grass spreads over days; creeping and floating plants spread; screenshot"
---

# PAR-FARM-22: Creeping and floating plant spread, rising rocks, plant regrowth verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
