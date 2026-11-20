---
id: PAR-GATHER-14
title: "Rising loose rocks on grass: the rock raised by TFC's grass random tick in spring frost"
area: GATHER
status: open
wave: 26
deps: [PAR-GATHER-11, PAR-GATHER-12, PAR-CLIM-27]
size: S
aliases: ["found while doing PAR-GATHER-11: GrassRules.risingRockGates and risingRockChunk exist; placing needs the loose rock CE blocks (PAR-GATHER-12), the rock type at the position and the hemispheric season and average temperature at the block"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/grass/RisingRock*.java"
  - "paper/src/test/java/dev/tfp/paper/grass/RisingRock*"
ui: false
in_game_steps: []
plan:
  - "After a surviving grass tick (GrassSweepRules), draw the rising rock: GrassRules.risingRockGates (chance 0.001, spring, average temperature at most 8 C, no player within 20 blocks), the slime-chunk gate, then place tfp:rock/loose/<rock at the position> in the air above the grass, or add one to a loose rock there (count < 3); decided from the snapshot, applied under the region budget by the GrassSweeper."
tests:
  - "paper: gates decide as TFC; a rock is placed on grass in spring frost, not near a player, not in summer"
done_criteria:
  - "Loose rocks rise on grass in spring frost away from players; tests green"
---

# PAR-GATHER-14: Rising loose rocks on grass

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
