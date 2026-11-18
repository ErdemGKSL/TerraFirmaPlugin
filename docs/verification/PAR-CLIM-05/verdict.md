# PAR-CLIM-05 verdict: seasonal grass tint band resolution

Date: 2026-11-18 (repair round). Base commit 5915734 plus the repair change (this commit). Server: Paper
1.21.11-132, CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents (headless recipe, `docs/testing/headless.md`);
client: vanilla 1.21.11 under Xvfb, pack applied.

## Decision (owner decision delegated to the manager)

The first round chose **(a) more temperature bands** (7 x 4 = 28 variants per biome). The verify stage then measured
October's grass about 10 degrees greener than June's (hue 98 to 110), because TFC's grass colormap alone gets greener as
it cools at a fixed groundwater. The criterion asks for October warmer and yellower, so the repair adds **(c) a
month/phase-based shift** on top of (a), with no new variants:

- `SeasonalColour.dormant(averageTemperature, averageRainfall, rainfallVariance, northern, fractionOfYear)` ports the
  autumn and winter window of TFC's leaves (`TFCColors.getSeasonalFoliageColor`): winter-controlled climates (average at
  or below 12 C, southern hemisphere half a year off), dry-season climates (above 12.5 C, variance beyond 0.4, minimum
  rain at most 120 mm), evergreen otherwise. TFC's per-block hash jitter is left out (the phase is a whole month).
- In that window a non-freezing band shows `SeasonalColour.dormantBand`: the same temperature band in the **driest**
  rainfall band, the straw yellow of TFC's grass colormap (hue about 45 degrees in every non-freezing temperature band).
  A first try with "one rainfall band drier" failed in game: the fresh world's (-768, 768) has a dry June (`t1r2`,
  already yellow) and a wet October (about 270 mm, `r0`), so October still came out greener than June. The driest band
  does not depend on the groundwater swing.
- Freezing bands (snow) and evergreen climates keep TFC's plain colormap colour.

| Option | Cost | Result |
|---|---|---|
| (a) more temperature bands (kept) | 16 to 28 variants per biome | June and October differ, but October is greener (TFC's grass colormap) |
| (b) grass-only colour view | per-block tint the vanilla client does not have | not cheaper |
| (c) month/phase-based shift (added) | 0 variants; one pure check per chunk column in the async remap | October in the straw band wherever TFC's leaves turn |

## Budget

| | Before PAR-CLIM-05 | Now | Limit |
|---|---|---|---|
| Variants per TFC biome | 16 | 28 | about 32 (2x) |
| Seasonal variant biomes | 2,000 | 3,500 (1.75x) | about 4,000 |
| Client biome registry (with vanilla's) | about 2,190 | about 3,690 | 4,096 (12-bit global palette) |
| Chunk remap | async on the CPU pool | async; plus `SeasonalColour.dormant` once per 4x4 column (16 per chunk, pure float math) | must stay async |

## In-game check

Steps: `gamerule advance_weather false`, `weather clear`, test room (`spikes/client/testroom.sh`, barrier roof, noon,
same camera) at the item's site (-768, 64, 768) and at a second, wetter site (1500, 70, 0); June shot; `tfp time add
months 4`; wait 15 s for the resend (`/tfp debug packets`: `phase=OCTOBER`, resent chunks); October shot from the same
spot. Both months at noon, clear sky (testroom.sh sets noon, which advances the calendar by one day, so the shots are
June 4/6 and October 7/8, 1003). Measured: mean RGB of the floor strip x 0-360, y 580-650 of the 1280x720 frame.

| Site | June | October | Direction |
|---|---|---|---|
| A (-768, 768), dry June | (141, 122, 51), hue 47.6, R/G 1.15 | (141, 118, 51), hue 45.1, R/G 1.19 | slightly warmer/yellower (June already near the straw band: dry summer, `t1r2`) |
| B (1500, 0), wet | (49, 87, 47), hue 116.8, R/G 0.56 | (146, 123, 52), hue 45.1, R/G 1.19 | clearly warmer/yellower: green to straw |

Evidence: `site-a-june-vs-october.png`, `site-b-june-vs-october.png` (HUD date strip + floor, June left, October
right). Full frames as lossless WebP were handed to the user outside the repo.

### Re-run of the item's own steps (second repair round, HEAD a840cd9)

The verify stage's run produced only dark frames (stars, night light) at the site: its shots were taken on bare terrain
(a snow-covered slope) before the client had the day time, so nothing could be judged. Re-run here on a fresh world
(Paper 1.21.11, client 1.21.11 under Xvfb, pack applied 3 s after the join), exactly the item's steps:
`gamerule advance_weather false`, `weather clear`, `spikes/client/testroom.sh` at (-768, 64, 768) (the world starts on
June 1, so no month step was needed), `tfp time set noon` (June 1, 1000 12:00, vanilla day time 6001), wait 20 s for
the chunks to render, shot; `tfp time add months 4`, `tfp time set noon` (October 2, 1000 12:00, day time 6001),
`weather clear`, 15 s wait for the resend, same camera, shot. `tfp debug chunk -768 768`: June 15.2 C / 205.8 mm,
October 6.5 C / 249.9 mm. Both frames are full daylight (blue sky, no stars). October was also shot after a rejoin at
GUI scale 2 and 3 (`MC_OPTIONS="guiScale:2"`, `"guiScale:3"`): same floor colour, HUD date strip scales correctly.

| Shot | Floor strip mean RGB | Hue | R/G |
|---|---|---|---|
| June 1, noon | (82, 100, 48) | 81.0 | 0.82 |
| October 2, noon | (141, 118, 51) | 45.1 | 1.19 |
| October, GUI scale 2 | (140, 118, 51) | 45.1 | 1.19 |
| October, GUI scale 3 | (141, 118, 51) | 45.1 | 1.19 |

Evidence: `rerun-june-vs-october.png` (left half of each 1280x720 frame, June left, October right). Full frames as
lossless WebP were handed to the user outside the repo. Result: green in June, straw yellow in October, at the same
site, camera and light: **PASS**.

## Verdict

- Controlled screenshots at the same site and light, June vs October, October warmer/yellower: **PASS** at both sites
  (lower hue, higher R/G in October; site B green to straw, site A from yellow-olive to straw).
- The colour change follows the chunk resend at the month change (phase OCTOBER, resent chunks), so the remap works.

## Tests

- `core/.../SeasonalColourTest.java`: 15 (band edges, 0 C edge, dormant band, TFC's leaf window by hand-worked golden
  values in both hemispheres and the dry-season branch, October yellower than June at a 186 mm site and at a dry-summer
  site).
- `paper/.../SeasonalBiomeRemapTest.java`: 15 (October in the driest band at a temperate site, November dormant at the
  dry-autumn chunk, the dormant window follows the hemisphere and skips evergreen climates, the remap equals the band of
  the sampled climate with the dormant flag).
- `./gradlew check` green.
