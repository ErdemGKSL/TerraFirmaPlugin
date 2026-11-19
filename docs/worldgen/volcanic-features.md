# Volcanic features: showcase seeds

PAR-WORLD-14. Where to look at each volcanic feature family of `core/worldgen/volcano/VolcanicFeatures` (springs,
rivulets, flood fill crater lakes, random fissure selectors, pumice and obsidian shard patches of the cinder cones, tuyas,
stratovolcanoes and shield volcanoes, the ocean ridge rivulets). The rules are pure core code; wiring them into the Paper
terrain (`TfcTerrain.decorate`) and the CE blocks they need is PAR-WORLD-27 / PAR-WORLD-15, so until then these positions
show the biomes and cones only.

## How the positions were found

A scratch JUnit search (not kept) built `SurfaceHeights` with the overworld region settings for each seed (as
`TerrainDensityGoldenTest.heights` does) and walked square rings around the origin every 64 blocks (up to 24 000 blocks
out). For each family it kept the first hit:

- cinder cone, tuya: a column whose biome lists the family and whose cell center (`CinderCones.center`, `Tuyas.center`)
  has such a biome too (the `tfc:volcano` / `tfc:tuya` center placement holds there): the coordinates are the center;
- stratovolcano per variant: a column of a stratovolcano biome with easing above 0.85 (`Stratovolcanoes.easing`), named by
  `variantAt` (`batholith` has no crater lake or fissures);
- shield volcano, ocean ridge: the first column of such a biome.

## Positions (block x z, biome)

| family | seed 42 | seed -7 | seed 20260917 |
|---|---|---|---|
| cinder cone center (rivulets, caldera, fissure, patches) | -32 1369 canyons | -1396 -104 canyons | 563 -825 canyons |
| tuya center (rivulets, caldera, fissure, patches) | 2387 -5809 tuyas | 3699 -6054 tuyas | 6061 -7209 tuyas |
| stratovolcano Fuji | -3008 704 volcanic_oceanic_mountains | 1280 -4416 volcanic_oceanic_mountains | -4928 5632 volcanic_oceanic_mountains |
| stratovolcano Tahoma | -9984 2176 volcanic_oceanic_mountains | 4736 7360 volcanic_mountains | -5056 3968 volcanic_oceanic_mountains |
| stratovolcano Crater Lake (Wizard Island, sulfur rivulets) | 0 -1664 volcanic_oceanic_mountains | 5184 5504 volcanic_mountains | -8192 3200 volcanic_oceanic_mountains |
| stratovolcano Kelimutu (three lakes) | -1984 1216 volcanic_oceanic_mountains | -768 -4288 volcanic_oceanic_mountains | -7872 -6784 volcanic_oceanic_mountains |
| stratovolcano batholith | 6144 6848 volcanic_oceanic_mountains | 3456 -4416 volcanic_oceanic_mountains | -7616 128 oceanic_volcanic_arc |
| shield volcano patches | 3648 -704 active_shield_volcano | -1984 -1152 active_shield_volcano | 0 0 dormant_shield_volcano |
| ocean ridge rivulets | -2304 -2304 | 640 704 | 3200 -3264 |

The underground springs (water, lava, surface water) run in every biome; glacial and ice sheet springs need TFC's
packed and blue ice, which the terrain does not place yet (`VolcanoView.packedIce`/`blueIce` are false).
