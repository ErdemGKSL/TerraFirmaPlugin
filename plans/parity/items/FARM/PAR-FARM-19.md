---
id: PAR-FARM-19
title: "Worldgen placement of spreading bushes and cranberries"
area: FARM
status: open
wave: 32
deps: [PAR-FARM-15, PAR-WORLD-13]
size: S
aliases: ["split from PAR-FARM-15"]
tfc_refs:
  - "src/generated/resources/data/tfc/worldgen/placed_feature/plant"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/berry/**"
  - "paper/src/test/java/dev/tfp/paper/berry/**"
ui: false
in_game_steps: []
plan:
  - "Place tfp:spreading_bush/<species> and tfp:spreading_bush/cranberry through the worldgen feature runner where TFC's placed features put them, using CustomBlocks."
tests:
  - "paper: a generated chunk holds the bushes in the biomes TFC lists"
done_criteria:
  - "Spreading bushes and cranberries generate in the TFC biomes"
---

# PAR-FARM-19: Worldgen placement of spreading bushes and cranberries

Follow-up of PAR-FARM-15. Follow `plans/parity/PROTOCOL.md`.
