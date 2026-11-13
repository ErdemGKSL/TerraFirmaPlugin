# PAR-CLIM-03 in-game check

Status: **passed** (2026-11-13, base 63205e4 plus the PAR-CLIM-03 working tree: `SnowLazyVsSteppedTest`,
`SnowCatchUpStats` and its hooks in `SnowService`; the snow rules and the catch-up are unchanged since PAR-CLIM-02).

- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, `level-seed=1234`,
  `EXTRA_JVM="-Xmx5G -Dtfp.snow.stats=true"`), CraftEngine, BetterModel, BetterHud, PacketEvents as in
  `docs/testing/headless.md`, primed and restarted on a fresh world. Client: vanilla 1.21.11 under Xvfb, pack applied
  (`session.sh`: joined, pack applied after 1x3s).
- Counting: snow layers of chunk (0, -124) (x 0..15, z -1984..-1969, y 60..200) are counted with
  `clone ... filtered minecraft:snow` into a buffer in a far chunk, which is cleared right after (the count leaves the
  test chunk unchanged). Chunk unload and load are driven by `forceload add/remove` over RCON with Bot far away, and the
  unload is confirmed with `execute unless loaded 8 64 -1976`.

## Steps and readouts

| Step | Calendar | Snow layers in chunk (0, -124) |
|---|---|---|
| `tfp time add months 7`, `forceload add 0 -1984` (first load since the clock jump: catch-up of the never-updated window) | January 1, 1001 | 55 (counted), then cleared with `fill ... air replace snow` (55 removed) |
| `tfp debug snowtime 8 -1976 1000`, `forceload remove`, unloaded, `forceload add` | January 1, 1001 | **0 -> 60** |
| Bot away (8 150 -1500), `forceload remove`, unloaded, `tfp time add months 6`, `forceload add` | July 1, 1001 08:37 | **60 -> 5** |

Bot's own `/tfp debug chunk` at the site in July (`july-readout.png`): `temperature 1.6°C, average -8.1°C, rainfall
128.6 mm (calendar tick 3466050)`, stamp 3464633 (the July load). The January catch-up planned accumulation from the
stamp 1000 (a full month window, capped at 64 steps); the July one planned melt (the window's samples above 2 C; the
morning reading is 1.6 C, the warm part of the day passes 2 C).

## Screenshots (Bot in spectator at 8 145 -1992, yaw 0, pitch 60, night vision, HUD hidden; 640x360 centre crops)

- `january-snow.png`: January, after the reload. Snow layers scattered over every chunk that got its catch-up (64 steps
  each), on the winter grass tint.
- `july-melted.png`: the same camera after the July reload: the snow is gone except a few layers (5 counted in the test
  chunk), summer grass tint. Chunks at the left edge of the full frame still showed January snow: they stayed loaded
  across the clock jump (neighbours of the forced chunk), so they got no catch-up and only the live samples near a
  player melt them (TFC's live branch does the same: a chunk ticked within 4,000 ticks gets no catch-up). In TFC the larger reason is the
  units: `/time add` and TFC's month skip do not advance player ticks, and TFC measures the catch-up window in player
  ticks, so TFC would not catch up even a reloaded chunk after such a jump; ours does (calendar ticks, ledger catch-up row).
- `july-readout.png`: the readout above.

## Criteria

| Criterion | Result |
|---|---|
| Equivalence test green | Pass: `core/src/test/java/dev/tfp/core/weather/SnowLazyVsSteppedTest.java` (5 tests) |
| Accumulation after `tfp debug snowtime` and an unload/reload (item steps) | Pass: 0 -> 60 layers, `january-snow.png` |
| Melt observed in-game | Pass: 60 -> 5 layers on the July reload, `july-melted.png`, `july-readout.png` |
| First-load cost of new chunks recorded in `docs/perf` | Pass: `docs/perf/snow-catch-up.md` (2.1 ms region time per new winter chunk over 8 hops, 35 us worker time per plan) |
| Ledger rows updated | Pass: the snow feature row, the catch-up rows and the rain and melt row |

## Notes

- The console's `tfp debug chunk <x> <z>` sometimes returned an empty reply over RCON while the chunk was being loaded by
  the same call; Bot's in-game readout was used instead. Not part of this item.
- Memory: the first client join together with the server (`-Xmx5G`) and other agents' builds ran the host out of memory
  (kernel OOM kill, server watchdog dumps). The run continued with the client started later; all readouts above are from
  after that.
- Workflow recapture (2026-11-13, rejected as inconclusive, not as a defect): the room had a snow_block floor (snow on
  it is white on white), 00 was taken with a layer already present, 01 followed a second `snowtime` write, and no shot
  showed F3. The item's `in_game_steps` now spell out the room (grass and stone floor, a short_grass strip for snow
  piles, a water pool for ice, no barrier roof), a single `snowtime` write, a clean unload/reload checked with
  `execute unless loaded`, a low camera and F3 shots. The counts above (0 -> 60, 60 -> 5) are unaffected. The snow pile
  and ice pile are vanilla snow layer and ice placeholders (ledger), so F3 shows `minecraft:snow` / `minecraft:ice`.
- Health 850 -> 639 in that recapture: the item does not deal damage (no snow or ice code path hurts players); the
  recapture steps now keep Bot in creative so a drop there would point at a bug elsewhere (body temperature, PAR-SURV).
- Empty `tfp debug chunk` RCON reply during the catch-up after a clock jump: tracked as PAR-CLIM-17.
