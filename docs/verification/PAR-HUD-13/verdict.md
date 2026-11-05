# PAR-HUD-13: info plate fits the longest date line; info HUD screen-edge shift: verdict: pass

- Date: 2026-11-05. Tree `731f8aa` plus this item's change (160x22 plate, HUD `pixel.x: 1` on the four info layouts),
  jar from `:dist:shadowJar` with freshly generated content.
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start),
  CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb 1280x720 (`spikes/client/session.sh`,
  `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker), from the crops below and from rows
  and columns measured on the raw 1280x720 shots.

## Steps (RCON)

`forceload add -16 -16 16 16`, stone floor at y 99, `weather clear`, `tfp time set noon` (June 1, 1000), join,
`tp Bot 0 100 0 0 30`, `gamemode survival Bot`, `tfp time add months 3` (September 1), `tfp time add days 7`
(September 8), `tfp time add months 3` (December 8), screenshots at GUI 2; relaunch at GUI 3, December 8 and, after
`tfp time add months 9`, September 8, 1001. This world has 8-day months, so day 8 is its longest day; the two-digit
day of a 30-day month ("September 30, 1000  (Summer)", 150 GUI px, the widest line) is covered by
`BetterHudPackTest.plateIsAtLeastAsWideAsTheLongestDateLinePlusInsetShadowAndMargin`.

## Crops

| file | scale | content |
|---|---|---|
| `gui2-september-topleft-4x.png`, `gui2-december-topleft-4x.png` | 2 | plate, date, thermometer, temperature |
| `gui3-september-topleft-3x.png`, `gui3-december-topleft-3x.png` | 3 | same |

## Checks

| check | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| Plate 160 GUI px wide (top outline 320 / 480 screen px) | pass | pass | raw shots, row scan |
| Date text starts 5 GUI px right of the plate's left edge, thermometer 6 px (same offsets as with the 124 px plate: a wider plate moves nothing else) | pass | pass | plate x 2 / 1 screen px, text x 12 / 16, thermometer tube x 14 / 19 |
| September and December dates fully on the plate, with margin ("December 8, 1000  (Winter)" ends at GUI 138, plate at 160) | pass | pass | all four crops |
| Thermometer and temperature in place on the plate | pass | pass | all four crops |
| Plate fully on screen: left frame column 2 screen px wide at GUI 2 (x 2..3) and 3 px at GUI 3 (x 1..3) | pass | pass | raw shots |

## Note on the PAR-HUD-06 finding

PAR-HUD-06 reported that a 158x22 plate moved the plate about 17 GUI px right and the date and thermometer 17 px left.
This run does not reproduce it: with a 160x22 plate the plate, text and thermometer keep their layout offsets. BetterHud
(`LayoutComponentContainer`, `offset: left`, `align: left`) closes every element with a negative space of its own width,
so the boss-bar line has zero net width whatever the plate's width; a shift of exactly half the width change, in opposite
directions for the plate and the rest, is what the client does when the plate glyph's real advance is the old 124 px
while BetterHud counts 158, i.e. a client or pack cache that still held the old texture. No layout compensation is
needed, so none was added.
