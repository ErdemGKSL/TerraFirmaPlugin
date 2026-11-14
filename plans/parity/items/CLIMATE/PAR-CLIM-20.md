---
id: PAR-CLIM-20
title: "Climate weather cycle follow-up: in-game check (local rain per player), itest weather.cycle, PrecipitationService honours the weather flag"
area: CLIMATE
status: done
wave: 14
deps: [PAR-CLIM-12]
size: S
aliases: ["follow-up of PAR-CLIM-12 (2026-11-14): its in-game check, its itest case and the weather flag in PrecipitationService need files outside PAR-CLIM-12's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/mixin/ServerLevelMixin.java"
  - "src/main/java/net/dries007/tfc/client/ClimateRenderCache.java"
files_owned:
  - "docs/verification/PAR-CLIM-20/**"
  - "itest/src/main/java/dev/tfp/itest/cases/WeatherCycleCase.java"
  - "paper/src/main/java/dev/tfp/paper/weather/PrecipitationService.java"
  - "paper/src/test/java/dev/tfp/paper/weather/PrecipitationServiceWeatherFlagTest.java"
ui: true
in_game_steps:
  - "Fresh TFC world; gamerule doWeatherCycle (advance_weather on 1.21.11+) true; tfp debug chunk at a dry site (rainfall < 100 mm) and at a wet site (> 400 mm); note precipitation; the gamerule reads false after one second"
  - "tfp time add hours 1 repeatedly until tfp debug chunk at the wet site shows RAIN; with two bots, one standing at the wet site and one at the dry site, each for at least 120 ticks since its last join or teleport (the local rain level fades 0.01 per tick, as TFC, plus up to 20 ticks of decision lag), screenshot both skies in the same tick: rain falls at the wet site and none is drawn at the dry site. With one bot, wait at least 120 ticks after each teleport and note both calendar ticks (same climate rain window)"
  - "weather clear (vanilla command); wait 100 ticks; screenshot: the climate cycle keeps the TFC weather (as TFC, where advanceWeatherCycle is replaced)"
plan:
  - "Moved from PAR-CLIM-12 (2026-11-14): PAR-CLIM-12 built and unit-tested the cycle (core/weather/cycle, paper/weather/cycle: ClimateWeatherCycle, VanillaWeatherGates, WeatherCycleModule); its worker had no headless-client session to run, and the case file and PrecipitationService are outside its files."
  - "itest `weather.cycle` case (WeatherCycleCase, add it to SelfTestSuite): on the self-test world, force two calendar ticks through CalendarService.skip (one in a climate rain window, one outside), wait one sync (20 ticks) and assert World.hasStorm() follows ClimateWeatherCycle.target, and that the weather rule reads false."
  - "PrecipitationService.decide reads `services().get(ClimateWeatherCycle.class).map(c -> c.enabled(world))` (TFC: rainIntensity = isWeatherEnabled ? getRain : -1) so snow, HUD, barrel, drinking and firestarter see no rain with the flag off; add vanilla's `level.isRaining()` gate (rain level above 0.2; on Paper: World.hasStorm() as the closest server-side reading) now that the climate drives the vanilla rain (ledger row 'TFC answers Level.isRainingAt ...'). Update the ledger rows 'isWeatherEnabled' and 'TFC answers Level.isRainingAt'."
  - "Done (2026-11-14): PrecipitationService.decide applies the world's weather flag (weatherEnabled: the module's ClimateWeatherCycle, found in ModuleServices once enabled; true for worlds the cycle does not drive and without the module) and vanilla's isRaining gate from the lazy rain level (climateRaining, WeatherCycle.levelsAt) instead of World.hasStorm(), which lags the climate by up to a sync and broke weather.precip_sample in the vanilla-terrain self-test world. PrecipitationServiceWeatherFlagTest (5 tests); itest weather.cycle (runs its own ClimateWeatherCycle over the vanilla-terrain self-test world) PASSED on Paper and Folia 1.21.11; in-game check docs/verification/PAR-CLIM-20/verdict.md (pass). Snow/ice and the flag: follow-up PAR-CLIM-21 (SnowService is outside this item's files)."
tests:
  - "itest: `weather.cycle` case (storm state at two forced calendar ticks)"
  - "paper: PrecipitationServiceWeatherFlagTest (flag off gives NONE at a wet site in a rain window)"
done_criteria:
  - "Screenshots: rain drawn at the wet site and none at the dry site at the same calendar tick; after `weather clear` the climate weather is back within a second; verdict in docs/verification/PAR-CLIM-20/verdict.md"
  - "itest weather.cycle green on Paper and Folia; PrecipitationService gives NONE with the weather flag off; tests green"
---

# PAR-CLIM-20: Climate weather cycle follow-up: in-game check (local rain per player), itest weather.cycle, PrecipitationService honours the weather flag

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
