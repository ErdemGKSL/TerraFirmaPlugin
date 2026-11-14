# PAR-CLIM-20 verification: climate weather cycle in game, itest weather.cycle, PrecipitationService and the weather flag

Date: 2026-11-14. Build: base `63be09e` plus this item's change (the PAR-CLIM-20 commit). Paper 1.21.11 (server run dir
fresh, `TFP_WORLD=1`, primed and restarted per `docs/testing/headless.md`), vanilla 1.21.11 client under Xvfb (one bot,
`Bot`), CraftEngine pack applied 3 s after the join. Seed 2345879151979581053.

## Sites

Picked from the climate model (scratch program on the worldgen classes, outside the repo): dry column -4000, 3000 (average
rainfall 89 mm, `tfp debug chunk` instant rainfall 138 to 148 mm in June) and wet column 5000, 4000 (average 405 mm,
instant 384 to 387 mm), and the next calendar window where the wet site rains and the dry one does not for at least
6,000 ticks (from calendar 1,037,800).

## Steps run

1. `gamerule advance_weather true`, then after 2 s `gamerule advance_weather` read **false** (the cycle holds it off).
   `tfp debug chunk` at both sites at calendar 966,433: NONE at both (no rain window).
2. `tfp time add ticks 72000` (calendar 1,038,566, June 4): `tfp debug chunk 5000 4000` **RAIN**, `-4000 3000` **NONE**.
   One bot, 8 s (160 ticks) after each teleport before each shot:
   - `01-wet-sky-cal1038850.png`: wet site, calendar 1,038,850, looking up: dark rain sky.
   - `02-dry-sky-cal1039000.png`: dry site, calendar 1,039,000: bright clear sky, no rain.
   - `03-wet-ground-cal1040283.png`: wet site, calendar 1,040,283, looking down: rain streaks and splashes.
   - `04-dry-cal1040516.png`: dry site, calendar 1,040,516: clear sky, no rain drawn.
   All four calendar ticks lie in the same climate rain window (debug readout RAIN at the wet site and NONE at the dry
   site at each of them).
3. At the wet site, vanilla `weather clear`, then 5 s (100 ticks): `05-wet-after-weather-clear-cal1039866.png` still shows
   rain; `tfp debug chunk 5000 4000` RAIN at calendar 1,039,866; `gamerule advance_weather` still false.

## Self-test

`:itest:runSelfTest -Pmc=1.21.11` (the self-test world has vanilla terrain, so the case runs its own
`ClimateWeatherCycle` over it, see `WeatherCycleCase`):

- Paper 1.21.11: `weather.cycle` PASSED (calendar 10,596,219 storm, rule false; weather clear undone; flag off: clear and
  precipitation NONE; flag on: storm again; calendar 10,606,820 clear). `weather.precip_sample` PASSED. The run's one
  failure, `device.roundtrip` "after the restart", was the second start of a run dir reused from an earlier run in this
  session (the probe's stored ticks from that run); not touched by this item.
- Folia 1.21.11: `weather.cycle` PASSED (calendar 5,568,203 storm, rule false, ... calendar 5,577,204 clear),
  `weather.precip_sample` PASSED, whole run BUILD SUCCESSFUL, no thread-ownership finding in the log.

## Verdict

| Criterion | Result |
|---|---|
| Rain drawn at the wet site and none at the dry site in the same rain window (one bot: both calendar ticks noted) | pass (01 to 04) |
| After `weather clear` the climate weather is back within a second | pass (05; the storm flag is reset on the next 20-tick sync) |
| itest `weather.cycle` green on Paper and Folia | pass |
| PrecipitationService gives NONE with the weather flag off; tests green | pass (`PrecipitationServiceWeatherFlagTest`, itest flag-off step) |
