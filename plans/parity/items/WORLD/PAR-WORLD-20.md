---
id: PAR-WORLD-20
title: "Underground decoration and erosion: cave spikes and columns, calcite, cave vegetation, hanging roots, underground lakes, the erosion pass"
area: WORLD
status: open
wave: 48
deps: [PAR-WORLD-06, PAR-WORLD-12, PAR-WORLD-14, PAR-WORLD-15, PAR-PHYS-03, PAR-DECO-06, PAR-DECO-08, PAR-CLIM-04]
size: M
aliases: ["parity audit 2026-11-06: TFC placed features with no rule and no open-gap entry"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/ErosionFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/cave"
  - "src/main/java/net/dries007/tfc/world/feature/TFCFeatures.java"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/underground_decoration.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/underground_lakes.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/erosion.json"
  - "src/main/resources/data/tfc/worldgen/placed_feature/underground_flood_fill_lake.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/hanging_roots_patch.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/cave_vegetation.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/cave/**"
  - "core/src/test/java/dev/tfp/core/worldgen/cave/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/cave/**"
  - "core/src/main/java/dev/tfp/core/worldgen/erosion/**"
  - "core/src/test/java/dev/tfp/core/worldgen/erosion/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/erosion/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/CaveDecoration*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/CaveDecoration*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcChunkGenerator.java"
ui: true
in_game_steps:
  - "tfp debug worldgen find cave_spike (or tp Bot to a cave from the census of PAR-WORLD-19); time set noon; screenshot a cave room with spikes, a column and mossy cobble from cave_vegetation"
  - "tp Bot to an underground flood-fill lake found by the itest case; screenshot at GUI scale 2"
  - "tp Bot to a sloped dirt or gravel cliff in a fresh chunk; screenshot that no generated block falls on chunk load (erosion stabilised it)"
plan:
  - "Current (audit 2026-11-06): the biome tags `in_biome/underground_decoration` (cave_column, cave_spike, large_cave_spike, calcite, mega_calcite, icicle, springs, underground loose rocks), `in_biome/underground_lakes` (underground_flood_fill_lake) and `in_biome/erosion` (tfc:erosion) are placed in every TFC biome, but no Java rule runs cave_column, cave_spike, large_cave_spike, calcite, mega_calcite, cave_vegetation, hanging_roots or underground_flood_fill_lake, and none of them is in an open-gaps list, so no item's done criterion would catch them. Springs and underground loose rocks stay with PAR-WORLD-14 and PAR-WORLD-09."
  - "Port in core/worldgen/cave (platform-free, thread-safe, golden against an /opt/tfc oracle with TFC's order of random draws): CaveSpikesFeature, LargeCaveSpikesFeature, CaveColumnFeature (raw rock spikes of the local rock, written as the CE rock spike states of PAR-DECO-06), ThinSpikeFeature for calcite and mega_calcite (the CE calcite block of PAR-DECO-08; icicle keeps PAR-CLIM-04's block), CaveVegetationFeature (raw to mossy cobble and cobble replacement per rock, hanging plants through PAR-WORLD-12's plant blocks), the hanging_roots vegetation patch (vanilla hanging roots + TFC rooted dirt), and the underground flood-fill lake through PAR-WORLD-14's flood-fill code."
  - "Port ErosionFeature in core/worldgen/erosion: the top-down column pass that removes or stabilises landslide-prone blocks (LandslideRecipe through PAR-PHYS-03's rules) above fragile cells and hardens raw rock under them, so freshly generated terrain never collapses on first update; run it in the feature step order of the biome feature list (erosion before surface decoration)."
  - "Wire the new rules into TfcChunkGenerator's feature step in TFC's biome step order (underground_structures, then underground_decoration; lakes before them), decide async per chunk and write through the existing chunk writer; no ticking, no extra chunk loads beyond the TFC feature radius (NP-WORLD-01 chunk-boundary rule)."
  - "Add the eight placed-feature ids to a new `core/src/test/resources/dev/tfp/core/worldgen/cave/open_gaps.txt` first, and remove each id when its rule lands, so the list is empty at done."
tests:
  - "core: CaveFeatureGoldenTest per feature (block lists for 64 seeded origins vs the TFC oracle), open_gaps.txt empty"
  - "core: ErosionGoldenTest (column before/after vs TFC for landslide-prone stacks over air, water and cave cells)"
  - "core: property test that erosion leaves no landslide-prone block above a fragile cell inside the chunk"
  - "itest: worldgen.cave case: pregenerate 16 chunks with caves, count spikes, columns and calcite per chunk within TFC's range, and assert no falling-block entity spawns on first load"
done_criteria:
  - "Every id of underground_decoration (except the springs and loose rocks owned elsewhere), underground_lakes and erosion has a golden-tested rule; the cave open_gaps list is empty"
  - "In-game screenshots show spikes, columns, calcite and cave vegetation in caves and an underground lake; generated slopes do not collapse on load"
  - "Divergences (block substitutes, carrier hitboxes of spikes) recorded in docs/porting/ledger.md"
---

# PAR-WORLD-20: Underground decoration and erosion: cave spikes and columns, calcite, cave vegetation, hanging roots, underground lakes, the erosion pass

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
