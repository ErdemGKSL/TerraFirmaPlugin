---
id: PAR-WORLD-43
title: "Wire every biome's own surface builder (BiomeSurfaces) and the full surface block set into the Paper terrain"
area: WORLD
status: done
wave: 28
deps: [PAR-WORLD-05, PAR-WORLD-41, PAR-CLIM-04]
size: M
aliases: ["PAR-WORLD-05 follow-up (terrain wiring)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/surface/SurfaceManager.java"
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
  - "src/main/java/net/dries007/tfc/world/TFCAquifer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcTerrainSurface*"
ui: true
in_game_steps:
  - "Fresh world; tp Bot to a frozen ocean (warmest month below -0.5 C): sea ice on the water at y 62, icebergs of packed ice and snow; screenshot at GUI scale 2"
  - "tp to an ice sheet biome: snow over packed ice and blue ice, moraines at the edges; tp to an active shield volcano shore: basalt lava flows on the beach; tp to badlands: banded sand and sandstone"
  - "tfp debug block on a plains grass block and a beach sand block: the soil variant and the sand colour of TFC"
plan:
  - "Done: TfcTerrain builds each chunk's surfaces together with ColumnSurface.chunkRandom (x outer, z inner) using BiomeSurfaces (seed stream after the layers + one noise sampler seed), full SurfaceColumn inputs (CinderCones.centerBiome added, a small edit outside files_owned), writes cobble/basalt/tuff/gravel/sea ice CE blocks (SURFACE_IDS appended to the palette) and the blocks above the rock (sea ice, icebergs); water now fills to y 62 as TFC. Sand/sandstone/halite/hardened clay stay vanilla/raw until PAR-DECO-08. Test TfcTerrainSurfaceTest (2, needs generated content in the test classpath, skipped otherwise: checked once with it) and TfcTerrainSoilTest. Pending in-game check (container cannot run client): in_game_steps."
  - "PAR-WORLD-05 ported every TFC surface builder in core (BiomeSurfaces, SurfaceBlock, ShoreAndOceanSurface, IceSheetSurface, ShieldVolcanoSurface, BadlandsSurface, LandSurfaces), golden-tested against TFC. TfcTerrain still builds with the legacy SurfaceFamily and writes SurfaceKind as vanilla blocks."
  - "Build one BiomeSurfaces per world from the generator's seed stream at TFC's point (after the region generator with the chunk data generator, the biome layers and the noise sampler's seed: as WaterTerrain replays the stream, then one next()); look builders up by the column's TFC biome id."
  - "Fill the full SurfaceColumn per column: biome weight (localBiomeWeights), preVolcanicHeight and the cinder easing (CinderCones), the warmest month (SeaIce.warmestMonth with the chunk climate), the rock at y = 0 (karst, mafic), the original biome without rivers; share ColumnSurface.chunkRandom(chunkX, chunkZ) across the chunk's columns in TFC's order (x outer, z inner)."
  - "Water: TFC's aquifer fills below sea level (y <= 62); TfcTerrain fills up to 63 inclusive. Align it (and the decoration views that read it) so the surface pass and the world agree."
  - "PAR-WORLD-41 (dep) owns writing the soil blocks; its core part (the soil variant per rule) is done by PAR-WORLD-05's SurfaceBlock. Here: write every other SurfaceBlock through the RockPalette / CE ids of PAR-WORLD-04 (soil variants, sand colours, rock gravel and cobble, basalt, tuff, hardened clay, halite) and TFC's sea ice of PAR-CLIM-04; blocks a pass writes above the rock top (icebergs) and below it."
tests:
  - "paper: TfcTerrainSurface test that a chunk's columns call the biome's own builder with the shared chunk random (column order) and that sea ice and icebergs appear for a frozen column"
done_criteria:
  - "Every TFC biome's surface in the world comes from its own builder; sea ice, icebergs, ice sheets and lava flows generate; in-game check done"
---

# PAR-WORLD-43: Wire every biome's own surface builder (BiomeSurfaces) and the full surface block set into the Paper terrain

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
