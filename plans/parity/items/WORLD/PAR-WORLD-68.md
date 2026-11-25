---
id: PAR-WORLD-68
title: "Volcano chunk generation failure: regression test over the real terrain fill around volcanoes and vents"
area: WORLD
status: done
wave: 53
deps: [PAR-WORLD-60]
size: S
aliases: ["owner report: a headless bot cannot join the TFC test world (chunk generation fails in VolcanoBlocks.rockBlockId)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/TFCFeatures.java"
files_owned:
  - "paper/src/test/java/dev/tfp/paper/worldgen/VolcanoChunkFillTest.java"
  - "paper/build.gradle.kts"
ui: false
plan:
  - "Reported cause: `VolcanoBlocks.rockBlockId` throwing for volcanic blocks with rock -1. At HEAD (c45571f2 plus the rebase) that is the PAR-WORLD-60 bug and its fix is in place: `rockBlockId(kind, rock)` reads the rock name only for hardened rock and magma with a rock in range; `VolcanicFeatureBlocks.rockId/oreId` read it only for rock kinds and `TfcTerrain.volcanicData` returns before them for the kinds with a placeholder."
  - "Reproduction attempt: the real `TfcTerrain.fill` (seed -5793902121713522665, palette resolved through a fake `BlockVisuals`, a chunk-data fake of height -64..320 that throws out of bounds) over every chunk of a 80 x 80 chunk square around the origin that holds volcanic blocks (found with the terrain's own `VolcanicFeatures.place` over the real `Ground` view): 346 chunks with air, lava, water, hardened rock, gravel, magma, gem ore, pumice and obsidian shards, in two CraftEngine states (knows everything; knows no magma and groundcover blocks). No exception, so the failure does not reproduce at HEAD; a server built before PAR-WORLD-60 (c45571f2 contains it) is the likely source of the report."
  - "Done: `VolcanoChunkFillTest` (two cases) is the regression test; paper's test classpath now carries `content/generated` so the terrain tests that need the generated rock settings run instead of aborting."
tests:
  - "paper: VolcanoChunkFillTest generates every chunk with volcanic blocks of a seed square in two CraftEngine states without an exception"
done_criteria:
  - "No exception filling 346 volcano and vent chunks of the seed"
---

# PAR-WORLD-68: Volcano chunk generation failure

Follow-up of PAR-WORLD-60. Follow `plans/parity/PROTOCOL.md`.
