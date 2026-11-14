---
id: PAR-CLIM-21
title: "Snow and ice stop with the weather flag off (SnowService live step and catch-up), as TFC onTickChunk"
area: CLIMATE
status: open
wave: 15
deps: [PAR-CLIM-20]
size: S
aliases: ["follow-up of PAR-CLIM-20 (2026-11-14): SnowService decides precipitation from the climate itself, not through PrecipitationService, and is outside PAR-CLIM-20's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/weather/SnowService.java"
  - "paper/src/test/java/dev/tfp/paper/weather/SnowServiceWeatherFlagTest.java"
ui: false
plan:
  - "Moved from PAR-CLIM-20 (2026-11-14): PrecipitationService now reads the weather flag (ClimateWeatherCycle.enabled) and vanilla's level.isRaining() gate (PrecipitationService.decide / climateRaining), but SnowService decides snow and melt with climate.rainIntensity(t) directly (the live step near SnowService:606 and the catch-up's SnowCatchUp.Climate.rainIntensity near :372). TFC's WeatherHelpers.onTickChunk (line 209) returns at once when !tracker.isWeatherEnabled(): no snow accumulation and no melting at all (live and catch-up)."
  - "Read the flag the way PrecipitationService.weatherOf does (ModuleServices ClimateWeatherCycle, captured on the global/region thread into the snapshot) and skip both accumulation and melting when it is off, in the live step and in the catch-up (decide what happens to the chunk's snow stamp so turning the flag back on does not replay the off time; TFC still sets lastRandomTick only when the flag is on, so the next on-tick catches up at most one month). Ledger: update the 'isWeatherEnabled' divergence row."
tests:
  - "paper: SnowServiceWeatherFlagTest (flag off: a cold wet site in a rain window plans no accumulation and a warm snowy site no melt, live and catch-up)"
done_criteria:
  - "With the weather flag off SnowService neither accumulates nor melts snow (live and catch-up); tests green"
---

# PAR-CLIM-21: Snow and ice stop with the weather flag off (SnowService live step and catch-up), as TFC onTickChunk

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
