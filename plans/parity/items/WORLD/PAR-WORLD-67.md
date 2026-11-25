---
id: PAR-WORLD-67
title: "Surface flood-fill lakes (tfc:flood_fill_lake): the rule, its golden and the generation wiring"
area: WORLD
status: open
wave: 53
deps: [PAR-WORLD-64]
size: M
aliases: ["PAR-WORLD-65 follow-up: the placed-feature coverage guard found tfc:flood_fill_lake with no rule and no open-gaps line"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/FloodFillLakeFeature.java"
  - "src/main/resources/data/tfc/worldgen/placed_feature/flood_fill_lake.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/flood_fill_lake.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/surface_flood_fill_lakes.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/all_lakes.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/lake/**"
  - "core/src/test/java/dev/tfp/core/worldgen/lake/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/lake/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/decoration/coverage_gaps.txt"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/SurfaceLake*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/SurfaceLake*"
ui: true
in_game_steps:
  - "tp Bot to a flood-fill lake in a plains or plateau chunk generated after this item (tfp debug worldgen find, or the census); time set noon; screenshot the lake at GUI scale 2"
plan:
  - "TFC lists #tfc:feature/surface_flood_fill_lakes (tfc:flood_fill_lake: rarity_filter 5, in_square, WORLD_SURFACE_WG, tfc:climate min_temperature -17, min_groundwater 125) in in_biome/all_lakes and in the large_features of many land biomes; no rule places it (PlacedFeatureCoverageTest, coverage_gaps.txt)."
  - "Port it in core/worldgen/lake through the public flood-fill entry PAR-WORLD-64 adds to core/worldgen/volcano/Caldera (Config.WATER, TFC's draws in TFC's order), golden against a scratch oracle as the cave goldens of PAR-WORLD-20; remove tfc:flood_fill_lake from coverage_gaps.txt."
  - "Wire it in TfcTerrain in its large-feature slot of the biome lists (NP-WORLD-01: this chunk and its 8 neighbours, only this chunk's blocks written); fresh water, ice on top where TFC freezes it."
tests:
  - "core: golden of the surface lake from the oracle; coverage_gaps.txt empty"
  - "paper: SurfaceLake adapter test (only this chunk written)"
done_criteria:
  - "Surface flood-fill lakes generate in their biomes; coverage_gaps.txt is empty"
  - "Screenshot of a surface lake passes review"
---

# PAR-WORLD-67: Surface flood-fill lakes

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
