---
id: PAR-WORLD-41
title: "Terrain writes the TFC soil blocks: soil variant per surface rule and the RockPalette surface ids in TfcTerrain"
area: WORLD
status: open
wave: 27
deps: [PAR-WORLD-04]
size: M
aliases: ["PAR-WORLD-04 follow-up: the soil block set and RockPalette.surfaceIds/surfaceIndex exist, but TfcTerrain (not owned by PAR-WORLD-04) still writes vanilla grass/dirt/coarse dirt/mud/gravel"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/surface/SoilSurfaceState.java"
  - "src/main/java/net/dries007/tfc/world/surface/SurfaceStates.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/surface/**"
  - "core/src/test/java/dev/tfp/core/worldgen/surface/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcTerrain*"
ui: true
in_game_steps:
  - "Fresh world generated after this item; tp Bot to a plains biome, then to a dry (aridisol) and a wet (fluvisol/podzol) region"
  - "tfp debug block on grass, dirt, coarse dirt, mud and gravel: tfp:grass/<soil>, tfp:dirt/<soil>, tfp:coarse_dirt/<soil>, tfp:mud/<soil>, tfp:rock/gravel/<rock>; screenshot at GUI scale 2 (TFC grass top tinted by the biome colour)"
  - "tfp debug worldgen 5x5 chunk scan: no minecraft:grass_block/dirt/coarse_dirt/mud/gravel"
plan:
  - "Current: PAR-WORLD-04 generates every soil block (tfp:grass|clay_grass|coarse_dirt|mud|rooted_dirt/<soil>, peat, kaolin) and adds RockPalette.SOILS, surfaceId, surfaceIds(rawRockIds) and surfaceIndex(kind, soil, rock, rocks). core/worldgen/surface/SurfaceRules maps every soil variant to its SurfaceKind and drops the variant (ledger divergence 'TFC's soil and surface blocks are per soil variant'), and TfcTerrain.surfaceBlocks() writes vanilla Material blocks."
  - "Core: carry TFC's soil variant (SoilBlockType.Variant order = RockPalette.SOILS) through SoilSurfaceState/SurfaceStates (soil(type, variant), transitioningSoil, the aridisol/fluvisol/... choices by rainfall) next to the kind, keeping TFC's order of noise reads; the rock of gravel is the column's surface rock. Golden tests against TFC rows."
  - "TfcTerrain: append RockPalette.surfaceIds(ROCK_IDS) to the palette ids (one resolve, the refuse rule covers them), and write palette.block(base + RockPalette.surfaceIndex(kind, soil, rock, rocks)) for grass/dirt/coarse dirt/mud/gravel; keep vanilla for sand/sandstone (PAR-WORLD-23), snow and packed ice. Update depositMaterials and TreeView ground classes to the CE blocks."
  - "Ledger: drop or narrow the per-soil-variant divergence row once written."
tests:
  - "core: soil variant per rule vs TFC golden rows"
  - "paper: TfcTerrain writes the palette surface block for every soil kind (fake palette)"
done_criteria:
  - "A fresh world shows TFC grass/dirt/gravel textures (screenshot) with no vanilla placeholder for those kinds in a 5x5 chunk scan (`tfp debug worldgen`)"
---

# PAR-WORLD-41: Terrain writes the TFC soil blocks: soil variant per surface rule and the RockPalette surface ids in TfcTerrain

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
