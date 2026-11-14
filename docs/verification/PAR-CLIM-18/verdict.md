# PAR-CLIM-18 server check

Status: **passed** (2026-11-14, base 9e2d8d8 plus the PAR-CLIM-18 working tree).

- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, `level-seed=1234`, `EXTRA_JVM=-Dtfp.snow.stats=true`),
  CraftEngine, BetterModel, BetterHud, PacketEvents as in `docs/testing/headless.md`, primed and restarted on a fresh
  world. No client: the criterion is a block count, and no player was online (the calendar stood still between the
  commands, `enableTimeStopWhenServerEmpty`).
- Counting as in `docs/verification/PAR-CLIM-03/verdict.md`: the snow layers of chunk (0, -124) (x 0..15,
  z -1984..-1969, y 60..199) are cloned with `filtered minecraft:snow` into a buffer at 2000 60 2000 (force-loaded),
  which is cleared after each count.

## Steps and readouts

| Step | Calendar | Snow layers in chunk (0, -124) |
|---|---|---|
| `tfp time add months 7`, `forceload add 0 -1984` (first load: catch-up of the never-updated window), 15 s | January 1, 1001 06:00 | 56 |
| chunk kept force-loaded (`forceload query`: marked before and after), `tfp time add months 6`, 20 s | July 1, 1001 06:00 | **56 -> 1**, no unload |

After the July skip, `tfp debug chunk 8 -1976` read `snow time stored: 3462000`, the calendar tick after the skip (the
skip pass stamped the loaded chunk), with `temperature 1.3 C, average -7.9 C, rainfall 127.6 mm`. The snow stats and
pipeline lines of the run showed `threadViolations=0` and `decideErrors=0`.

## Criteria

| Criterion | Result |
|---|---|
| After `/tfp time add months 6` with the test chunk force-loaded across the skip, the January snow melts without an unload | Pass: 56 -> 1 layers, stamp moved to the skip's end |
| MockBukkit test | Pass: `paper/src/test/java/dev/tfp/paper/weather/SnowSkipCatchUpMockBukkitTest.java` (5 tests) |
