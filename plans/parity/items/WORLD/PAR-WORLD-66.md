---
id: PAR-WORLD-66
title: "Surface decoration in generation: wire the forest floor, island guano, tide pools, sea stacks and bamboo into TfcTerrain, the worldgen.decoration itest"
area: WORLD
status: done
wave: 52
deps: [PAR-WORLD-21, PAR-WORLD-65, PAR-DECO-23]
size: M
aliases: ["PAR-WORLD-21 follow-up: the chunk fill lives in paper/worldgen/TfcTerrain.java, outside PAR-WORLD-21's files_owned"]
tfc_refs:
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/land_plants.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/surface_decoration/tidal_flats.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/large_features"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/SurfaceDecoration*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/SurfaceDecoration*"
  - "itest/src/main/java/dev/tfp/itest/cases/WorldgenDecoration*.java"
ui: true
in_game_steps:
  - "tp Bot into a temperate forest chunk generated after this item; time set noon; screenshot the forest floor (sticks, pinecones, humus, bones, rotten flesh, salt licks)"
  - "tp Bot to a tidal flats or rocky shore position from the itest case; screenshot a tide pool and a sea stack patch"
  - "tp Bot to a plateau or mountain chunk with bamboo from the itest case; screenshot bamboo and golden bamboo"
plan:
  - "Rules from PAR-WORLD-21/65 in core/worldgen/decoration: ForestFloor.forestPlants (inside #tfc:feature/land_plants at index 15 of its 128 entries: run it in that slot of the plant step), guanoIslands (large features of guano_island), atollGuano (ocean_atolls, deep_ocean_atolls, after the atoll corals), and the tide pool, sea stack and bamboo rules of PAR-WORLD-65 in their biome tag order. The glacial oceanic spring already runs through VolcanicFeatures (PAR-WORLD-21)."
  - "Per NP-WORLD-01 decide the features of this chunk and its 8 neighbours, write only this chunk's blocks, as the shore groundcover does; feed GroundView.forestDensity and topBlockStone (forest-bounded patches place nothing where the forest is unknown). Covers as the CE groundcover blocks (tfp:groundcover/<type>, placeholder fallback); golden bamboo through PAR-DECO-23's block."
  - "Done 2026-11-25: paper worldgen/SurfaceDecorationStep decides the 3x3 origin chunks, each over a fresh DecorationLevel on the terrain with the trees (PlantGround now a DecorationLevel.Terrain), for the biome at the origin centre (core decoration/DecorationBiomes: 99 land biomes, guano island, atolls, tidal flats): bamboo in the large features order (rare_bamboo, bamboo, rare_bamboo_golden, bamboo_golden) after the forest; island and atoll guano, sea stacks and the forest floor before the surface plants; tide pools after the shore groundcover. TfcTerrain.writeSurface writes only this chunk's blocks where they fit (covers on free positions, ground blocks on ground, never over trees), CE groundcover/cobble/hardened/golden bamboo with vanilla fallbacks. SurfaceDecorationStepTest 4; itest worldgen.decoration written (WorldgenDecorationCase, not run here). PAR-DECO-23 is still open: golden bamboo is written as vanilla bamboo until its block exists (ledger). Ledger: feature row + 3 divergences."
  - "Pending in-game check (container cannot run client): the screenshots of in_game_steps into docs/verification/PAR-WORLD-66/, and the itest run of worldgen.decoration."
tests:
  - "paper: SurfaceDecoration wiring test (only free positions of this chunk written, the tag slot order)"
  - "itest: worldgen.decoration case counts forest-floor groundcover, tide pools and bamboo per biome sample within TFC's ranges"
done_criteria:
  - "The forest floor, island guano, tide pools, sea stacks and bamboo generate in their biomes"
  - "Screenshots of the forest floor, a tide pool and bamboo pass review"
---

# PAR-WORLD-66: Surface decoration in generation

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
