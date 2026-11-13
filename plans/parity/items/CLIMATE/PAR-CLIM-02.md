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
  - "tfp time set hour 12, then tfp time add months 7 (January)"
  - "tfp time add hours 1 until /tfp debug chunk shows precipitation (not NONE) at the cold column: TFC's decision follows the climate's rain windows, not /weather"
  - "weather rain 1000000 (only so the vanilla client draws a storm; it does not change the decision)"
  - "tp Bot to a cold site (tfp debug chunk shows < 0 C, precipitation SNOW) and a warm site (RAIN); screenshot each facing the ground under the open sky. Client particles are rain at both sites until PAR-CLIM-05 (NP-CLIM-02)"
plan:
  - "Current: `core/weather/PrecipitationType.decide` is golden-tested; PrecipitationService exists."
  - "Sample per player position (throttled 20 ticks) and expose `precipitationAt(pos)` to other systems (barrel rain fill, drinking in rain, fire dousing)."
  - "Server check: snow falls on cold ground at the right season, rain in warm areas."
  - "Done: PrecipitationService samples every online player every 20 ticks whether or not a HUD is active (core PrecipitationSample/PrecipitationSamples, newest clock reading wins), uses vanilla isRainingAt position gates (MOTION_BLOCKING top + sky light 15), and exposes sampleOf(player), precipitationAt/isRainingAt(world, pos, rainReaches) and PrecipitationService.current(). Tests: PrecipitationSamplesTest (3), PrecipitationSamplingMockBukkitTest (7, fake clock, forced cold/warm climate). In-game: docs/verification/PAR-CLIM-02/verdict.md (pass; client particles per NP-CLIM-02). The itest weather.precip_sample and the ModuleServices wiring are outside files_owned: follow-up PAR-CLIM-15."
tests:
  - "paper: fake-clock sampling test"
  - "itest: `weather.precip_sample` with a forced climate"
done_criteria:
  - "In-game screenshots: snow at a cold January site, rain at a warm site (checklist in docs/visual-checklists)"
---

# PAR-CLIM-02: Precipitation per position on Paper and the in-game snow-vs-rain check

> see_not_parity: NP-DATA-01, NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
