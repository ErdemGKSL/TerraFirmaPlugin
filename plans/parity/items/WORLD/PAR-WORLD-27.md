---
id: PAR-WORLD-27
title: "Wire VolcanicFeatures into the Paper terrain: rivulets, crater lakes, tuya and stratovolcano fissures, pumice and obsidian patches, springs; spring water of crater lakes"
area: WORLD
status: open
wave: 26
deps: [PAR-WORLD-14]
size: M
aliases: ["PAR-WORLD-14 follow-up"]
tfc_refs:
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/underground_decoration.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/stratovolcanoes.json"
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/VolcanicFeatureBlocks*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/VolcanicFeature*"
  - "paper/src/main/java/dev/tfp/paper/worldwater/hot/SpringWater.java"
  - "paper/src/test/java/dev/tfp/paper/worldwater/hot/SpringWater*"
ui: true
in_game_steps:
  - "Fresh world, seed 42 (docs/worldgen/volcanic-features.md): tp Bot to the Crater Lake stratovolcano near 0 -1664 and to the cinder cone at -32 1369; screenshot at GUI scale 2: crater lakes, magma rivulets down the slope, pumice and obsidian shard groundcover"
  - "tfp debug water in a crater spring water lake: spring"
plan:
  - "PAR-WORLD-14 ported every volcanic feature as pure core rules (`core/worldgen/volcano/VolcanicFeatures`: `place(chunkX, chunkZ, view, out)` gives the chunk's own blocks of its 3 x 3 neighbourhood). `TfcTerrain.decorate` still runs only `Volcanoes.place` (calderas, volcano fissures, hot springs). Replace that call with `VolcanicFeatures.place` (same chunk-boundary rule, NP-WORLD-01), keeping its position after the trees and before the corals and plants, and the underground springs in their tag order."
  - "Write the new `VolcanicKind`s: water and salt water sources, hardened rock (raw rock until PAR-WORLD-15), sulfur ore (CE ore palette when present, else raw rock), pumice and obsidian shard groundcover (CE groundcover ids), magma per rock (PAR-WORLD-15). The view must supply `oceanFloor` and the ground over the decorated terrain; `packedIce`/`blueIce` stay false until the glacier builders exist."
  - "Extend `SpringWater` so the stratovolcano crater spring water lakes and the `*_volcano_spring_water_fissure` springs count as spring water (ledger divergence row on spring water names this)."
tests:
  - "paper: VolcanicFeatureWiringTest (every kind maps to a placeholder or CE id, blocks stay in the chunk, trees kept)"
  - "paper: SpringWater test for a crater lake pool"
done_criteria:
  - "Crater lakes, rivulets and patches appear at the showcase positions (screenshot)"
---

# PAR-WORLD-27: Wire VolcanicFeatures into the Paper terrain

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
