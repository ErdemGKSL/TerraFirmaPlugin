---
id: PAR-CLIM-03
title: "Snow and ice accumulation: lazy-vs-stepped equivalence and server check"
area: CLIMATE
status: done
wave: 12
deps: [PAR-CLIM-02]
size: S
aliases: ["M2-WTH-02", "M2-WTH-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/weather/Snow*"
  - "core/src/test/java/dev/tfp/core/weather/Snow*"
  - "paper/src/main/java/dev/tfp/paper/weather/Snow*"
  - "paper/src/test/java/dev/tfp/paper/weather/Snow*"
  - "paper/src/main/java/dev/tfp/paper/weather/ChunkSnowTime.java"
ui: true
in_game_steps:
  - "Fresh world (level-seed=1234). Bot in creative for the whole run (no damage; note the health readout in every shot, a drop in creative is a bug to report). tfp time add months <n> until the HUD date reads January; tfp debug chunk 8 -1976 must read below -5 C (chunk 0,-124)"
  - "Build the room on a contrasting floor, all inside chunk (0,-124), no roof (testroom.sh's barrier roof blocks snow, do not use it): Y = surface y at 8 -1976; fill 2 Y+1 -1982 14 Y+12 -1970 air; fill 2 Y-2 -1982 14 Y-1 -1970 dirt; fill 2 Y -1982 14 Y -1970 grass_block; fill 2 Y -1982 4 Y -1980 stone; fill 3 Y+1 -1977 5 Y+1 -1975 short_grass; fill 11 Y -1977 13 Y -1975 water; weather clear; time set noon"
  - "00-before.png: tp Bot 8 Y+1 -1969 180 15 (south edge, low angle looking north over the floor), shot at once: grass, stone, plants and open water, no snow"
  - "tfp debug snowtime 8 -1976 1000, exactly once, nothing else written to the chunk afterwards"
  - "Clean unload/reload: tp Bot 8 150 -1500; forceload remove all; poll execute unless loaded 8 64 -1976 every 5 s until it succeeds (max 120 s); then tp Bot 8 Y+1 -1969 180 15 and wait 15 s for the catch-up hops"
  - "01-after-low.png: same camera as 00 (low angle, layer heights and edges visible against grass and stone); 02-after-f3.png: xdotool key F3, Bot looking at a snow layer on the floor (Targeted Block shows minecraft:snow[layers=n], our placeholder) and a second F3 shot looking at the former short_grass strip (snow pile placeholder) or the pool (ice); 8x crops of a layer edge and of the pool next to the TFC snow_pile / snow textures"
  - "Readouts for the verdict: clone 2 Y-2 -1982 14 Y+12 -1970 to a far buffer with filtered minecraft:snow (count, then clear the buffer), execute if block 12 Y -1976 minecraft:ice, tfp debug chunk 8 -1976 (pile count). Melt check: Bot away, forceload remove, unloaded, tfp time add months 6, reload, 03-july-low.png from the 00 camera, count again"
plan:
  - "Current: SnowRule, SnowCatchUp, SnowService and the catch-up on load are done; server check of accumulation passed once."
  - "Write the lazy-vs-stepped equivalence test over one climate sequence (stepped onTickChunk vs SnowCatchUp)."
  - "Server check: melting on a warm day, first-load cost of new chunks recorded in docs/perf."
  - "Close the rain and melt divergence rows in the ledger or update them."
  - "Done: SnowLazyVsSteppedTest (5 tests: stepped replay vs its expectation, catch-up vs fuzzed stepped expectation over a year at three sites, bounds vs the plain stepped run, monthly sign, capped month); SnowCatchUpStats (+ test, -Dtfp.snow.stats=true) for the first-load cost in docs/perf/snow-catch-up.md; server check docs/verification/PAR-CLIM-03/verdict.md (January catch-up 0 -> 60 layers, July melt 60 -> 5); ledger: rain row closed, melt position kept"
  - "Repair (review 2026-11-13): the workflow recapture was inconclusive (snow_block floor, extra snowtime write, no F3); in_game_steps now build the room on grass/stone with plants and a pool, one snowtime write, a clean unload/reload, low-angle and F3 shots. Test Javadoc states the one-column simplification; ledger: live rate of the sampler, calendar-tick units in the live rain branch"
tests:
  - "core: SnowLazyVsSteppedTest"
done_criteria:
  - "Equivalence test green; melt observed in-game; ledger rows updated"
---

# PAR-CLIM-03: Snow and ice accumulation: lazy-vs-stepped equivalence and server check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
