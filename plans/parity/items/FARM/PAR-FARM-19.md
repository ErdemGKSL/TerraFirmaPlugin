---
id: PAR-FARM-19
title: "Worldgen placement of spreading bushes and cranberries"
area: FARM
status: done
wave: 32
deps: [PAR-FARM-15, PAR-WORLD-13]
size: S
aliases: ["split from PAR-FARM-15"]
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
  - "Place tfp:spreading_bush/<species> and tfp:spreading_bush/cranberry through the worldgen feature runner where TFC's placed features put them, using CustomBlocks."
  - "Done: with PAR-FARM-18 (same commit): spreading bushes with canes and the cranberry bush (block_with_fluid with the water predicate) are placed by the feature runner; BerriesTest covers column stages, canes and determinism. Pending in-game check (container cannot run client)"
tests:
  - "paper: a generated chunk holds the bushes in the biomes TFC lists"
done_criteria:
  - "Spreading bushes and cranberries generate in the TFC biomes"
---

# PAR-FARM-19


Follow-up of PAR-FARM-15. Follow `plans/parity/PROTOCOL.md`.
