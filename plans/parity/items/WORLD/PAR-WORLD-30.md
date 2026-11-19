---
id: PAR-WORLD-30
title: "Vein step wiring: biome vein passes, kaolin disc, projected, near-lava and rock-writing veins in TfcTerrain"
area: WORLD
status: open
wave: 22
deps: [PAR-WORLD-08, PAR-WORLD-04]
size: M
aliases: ["follow-up of PAR-WORLD-08"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/KaolinDiscVeinFeature.java"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins/montane.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins/collisional_mountains.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins/river.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins/kaolin.json"
  - "src/main/resources/data/tfc/worldgen/placed_feature/vein/kaolin_disc.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/VeinStep*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/VeinStep*"
  - "paper/src/test/java/dev/tfp/paper/worldgen/OreVeinWiringTest.java"
ui: false
in_game_steps: []
plan:
  - "PAR-WORLD-08 ported every vein type and the biome passes in core (`Veins`, `OreVeins(levelSeed, main, biomeVeins, rockIds)`, `OreVeins.WorldView`, `KaolinDisc`) and `OreVeinResources.loadAll/biomeVeins/kaolin` load them. `TfcTerrain` still builds `new OreVeins(seed, OreVeinResources.fromJar(...), ROCK_IDS)` and calls `ores.chunk(chunkX, chunkZ)`: only the main tag's types that need no world view and write only ores are placed."
  - "Give the vein step a `WorldView` (ocean-floor height of the noise stage, also 15 blocks outside the chunk for project_offset; lava at y = -55 within 4 blocks) and the chunk's biome vein tags (montane, collisional_mountains, river from the biome of each column; TFC runs a biome's tag per chunk feature origin); pass `OreVeinResources.loadAll` and `biomeVeins` instead of `fromJar`."
  - "Extend the terrain palette with the non-ore blocks the veins write (dike rocks, gravel, marble, coal, salts) so the rock-writing types are no longer held back; a pick that is a rock becomes the rock later veins see (OreVeins already does this)."
  - "Kaolin disc: only in `#in_biome/veins/kaolin` biomes and where the chunk climate has average temperature >= 18 and groundwater >= 300 (`KaolinDisc.MIN_TEMPERATURE/MIN_GROUNDWATER`), after the soil exists (needs PAR-WORLD-04's kaolin clay blocks)."
  - "Rerun PAR-WORLD-17's `ore.census` and PAR-REL-12's `ore.overlay.budget` (delegated server run) and record the numbers in docs/perf/ore-overlay-budget.md."
tests:
  - "paper: VeinStep wiring test: biome passes only in their biomes, projected types follow the height, near-lava types only near lava, kaolin only on soil and in its climate"
  - "itest: ore.census and ore.overlay.budget rerun"
done_criteria:
  - "Every vein type of TFC's tags generates in the terrain in tag order; census and budget numbers recorded"
---

# PAR-WORLD-30: Vein step wiring: biome vein passes, kaolin disc, projected, near-lava and rock-writing veins in TfcTerrain

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
