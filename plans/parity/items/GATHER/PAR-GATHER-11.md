---
id: PAR-GATHER-11
title: "Grass spread, grass death and rising loose rocks: the lazy sweep of TFC's grass random tick"
area: GATHER
status: open
wave: 19
deps: [PAR-GATHER-05, PAR-INFRA-11]
size: M
aliases: ["found while doing PAR-GATHER-05: the pure rules (GrassRules) exist, the world sweep does not"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/IGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/grass/**"
  - "paper/src/test/java/dev/tfp/paper/grass/**"
ui: false
in_game_steps: []
plan:
  - "Grass blocks are written by worldgen without placement events, so they cannot be indexed one by one in the catch-up service (thousands per chunk). Design a sampled sweep: on chunk load and the visible-area sweep, draw the Poisson number of random ticks the column set would have had (randomTickSpeed/4096 per block and tick) and apply GrassRules.canBeGrass (dies to dirt/clay) and the 4 spread attempts (GrassRules.spreadOffset, light >= 9, dirt/clay target, canPropagate), decided async from a snapshot, applied under the region budget."
  - "Rising rock: GrassRules.risingRockGates / risingRockChunk after a surviving grass tick: place or add to a loose rock on the grass (needs the loose rock CE blocks from PAR-GATHER-01's follow-up) when hemispheric season is spring, average temperature at most 8 C, no player within 20 blocks, chance grassSpawningRocksChance (0.001)."
  - "Connected grass sides (north/east/south/west of the CE grass) are PAR-WORLD-04's divergence and not part of this item."
tests:
  - "paper: sweep decision on a snapshot (dies under a full block, spreads onto dirt in light, not onto water)"
done_criteria:
  - "Grass under a placed block turns to dirt after a catch-up; grass spreads onto neighbouring dirt in daylight"
---

# PAR-GATHER-11: Grass spread, grass death and rising loose rocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
