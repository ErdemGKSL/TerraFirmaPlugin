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
  - "docs/visual-checklists/PAR-CLIM-02.md"
ui: true
in_game_steps:
  - "Server: fresh TFC world with `level-seed=1234` in server.properties before the first start (the sites and the rain window below are for that seed; for another seed find them by stepping hours and by the heightmap), `EXTRA_JVM=-Xmx5G` (3 GB runs out of heap after a few far teleports, see verdict.md). Teleport with plain `tp`, never `spreadplayers` with a large radius (it generates hundreds of chunks on the tick thread and the watchdog kicks Bot)"
  - "RCON: op Bot; gamemode creative Bot; effect give Bot night_vision infinite 0 true; gamerule advance_weather false; weather rain 1000000 (only so the vanilla client draws a storm; it does not change the decision); tfp time add months 7 (January 1); never use `tfp time set hour`, which moves forward to the next such hour and can skip the window"
  - "Readout: Bot runs it in the client, not RCON (the console reply to `tfp debug chunk <x> <z>` is empty over RCON until PAR-CLIM-17): `DISPLAY=:<d> xdotool key slash; xdotool type 'tfp debug chunk'; xdotool key Return` (no coordinates: it reads Bot's own position), then read the `[CHAT]` lines of `<game-dir>/logs/latest.log` and copy the `climate now:` and `precipitation here:` lines into verdict.md"
  - "Cold site, outside the window: tp Bot 0 125 -2000 (about -19 C), readout shows NONE under the vanilla storm"
  - "Window: tfp time add hours 109 (January 5, about 19:00; the window runs to January 6 14:00). setblock 8 124 -1976 barrier; tp Bot 8.5 125 -1975.5 180 45 (camera over the patch); readout shows SNOW"
  - "Fresh patch: fill 4 112 -1989 12 123 -1981 air replace snow; wait 12 s for the chat to fade; screenshot (cold-patch-before)"
  - "Accumulation: tp Bot 8.5 150 -1500.5 (30 chunks away), wait 35 s so the cold chunks unload and stamp, tfp time add hours 6, tp Bot 8.5 125 -1975.5 180 45, wait 25 s, readout (still SNOW), screenshot after 12 s (cold-patch-after), count with `clone 4 112 -1989 12 123 -1981 4 260 -1989 filtered minecraft:snow` (then fill the copy with air): new snow on the cleared patch comes from the SNOW decision (SnowService catch-up), not from world generation"
  - "Warm site, same window: tp Bot -255.5 90 9600.5, wait 25 s, then `execute positioned -256 0 9600 positioned over motion_blocking run tp Bot ~ ~ ~ 0 25` (land, about 30 C; 0 74 9000 is ocean for this seed and Bot sinks there, so the readout is NONE), readout shows RAIN, screenshot (warm-rain). Client particles are rain at both sites until PAR-CLIM-05 (NP-CLIM-02): the cold-site criterion is the SNOW readout plus the snow built on the cleared patch"
plan:
  - "Current: `core/weather/PrecipitationType.decide` is golden-tested; PrecipitationService exists."
  - "Sample per player position (throttled 20 ticks) and expose `precipitationAt(pos)` to other systems (barrel rain fill, drinking in rain, fire dousing)."
  - "Server check: snow falls on cold ground at the right season, rain in warm areas."
  - "Implemented: PrecipitationService samples every online player every 20 ticks whether or not a HUD is active (core PrecipitationSample/PrecipitationSamples, newest clock reading wins), uses vanilla isRainingAt position gates (MOTION_BLOCKING top + sky light 15), and exposes sampleOf(player), precipitationAt/isRainingAt(world, pos, rainReaches) and PrecipitationService.current(). Tests: PrecipitationSamplesTest (3), PrecipitationSamplingMockBukkitTest (7, fake clock, forced cold/warm climate). The itest weather.precip_sample and the ModuleServices wiring are outside files_owned: follow-up PAR-CLIM-15; the console readout over RCON is PAR-CLIM-17."
  - "Criterion reworded (unblocker, 2026-11-13): the client cannot draw snowflakes from the server's decision (NOT-PARITY NP-CLIM-02; the cold variant biome is PAR-CLIM-05, blocked on its own owner decision and outside these files), so the cold-site proof is the SNOW readout plus snow built on a cleared patch, as the in_game_steps already said. docs/visual-checklists/PAR-CLIM-02.md is added to files_owned because the criterion names it."
  - "Done: run 3 passed in January (docs/verification/PAR-CLIM-02/verdict.md): SNOW at -17 C with 29 snow blocks built on the cleared 9x9 patch after the 6 h catch-up, RAIN at 30 C in the same window, NONE outside the window; checklist docs/visual-checklists/PAR-CLIM-02.md. No Java change in this round; core, paper, datagen tests and shadowJar green."
tests:
  - "paper: fake-clock sampling test"
  - "itest: `weather.precip_sample` with a forced climate"
done_criteria:
  - "In-game, in January (checklist in docs/visual-checklists/PAR-CLIM-02.md): Bot's `/tfp debug chunk` reads `precipitation here: SNOW` at a sub-zero site and snow is built on a freshly cleared patch there, and reads `RAIN` at a warm site in the same rain window. The client's falling particles stay rain at the cold site (accepted substitute NOT-PARITY NP-CLIM-02, ledger Divergences row 'The client's snow or rain follows the biome temperature ...', revisit PAR-CLIM-05)"
---

# PAR-CLIM-02: Precipitation per position on Paper and the in-game snow-vs-rain check

> see_not_parity: NP-DATA-01, NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
