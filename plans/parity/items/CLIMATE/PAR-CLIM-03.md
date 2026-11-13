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
  - "World check first (the dry-world recapture of 2026-11-13 got 0 layers, which is TFC-consistent for that site): before the server's first start put level-seed=1234 into the run dir's server.properties (spikes/client/server.sh does not set it) and start with TFP_WORLD=1; over RCON `seed` must print [1234], else delete the world and start again. Bot in creative for the whole run (note the health readout in every shot)"
  - "tfp time add months <n> until the HUD date reads January. `tfp debug chunk 8 -1976` (chunk 0,-124) must read rainfall of at least 100 mm (seed 1234 gives 129 to 416 mm there in every month, `SnowCatchUpSiteTest`) and a temperature below -2 C; stop and report the readout if not. Its `would accumulate` line is the precipitation of this instant only and is not the gate: the catch-up reads the month's samples and the mean rainfall"
  - "Floor, all inside chunk (0,-124), no roof (testroom.sh's barrier roof blocks snow, do not use it): Y = surface y at 8 -1976; fill 2 Y+1 -1982 14 Y+12 -1970 air; fill 2 Y-2 -1982 14 Y-1 -1970 dirt; fill 2 Y -1982 14 Y -1970 grass_block; fill 2 Y -1982 4 Y -1980 stone; weather clear; time set noon. Never a snow_block floor"
  - "00-before.png: tp Bot 8 Y+1 -1969 180 15 (low angle looking north over the floor), shot at once: grass and stone, no snow; count snow in the chunk (clone 0 Y-2 -1984 15 Y+12 -1969 to a far buffer with filtered minecraft:snow, then clear the buffer) and clear any with fill ... air replace snow"
  - "tfp debug snowtime 8 -1976 1000, exactly once; then tp Bot 8 150 -1500; forceload remove all; poll `execute unless loaded 8 64 -1976` every 5 s until it succeeds (max 120 s); tp Bot 8 Y+1 -1969 180 15 and wait 15 s for the catch-up hops"
  - "01-after-low.png from the 00 camera (layers visible against grass and stone); 02-after-f3.png: F3 looking at a layer (Targeted Block minecraft:snow[layers=n]); count again as in 00 (expected: up to 64 accumulation steps for the chunk)"
  - "Melt: Bot away, forceload remove all, unloaded as above, tfp time add months 6, tp back, wait 15 s, 03-july-low.png from the 00 camera, count again (lower than January). The shots of snow piles over plants and ice on a pool moved to PAR-CLIM-19 (PROTOCOL 6.5, they show PAR-CLIM-04's blocks)"
plan:
  - "Current: SnowRule, SnowCatchUp, SnowService and the catch-up on load are done; server check of accumulation passed once."
  - "Write the lazy-vs-stepped equivalence test over one climate sequence (stepped onTickChunk vs SnowCatchUp)."
  - "Server check: melting on a warm day, first-load cost of new chunks recorded in docs/perf."
  - "Close the rain and melt divergence rows in the ledger or update them."
  - "Done: SnowLazyVsSteppedTest (5 tests: stepped replay vs its expectation, catch-up vs fuzzed stepped expectation over a year at three sites, bounds vs the plain stepped run, monthly sign, capped month); SnowCatchUpStats (+ test, -Dtfp.snow.stats=true) for the first-load cost in docs/perf/snow-catch-up.md; server check docs/verification/PAR-CLIM-03/verdict.md (January catch-up 0 -> 60 layers, July melt 60 -> 5); ledger: rain row closed, melt position kept"
  - "Repair (review 2026-11-13): the workflow recapture was inconclusive (snow_block floor, extra snowtime write, no F3); in_game_steps now build the room on grass/stone with plants and a pool, one snowtime write, a clean unload/reload, low-angle and F3 shots. Test Javadoc states the one-column simplification; ledger: live rate of the sampler, calendar-tick units in the live rain branch"
  - "Unblock (2026-11-13, third recapture rejected with 0 layers): root cause is the test world, not the catch-up. The recapture read rainfall 21 mm and average -1.5 C at 8 -1976; seed 1234 gives 129 to 416 mm there in every month, so the run was on another world (spikes/client/server.sh never sets level-seed and the steps did not check it). SnowCatchUp.plan walks the month's samples with getRain per sample against the mean rainfall, as TFC's onTickChunk (WeatherHelpers:232-275); at 21 mm no sample clears clampedMap(21, 0, 500, 1, 0) = 0.958, so TFC places nothing there either. SnowCatchUpSiteTest pins both: the seed-1234 site plans the capped 64 steps at any surface height from stamp 1000 on January 1, 1001; the dry site plans none even at -11.6 C. in_game_steps now set and check level-seed=1234 and gate on rainfall >= 100 mm (not on the instant `would accumulate` line); the pile/ice/F3 room shots moved to PAR-CLIM-19 (PROTOCOL 6.5, after PAR-CLIM-04's blocks); skip-time catch-up of loaded chunks is PAR-CLIM-18; ledger: snow catch-up row fixed to 4 cells, 'full night's sleep', live-rate note split into its own row"
  - "Done: SnowLazyVsSteppedTest and SnowCatchUpSiteTest green; in-game accumulation 0 -> 60 and July melt 60 -> 5 in docs/verification/PAR-CLIM-03/verdict.md (with the dry-world recapture explained); ledger rows for rain, melt and the catch-up updated; follow-ups PAR-CLIM-18 (skip-time catch-up) and PAR-CLIM-19 (room shots after PAR-CLIM-04)"
tests:
  - "core: SnowLazyVsSteppedTest"
  - "core: SnowCatchUpSiteTest"
done_criteria:
  - "Equivalence test green; melt observed in-game; ledger rows updated"
---

# PAR-CLIM-03: Snow and ice accumulation: lazy-vs-stepped equivalence and server check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
