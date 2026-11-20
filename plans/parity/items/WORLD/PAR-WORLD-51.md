---
id: PAR-WORLD-51
title: "Per-world TFC settings in every climate consumer (HUD, snow, precipitation, weather, seasonal remap, plant effects, chunk climate)"
area: WORLD
status: open
wave: 29
deps: [PAR-WORLD-22]
size: M
aliases: ["PAR-WORLD-22 follow-up: the climate readers outside the generator still use TFC's preset"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings/Settings.java"
  - "src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/banana/PaperBananaEffects.java"
  - "paper/src/main/java/dev/tfp/paper/berry/PaperBerryEffects.java"
  - "paper/src/main/java/dev/tfp/paper/berry/spread/PaperSpreadEffects.java"
  - "paper/src/main/java/dev/tfp/paper/calendar/TimeRateCommands.java"
  - "paper/src/main/java/dev/tfp/paper/climate/PlayerClimateService.java"
  - "paper/src/main/java/dev/tfp/paper/climate/SeasonalBiomeService.java"
  - "paper/src/main/java/dev/tfp/paper/climate/ChunkClimateSource.java"
  - "paper/src/main/java/dev/tfp/paper/fruittree/PaperFruitTreeEffects.java"
  - "paper/src/main/java/dev/tfp/paper/hud/HudHemisphere.java"
  - "paper/src/main/java/dev/tfp/paper/instrument/InstrumentReadout.java"
  - "paper/src/main/java/dev/tfp/paper/tabs/TabService.java"
  - "paper/src/main/java/dev/tfp/paper/weather/PrecipitationService.java"
  - "paper/src/main/java/dev/tfp/paper/weather/SnowService.java"
  - "paper/src/main/java/dev/tfp/paper/weather/cycle/ClimateWeatherCycle.java"
  - "paper/src/main/java/dev/tfp/paper/weather/cycle/LocalRain.java"
  - "core/src/main/java/dev/tfp/core/worldgen/chunk/ChunkDataGenerator.java"
  - "core/src/main/java/dev/tfp/core/worldwater/WaterTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/WorldSettingsLookup.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/WorldSettingsLookup*"
ui: false
in_game_steps: []
plan:
  - "PAR-WORLD-22 made TFC's Settings per world (worldgen-settings.yml, stored in <world>/tfp/world-settings.yml) and the generator (TfcTerrain: region map, chunk data, bedrock, grass density; spawn) uses them. Every other climate reader still builds `OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE)` or `ChunkDataGenerator.forSeed` / `WaterTerrain` over `RegionSettings.OVERWORLD`, so a world with a non-default temperature_scale, rainfall or continentalness shows the preset's climate in the HUD, snow, rain, seasonal colours and plant effects (files listed in files_owned)."
  - "Add `WorldSettingsLookup` (paper/worldgen): the settings of a World from its `TfcChunkGenerator.settings()` (TFC's preset for other generators); (PAR-WORLD-23) also build WaterTerrain's and forSeed's RegionGenerator with the rock settings so their karst biomes match the terrain; replace every DEFAULT_TEMPERATURE_SCALE / RegionSettings.OVERWORLD use with the world's values; give ChunkDataGenerator.forSeed and WaterTerrain a RegionSettings parameter (keep the old overloads for tests)."
tests:
  - "paper: a world whose generator has temperature_scale 5000 gives the HUD/snow climate with that scale (MockBukkit)"
done_criteria:
  - "No production use of OverworldClimate.DEFAULT_TEMPERATURE_SCALE or RegionSettings.OVERWORLD outside tests and the defaults; tests green; ledger divergence of PAR-WORLD-22 for the climate readers closed"
---

# PAR-WORLD-51: Per-world TFC settings in every climate consumer

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
