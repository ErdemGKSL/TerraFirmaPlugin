---
id: PAR-CLIM-02
title: "Precipitation per position on Paper and the in-game snow-vs-rain check"
area: CLIMATE
status: done
wave: 11
deps: []
size: S
aliases: ["M2-WTH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/weather/PrecipitationService.java"
  - "paper/src/test/java/dev/tfp/paper/weather/Precipitation*"
  - "core/src/main/java/dev/tfp/core/weather/Precipitation*"
  - "core/src/test/java/dev/tfp/core/weather/Precipitation*"
ui: true
in_game_steps:
  - "RCON: op Bot; tfp time set hour 12; tfp time add months 7 (January); weather rain 1000000 (only so the vanilla client draws a storm; it does not change the decision)"
  - "Readout: Bot runs it in the client, not RCON (the console reply to `tfp debug chunk <x> <z>` is empty over RCON until PAR-CLIM-17): `DISPLAY=:<d> xdotool key slash; xdotool type 'tfp debug chunk'; xdotool key Return` (no coordinates: it reads Bot's own position), then read the `[CHAT]` lines of `<game-dir>/logs/latest.log` and copy the `climate now:` and `precipitation here:` lines into verdict.md"
  - "Cold site: tp Bot to a land column below -5 C under the open sky (for example -600 71 0 or higher ground nearby). tfp time add hours 1 and repeat the readout until it shows `precipitation here: SNOW` (TFC's decision follows the climate's rain windows, not /weather)"
  - "Fresh patch: fill a 9x9 area around Bot at ground level with `fill <x1> <y> <z1> <x2> <y+2> <z2> air replace snow`; screenshot facing the cleared patch (cold-site-before)"
  - "Accumulation: tp Bot at least 16 chunks away (survival, wait 30 s so the cold chunks unload and stamp), tfp time add hours 6, tp Bot back to the same spot, wait 30 s, readout again (still SNOW, or note the rain window ended), screenshot the same view (cold-site-after): new snow layers on the cleared patch come from the SNOW decision (SnowService catch-up), not from world generation"
  - "Warm site: tp Bot 0 74 9000 (creative for the teleport so the far chunk loads, then survival), readout shows RAIN above 0 C, screenshot facing the ground under the open sky (warm-site-rain). Client particles are rain at both sites until PAR-CLIM-05 (NP-CLIM-02): the cold-site criterion is the SNOW readout plus the snow built on the cleared patch"
plan:
  - "Current: `core/weather/PrecipitationType.decide` is golden-tested; PrecipitationService exists."
  - "Sample per player position (throttled 20 ticks) and expose `precipitationAt(pos)` to other systems (barrel rain fill, drinking in rain, fire dousing)."
  - "Server check: snow falls on cold ground at the right season, rain in warm areas."
  - "Done: PrecipitationService samples every online player every 20 ticks whether or not a HUD is active (core PrecipitationSample/PrecipitationSamples, newest clock reading wins), uses vanilla isRainingAt position gates (MOTION_BLOCKING top + sky light 15), and exposes sampleOf(player), precipitationAt/isRainingAt(world, pos, rainReaches) and PrecipitationService.current(). Tests: PrecipitationSamplesTest (3), PrecipitationSamplingMockBukkitTest (7, fake clock, forced cold/warm climate). In-game: docs/verification/PAR-CLIM-02/verdict.md (the 1942d21 re-check failed for lack of a per-position readout; re-run with the steps above, which read it from Bot's chat; the console readout over RCON is PAR-CLIM-17; client particles per NP-CLIM-02). The itest weather.precip_sample and the ModuleServices wiring are outside files_owned: follow-up PAR-CLIM-15."
tests:
  - "paper: fake-clock sampling test"
  - "itest: `weather.precip_sample` with a forced climate"
done_criteria:
  - "In-game screenshots: snow at a cold January site, rain at a warm site (checklist in docs/visual-checklists)"
---

# PAR-CLIM-02: Precipitation per position on Paper and the in-game snow-vs-rain check

> see_not_parity: NP-DATA-01, NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
