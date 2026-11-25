---
id: PAR-FARM-18
title: "Worldgen placement of stationary berry bushes"
area: FARM
status: done
wave: 31
deps: [PAR-FARM-14, PAR-WORLD-13]
size: S
aliases: ["split from PAR-FARM-14"]
tfc_refs:
  - "src/generated/resources/data/tfc/worldgen/placed_feature/plant"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/berry/**"
  - "paper/src/test/java/dev/tfp/paper/berry/**"
  - "core/src/main/java/dev/tfp/core/worldgen/plant/**"
  - "core/src/test/java/dev/tfp/core/worldgen/plant/**"
  - "core/src/test/resources/dev/tfp/core/flora/open_gaps.txt"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantBlocks.java"
ui: false
in_game_steps: []
plan:
  - "Place tfp:berry_bush/<species> (lifecycle from the month, stage 0) through the worldgen feature runner where TFC's stationary bush placed features put them (find them in the PAR-WORLD-13 gap ids), using CustomBlocks."
  - "Done: core worldgen/plant Berries (bush ids to tfp:berry_bush/<species>, survival, SpreadingBushFeature), PlantData reads tfp:spreading_bush, bush states and minecraft:water matching, PlantState kinds, PlantBlocks.ceRef/placeholder for the bushes; open_gaps.txt shrunk to the fruit trees and banana; BerriesTest (6). Files owned widened to core worldgen/plant and PlantBlocks. Pending in-game check (container cannot run client)"
tests:
  - "paper: a generated chunk holds bushes in the biomes TFC lists"
done_criteria:
  - "Bushes generate in the TFC biomes"
---

# PAR-FARM-18


Follow-up of PAR-FARM-14. Follow `plans/parity/PROTOCOL.md`.
