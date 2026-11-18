# Region and river maps: our port vs TerraFirmaCraft

PAR-WORLD-01. Each PNG here is a side-by-side image: **left TFC, right our port**, 8 px white gap.

## How they were made

- **Window and maps.** The window of TFC's `RegionGeneratorTests.testRegionGenerator`: centre grid (0, 75), radius 200,
  so 400 x 400 grid points (1 px = 128 blocks, about 51 km a side). The maps use TFC's own `taskColor` colour schemes
  and `Artist.Colors` gradients (ported verbatim in
  `core/src/test/java/dev/tfp/core/worldgen/region/RegionMapRenderColours.java`). Each map is drawn from the finished
  region (after every task), not from the in-between task states TFC's drawing test hooks with `visualizeRegion`.
- **River map.** TFC's `drawWithRivers`: river edge midpoint fractals (intersect distance 0.1 grid) over the rivers and
  lakes map, 4 samples per grid point, centre (0, 75), radius 50 (100 x 100 grid points, 400 x 400 px).
- **Seeds and settings.** 42, -7 and 20260917 with the overworld preset (continentalness 0.5, scales 20 000, constants 0);
  20260917 with finite continents on, as in `RegionGeneratorGoldenTest`.
- **TFC side.** A scratch oracle outside the repo compiled TFC's `world/region`, `world/river`, `world/noise`,
  `world/layer/framework` (+ `UniformLayer`, `ZoomLayer`, `SmoothLayer`, `TFCLayers`) unchanged at c3b4d9536 against
  Paper 1.21.11's Mojang-mapped `Mth` and `XoroshiroRandomSource`, with verbatim extracts of `Helpers` and
  `BiomeNoise` (the hotspot, plate and continent cell noises). As for the region golden oracle, the karst surface rock
  task and the chunk data generator were left out (they need the datapack rock settings), and `ChunkPos.asLong` was
  inlined (no game bootstrap). The same colour class, with only its imports changed, rendered the TFC images.

## Result: pixel diff 0

All 48 images (16 per seed: 15 region maps and the river map) are identical pixel for pixel:

| map (`MapKind`) | 42 | -7 | 20260917 |
|---|---|---|---|
| add_islands (land / ocean depth) | 0 | 0 | 0 |
| annotate_distance_to_cell_edge | 0 | 0 | 0 |
| annotate_boundary_types | 0 | 0 | 0 |
| annotate_hot_spot_age | 0 | 0 | 0 |
| annotate_distance_to_ocean_and_deep_ocean | 0 | 0 | 0 |
| annotate_base_land_height | 0 | 0 | 0 |
| annotate_distance_to_west_coast | 0 | 0 | 0 |
| add_mountains | 0 | 0 | 0 |
| annotate_biome_altitude | 0 | 0 | 0 |
| temperature | 0 | 0 | 0 |
| rainfall_after_rivers | 0 | 0 | 0 |
| rainfall_variance | 0 | 0 | 0 |
| choose_rocks | 0 | 0 | 0 |
| add_rivers_and_lakes | 0 | 0 | 0 |
| choose_biomes | 0 | 0 | 0 |
| rivers (river map) | 0 | 0 | 0 |

The tests pin the hashes: `RegionMapRenderTest` (one hash per seed over the 15 region maps) and `RiverMapRenderTest`
(one per seed for the river map). Running them writes every map to `core/build/worldgen-images/<seed>/`.

Committed side-by-sides (a selection, to keep the repo small): seed 42 `add_mountains`, `choose_biomes`,
`add_rivers_and_lakes`, `choose_rocks`, `rivers`; seeds -7 and 20260917 `choose_biomes`, `rivers`.

## Not compared

- TFC's `KOPPEN` / `KOPPEN_AFTER_RIVERS` maps need `KoppenClimateClassification`, which is not ported (drawing only).
- `ANNOTATE_KARST_SURFACE` and the karst, glacial, tectonic and height biome colour schemes: the karst surface flag stays
  false in our port (ledger divergence, datapack rock settings), so a TFC world with the real rock settings can show
  karst biome variants these maps do not have. The oracle leaves the task out the same way.
- `KAOLINITE_CAN_SPAWN`: a spawn helper map, not region data.

With diff 0, the M2-GEN-03 (region map) and M2-GEN-04 (river map) visual checks are closed.
