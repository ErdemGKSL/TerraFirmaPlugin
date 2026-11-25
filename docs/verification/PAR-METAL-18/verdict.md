# PAR-METAL-18 verdict: bells (bronze, brass)

Date: 2026-11-25. HEAD at the run: 53e33e7 plus the item's working changes. Server: Paper 1.21.11 build 132, CraftEngine 26.9.2,
BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449 (ports 45575 / 8373, display :111).

## Result: Pending in-game check (container cannot run client)

The headless client could not be kept running next to the server: after the join attempt the container's memory cgroup
killed the server JVM (`oom-kill ... task=java`, anon-rss 4 GB; 16 GB machine shared with other agents' Gradle runs), so
no screenshot exists. Nothing about the item failed; the visual and ring criteria are not yet judged.

## What was verified without a client (server start, second start after priming)

- `bell` module enabled (it is the last entry of the `Feature modules:` line); no exception in the log.
- CraftEngine loaded `configuration/bell/bell.yml` with no issue on the second start (the "Cannot allocate enough real
  block states" lines appear only on the priming start, for many files, as documented in headless.md section 3).
- No missing-model or missing-texture warning for `bell/*` models, `item/{brass,bronze}_bell` or the body textures.

## Steps still to run (in_game_steps of the item)

1. `ce item give Bot tfp:brass_bell` and `tfp:bronze_bell` (the item shows the TFC bell sprite).
2. Place each on the floor, on a ceiling and on a wall (single wall) and between two walls: the frame (posts / bar) and the
   bell body are drawn, the outline matches vanilla's bell, the facing follows the player or the wall.
3. Right click: the bell sound at volume 2; brass lower than bronze; a hammer in the main hand a tenth lower.
4. Shoot an arrow at it: it rings.
5. Screenshots at GUI scale 2 and 3 of the placed bells (8x crop of the body texture) in this folder.

Criterion "Ringing a brass bell": pending these steps.
