---
id: PAR-WORLD-05
title: "Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders"
area: WORLD
status: done
wave: 18
deps: [PAR-WORLD-04, PAR-CLIM-04]
size: M
aliases: ["M2-GEN-08 (remaining)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/surface/builder"
  - "src/main/java/net/dries007/tfc/world/feature/IceAndSnowFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/surface/**"
  - "core/src/test/java/dev/tfp/core/worldgen/surface/**"
ui: false
in_game_steps: []
plan:
  - "Port the remaining builders (biome table currently covers 46 biomes with the normal builder fallback) and the ice and snow feature; sea ice at generation with icebergs via core/weather/SeaIce."
  - "Golden column checks against the TFC oracle as for SurfaceGoldenTest."
  - "TFC's IceAndSnowFeature is an empty TODO at c3b4d9536 (place returns true): nothing to port. Sea ice and icebergs come from ShoreAndOceanSurfaceBuilder's frozen ocean extension, ported in ShoreAndOceanSurface (warmest month = SeaIce.warmestMonth as a column input)."
  - "The stratovolcano, atoll, tuff ring and tuya wrappers are not ported (their centered feature heights are not either); their seed draws are kept so the later builders' seeds are TFC's (ledger Divergences)."
  - "Edited the existing SurfaceGoldenTest and SurfacePropertyTest (own glob): the pass now uses TFC's getHeight (top block, water below sea level), so the pass reaches top - 20 and 44 of the 300 legacy rows changed; rows regenerated with the new oracle (TFC's code on Minecraft 1.21.11, whose float clampedMap the old stubs lacked)."
  - "Follow-up: PAR-WORLD-43 wires BiomeSurfaces, the full column inputs, the chunk random, the water level and the SurfaceBlock set into TfcTerrain (not in this item's files)."
  - "Done: core BiomeSurfaces (all 125 biomes, TFC registry seed order, cinder cone wrapper), SurfaceBlock, ShoreAndOceanSurface (frozen ocean, sea ice, icebergs via NormalNoise, lava-flow shore, ocean ridge), IceSheetSurface (+ ice sheet shield volcano), ShieldVolcanoSurface (lava flows), BadlandsSurface, LandSurfaces; heights fixed to TFC's getHeight. Tests: BiomeSurfacesGoldenTest (1000 columns bit-exact vs TFC, every biome; table flags), SurfaceGoldenTest rows regenerated (300), SurfacePropertyTest updated; ledger rows updated."
tests:
  - "core: SurfaceGoldenTest extended to every builder"
done_criteria:
  - "Every TFC biome uses its own builder; golden green"
---

# PAR-WORLD-05: Ice sheets, sea ice and icebergs at generation; lava flows; remaining biome surface builders

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
