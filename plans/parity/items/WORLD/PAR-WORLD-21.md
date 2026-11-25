---
id: PAR-WORLD-21
title: "Untracked surface features: forest-floor patches, tide pools, guano islands, bamboo, sea stack patches and five volcanic ids"
area: WORLD
status: done
wave: 50
deps: [PAR-WORLD-09, PAR-WORLD-12, PAR-WORLD-13, PAR-WORLD-14, PAR-DECO-12]
size: M
aliases: ["parity audit 2026-11-06: TFC placed features with no rule and no open-gap entry"]
tfc_refs:
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/forest_plants.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/guano_islands.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/tide_pool_decorations.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/sea_stacks_patch.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/bamboo.json"
  - "src/main/resources/data/tfc/worldgen/placed_feature/crater_lake_rivulet.json"
  - "src/main/resources/data/tfc/worldgen/placed_feature/glacial_oceanic_spring.json"
  - "src/main/java/net/dries007/tfc/world/feature/TidePoolFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/SeaStacksFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/TFCBambooFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/decoration/**"
  - "core/src/test/java/dev/tfp/core/worldgen/decoration/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/decoration/**"
  - "core/src/main/java/dev/tfp/core/worldgen/volcano/**"
  - "core/src/test/java/dev/tfp/core/worldgen/volcano/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/volcano*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/SurfaceDecoration*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/SurfaceDecoration*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcChunkGenerator.java"
ui: false
in_game_steps:
  - "tp Bot into a temperate forest chunk generated after this item; time set noon; screenshot the forest floor (sticks, pinecones, humus, bones, rotten flesh, salt licks)"
  - "tp Bot to a tidal flats or rocky shore position from the itest case; screenshot a tide pool and a sea stack patch"
  - "tp Bot to a plateau or mountain chunk with bamboo from the itest case; screenshot bamboo and golden bamboo"
plan:
  - "Current (audit 2026-11-06): these TFC placed features are listed by biome tags but have no Java rule and are in no open-gaps list (flora, groundcover_deposit, volcano_features), so PAR-WORLD-09/13/14 can reach their done criteria without them: the `feature/forest_plants` patches (sticks_forest_patch, pinecone_patch, salt_lick_patch, humus_patch, rotten_flesh_patch, bone_patch), `feature/guano_islands` (extra_island_guano_patch) and atoll_guano, `feature/tide_pool_decorations` (tide_pool, big_tide_pool), sea_stacks_patch (tidal flats), the tfc:bamboo features (bamboo, rare_bamboo, bamboo_golden, rare_bamboo_golden), and the volcanic ids crater_lake_rivulet, crater_lake_sulfur, fuji_rivulet, tahoma_rivulet, glacial_oceanic_spring."
  - "First add every id to a new `core/src/test/resources/dev/tfp/core/worldgen/decoration/open_gaps.txt` (and the five volcanic ids to volcano_features_open_gaps.txt / volcano_features.tsv from the oracle), then port the rules with goldens and remove each id as it lands."
  - "Port in core/worldgen/decoration: the random_patch expansions over PAR-WORLD-09's groundcover blocks, TidePoolFeature (pool shape, water, the tide_pool_decorations creatures/plants), SeaStacksFeature patches, TFCBambooFeature (vanilla bamboo + golden bamboo of PAR-DECO-12, podzol base, probability), guano patches; in core/worldgen/volcano the five rivulet/spring ids with the existing rivulet and spring rules."
  - "Wire through TfcChunkGenerator's surface_decoration step in the biome's feature order; decide async, write through the chunk writer."
  - "Split 2026-11-25: TfcTerrain.java (the chunk fill) is outside files_owned, so the generation wiring, the itest and the in-game steps moved to PAR-WORLD-66; the tide pool, sea stack and bamboo rules and the coverage guard moved to PAR-WORLD-65. TfcChunkGenerator.java was not changed."
  - "Done 2026-11-25: core/worldgen/decoration/ForestFloor (sticks_forest, pinecone, salt_lick, humus, rotten_flesh and bone patches with their forest-bounded climates, extra_island_guano_patch with the intertidal band and stone top, atoll_guano at the chunk corner); core/worldgen/volcano: crater_lake_rivulet, crater_lake_sulfur, fuji_rivulet and tahoma_rivulet were already run by VolcanicFeatures (PAR-WORLD-27) under the configured rows, glacial_oceanic_spring added (count 200, uniform 80..92, its three biomes, before the stratovolcanoes). Tests: DecorationGoldenTest 4 (21 oracle hashes on MC 1.21.11's random), VolcanicPlacedIdsTest 3. decoration/open_gaps.txt holds the 7 ids of PAR-WORLD-65."
tests:
  - "core: DecorationGoldenTest per feature vs the TFC oracle; decoration/open_gaps.txt empty; VolcanoFeatureGoldenTest covers the five ids"
  - "core: test that every placed feature reachable from a TFC biome tag is either ruled or listed in one of the open-gaps files (a coverage guard so no id is untracked again)"
  - "itest: worldgen.decoration case counts forest-floor groundcover, tide pools and bamboo per biome sample within TFC's ranges"
done_criteria:
  - "The forest floor patches, island and atoll guano and the five volcanic ids have golden- or table-tested rules; the remaining ids (tide pools, sea stacks, bamboo) are listed in decoration/open_gaps.txt and moved with the coverage guard to PAR-WORLD-65"
  - "Deferred check tracked in PAR-WORLD-66"
---

# PAR-WORLD-21: Untracked surface features: forest-floor patches, tide pools, guano islands, bamboo, sea stack patches and five volcanic ids

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
