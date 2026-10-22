---
id: PAR-CLIM-02
title: "Precipitation per position on Paper and the in-game snow-vs-rain check"
area: CLIMATE
status: open
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
  - "tfp time set hour 12"
  - "weather rain"
  - "tp Bot to a cold site (tfp debug chunk shows < 0 C) and a warm site; screenshot each"
plan:
  - "Current: `core/weather/PrecipitationType.decide` is golden-tested; PrecipitationService exists."
  - "Sample per player position (throttled 20 ticks) and expose `precipitationAt(pos)` to other systems (barrel rain fill, drinking in rain, fire dousing)."
  - "Server check: snow falls on cold ground at the right season, rain in warm areas."
tests:
  - "paper: fake-clock sampling test"
  - "itest: `weather.precip_sample` with a forced climate"
done_criteria:
  - "In-game screenshots: snow at a cold January site, rain at a warm site (checklist in docs/visual-checklists)"
---

# PAR-CLIM-02: Precipitation per position on Paper and the in-game snow-vs-rain check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
