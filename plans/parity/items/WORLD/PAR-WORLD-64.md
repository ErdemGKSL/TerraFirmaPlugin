---
id: PAR-WORLD-64
title: "Cave decoration in generation: wire the PAR-WORLD-20 cave rules and erosion into TfcTerrain, the underground flood-fill lake, the worldgen.cave itest"
area: WORLD
status: done
wave: 49
deps: [PAR-WORLD-20, PAR-DECO-06, PAR-WORLD-15]
size: M
aliases: ["PAR-WORLD-20 follow-up: the chunk fill lives in paper/worldgen/TfcTerrain.java and the flood fill in core/worldgen/volcano/Caldera.java, outside PAR-WORLD-20's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/FloodFillLakeFeature.java"
  - "src/main/resources/data/tfc/worldgen/placed_feature/underground_flood_fill_lake.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/flood_fill_lake.json"
  - "src/main/java/net/dries007/tfc/world/feature/ErosionFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/cave"
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/cave/**"
  - "core/src/test/java/dev/tfp/core/worldgen/cave/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/cave/**"
  - "core/src/main/java/dev/tfp/core/worldgen/volcano/Caldera.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/CaveDecoration*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/CaveDecoration*"
  - "itest/src/main/java/dev/tfp/itest/cases/WorldgenCave*.java"
ui: true
in_game_steps:
  - "tfp debug worldgen find cave_spike (or tp Bot to a cave from the census of PAR-WORLD-19); time set noon; screenshot a cave room with spikes, a column and mossy cobble from cave_vegetation"
  - "tp Bot to an underground flood-fill lake found by the itest case; screenshot at GUI scale 2"
  - "tp Bot to a sloped dirt or gravel cliff in a fresh chunk; screenshot that no generated block falls on chunk load (erosion stabilised it)"
plan:
  - "PAR-WORLD-20 ported the rules in core (golden against a scratch oracle of TFC's bodies on Minecraft 1.21.11's RandomSource): core/worldgen/cave CaveDecoration (cave_column, cave_spike, large_cave_spike, calcite, mega_calcite, icicle in the tag's order over a CaveLevel), CaveVegetation (the large feature of ten biomes), core/worldgen/erosion Erosion (the column pass over an Erosion.Column). TFC's thin spikes never place a block (see the golden test), so calcite and icicles do not generate; tfc:hanging_roots_patch is placed by no TFC biome. cave/open_gaps.txt still lists tfc:underground_flood_fill_lake."
  - "Underground flood-fill lake: expose Caldera.place with Config.WATER (replace_fluids empty, overfill) through a public entry and run underground_flood_fill_lake (rarity 3, in_square, uniform y -56..63) from core/worldgen/cave over the cave level; remove it from open_gaps.txt (empty at done)."
  - "Wiring in TfcTerrain.fill/decorate in TFC's step order: erosion first in its biome step (in_biome/erosion), the underground lakes (lakes step), cave_vegetation with the large features, then CaveDecoration in underground_decoration after the vein indicators and before the cave loose rocks. Per NP-WORLD-01 run the features of this chunk and its 8 neighbours over a CaveLevel that reads the neighbours' blocks from the Ground view (carves, terrain, rock) and writes only this chunk's blocks; erosion runs on this chunk's columns only (TFC does the same)."
  - "Blocks: rock spikes as tfp:rock/spike/<rock>[part, waterlogged] (PAR-DECO-06 has no lava or salt water fluid: lava-filled spikes are written dry, salt water as waterlogged; ledger divergence), hardened rock, tfp:rock/mossy_cobble and cobble, the CE guano groundcover, vanilla cobweb and hanging roots; the cave spring is a water source (no scheduled fluid tick: the first fluid update flows it). Map our blocks to CaveCell and Erosion.Column (FallRules.canFallThrough, the landslide soils of PAR-PHYS-03, the aquifer substance of the column)."
  - "Done 2026-11-25: core cave/UndergroundLakes (rarity 3, in_square, uniform -56..63, through the new public volcano/Caldera.waterLake) with UndergroundLakesGoldenTest (5 oracle hashes), CaveVegetation.placesIn (the 99 biomes that list it), cave/open_gaps.txt empty. paper worldgen/CaveDecorationStep decides the 3x3 origin chunks, each over a fresh CaveLevel on the noise-stage terrain (Ground carves, rock, surface), keeps only this chunk's blocks; TfcTerrain writes them by stage (lake at the start of decorate, spikes and columns before the cave loose rocks, cave vegetation after the forest) where the real block still is the cell the decision saw, and runs erosion on its own columns after the fill (TfcTerrain.erode). CE spike/hardened/mossy cobble/guano with vanilla fallbacks. CaveDecorationStepTest 5; itest worldgen.cave case written (WorldgenCaveCase, not run here). Ledger: feature row + 4 divergences (decision view, spike fluids, spike carrier hitbox, spring tick and erosion inputs)."
  - "Pending in-game check (container cannot run client): the screenshots of in_game_steps into docs/verification/PAR-WORLD-64/, and the itest run of worldgen.cave."
tests:
  - "core: open_gaps.txt empty; a golden for the underground flood-fill lake from the oracle"
  - "paper: CaveDecoration adapter test (CaveCell mapping both ways, only this chunk written)"
  - "itest: worldgen.cave case: pregenerate 16 chunks with caves, count spikes, columns and mossy cobble per chunk within TFC's range, and assert no falling-block entity spawns on first load"
done_criteria:
  - "Every cave id of PAR-WORLD-20 runs in generation; cave/open_gaps.txt is empty"
  - "In-game screenshots show spikes, columns and cave vegetation in caves and an underground lake; generated slopes do not collapse on load"
  - "Divergences (spike fluids, carrier hitboxes of spikes) recorded in docs/porting/ledger.md"
---

# PAR-WORLD-64: Cave decoration in generation

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
