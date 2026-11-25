---
id: PAR-WORLD-56
title: "Per-world TFC settings in the remaining climate readers (fog, crops, farmland, wild crops, rising rocks, regrowth, water terrain, calendar sample)"
area: WORLD
status: done
wave: 30
deps: [PAR-WORLD-51]
size: S
aliases: ["PAR-WORLD-51 follow-up: climate readers outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings/Settings.java"
  - "src/main/java/net/dries007/tfc/util/climate/OverworldClimate.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/climate/fog/FogForecast.java"
  - "paper/src/main/java/dev/tfp/paper/crop/CropSettle.java"
  - "paper/src/main/java/dev/tfp/paper/crop/wild/WildWorld.java"
  - "paper/src/main/java/dev/tfp/paper/farm/FarmlandReadout.java"
  - "paper/src/main/java/dev/tfp/paper/grass/RisingRocks.java"
  - "paper/src/main/java/dev/tfp/paper/regrowth/PlantSweeper.java"
  - "paper/src/main/java/dev/tfp/paper/worldwater/WorldWaterService.java"
  - "itest/src/main/java/dev/tfp/itest/cases/PrecipSampleCase.java"
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
  - "paper/src/main/java/dev/tfp/paper/windmill/WindmillWind.java"
ui: false
in_game_steps: []
plan:
  - "PAR-WORLD-51 added `paper/worldgen/WorldSettingsLookup` (`climate(World)`, `climate(long seed)`, `waterTerrain(World)`) and made `ChunkClimateSource.climateAt(seed, x, z)` use the world's region settings. These readers outside its files_owned still build `OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE)` or `new WaterTerrain(seed)`: FogForecast, CropSettle, WildWorld, FarmlandReadout, RisingRocks (2), PlantSweeper, WorldWaterService, itest PrecipSampleCase, and TfpPlugin.java:~996 (the calendar sample; a one-line hub edit in the PAR-INFRA-01 wiring, or move it into a module)."
  - "Replace each with `WorldSettingsLookup.climate(...)` / `WorldSettingsLookup.waterTerrain(world)`."
  - "Done: FogForecast, CropSettle, WildWorld, FarmlandReadout, RisingRocks (2), PlantSweeper, WindmillWind (found after the item was written, added to files_owned), TfpPlugin calendar sample and itest PrecipSampleCase use WorldSettingsLookup.climate; WorldWaterService uses WorldSettingsLookup.waterTerrain. Test: WorldSettingsLookupCallSitesTest (source scan of paper and itest main sources for DEFAULT_TEMPERATURE_SCALE and new WaterTerrain(); WorldSettingsLookupTest already covers a 5000 temperature_scale world)."
tests:
  - "paper: extend WorldSettingsLookupTest or a reader's own test with a 5000 temperature_scale world"
done_criteria:
  - "No production use of OverworldClimate.DEFAULT_TEMPERATURE_SCALE or `new WaterTerrain(seed)` outside tests and the defaults; tests green; ledger row of PAR-WORLD-22 updated"
---

# PAR-WORLD-56: Per-world TFC settings in the remaining climate readers

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
