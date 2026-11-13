---
id: PAR-CLIM-19
title: "Snow catch-up in-game verification: snow piles on plants, ice on a pool and the July melt from one room"
area: CLIMATE
status: open
wave: 14
deps: [PAR-CLIM-04]
size: S
aliases: ["deferred check of PAR-CLIM-03 (PROTOCOL 6.5): the pile and ice shots need PAR-CLIM-04's CraftEngine snow and ice piles"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
files_owned:
  - "docs/verification/PAR-CLIM-19/**"
ui: true
in_game_steps:
  - "World check first (a dry world gives 0 layers, as TFC would; see docs/verification/PAR-CLIM-03/verdict.md): before the server's first start put level-seed=1234 into the run dir's server.properties (spikes/client/server.sh does not set it) and start with TFP_WORLD=1; over RCON `seed` must print [1234], else delete the world and start again"
  - "Bot in creative for the whole run. tfp time add months <n> until the HUD date reads January. `tfp debug chunk 8 -1976` (chunk 0,-124) must read rainfall of at least 100 mm (seed 1234 gives 129 to 416 mm there in every month) and a temperature below -2 C; stop and report the readout if not. Its `would accumulate` line is the precipitation of this instant only and is not the gate"
  - "Room, all inside chunk (0,-124), no roof: Y = surface y at 8 -1976; fill 2 Y+1 -1982 14 Y+12 -1970 air; fill 2 Y-2 -1982 14 Y-1 -1970 dirt; fill 2 Y -1982 14 Y -1970 grass_block; fill 2 Y -1982 4 Y -1980 stone; fill 3 Y+1 -1977 5 Y+1 -1975 short_grass; fill 11 Y -1977 13 Y -1975 water; weather clear; time set noon"
  - "00-before.png: tp Bot 8 Y+1 -1969 180 15, shot at once: grass, stone, plants and open water, no snow"
  - "tfp debug snowtime 8 -1976 1000, exactly once; then Bot to 8 150 -1500, forceload remove all, poll `execute unless loaded 8 64 -1976` every 5 s until it succeeds (max 120 s), tp Bot 8 Y+1 -1969 180 15, wait 15 s"
  - "01-after-low.png from the 00 camera; 02-after-f3.png: F3 on a snow layer, on the former short_grass strip (snow pile, PAR-CLIM-04's block) and on the pool (ice pile or ice); 8x crops next to TFC's snow_pile / snow textures"
  - "Readouts: clone 2 Y-2 -1982 14 Y+12 -1970 to a far buffer with filtered minecraft:snow (and the CE snow pile id) for the count, then clear the buffer; execute if block 12 Y -1976 minecraft:ice; tfp debug chunk 8 -1976"
  - "Melt: Bot away, forceload remove, unloaded, tfp time add months 6, reload, 03-july-low.png from the 00 camera, count again"
plan:
  - "Moved from PAR-CLIM-03 (2026-11-13, PROTOCOL 6.5): PAR-CLIM-03's counts (0 -> 60 layers, 60 -> 5 on the July melt) are in docs/verification/PAR-CLIM-03/verdict.md; the shots of piles over plants and ice on a pool show PAR-CLIM-04's blocks, so they run after it."
tests: []
done_criteria:
  - "01 shows snow layers on the grass and stone and a snow pile over the short_grass strip, 02 shows the F3 targets, the pool is frozen or holds an ice pile, and 03 shows the July melt (count lower than in January); verdict in docs/verification/PAR-CLIM-19/verdict.md"
---

# PAR-CLIM-19: Snow catch-up in-game verification: snow piles on plants, ice on a pool and the July melt from one room

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
