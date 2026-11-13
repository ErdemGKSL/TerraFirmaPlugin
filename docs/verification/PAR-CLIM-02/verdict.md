# PAR-CLIM-02 in-game check

- Date: 2026-11-13
- Tree: base 859c5ed plus the PAR-CLIM-02 change (the jar was built from the working tree of the item commit)
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel, BetterHud,
  PacketEvents as in `docs/testing/headless.md`
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), GUI scale 2, pack applied

## Steps run (RCON)

1. `tfp time set hour 12` (June 1, 1000), `tfp time add months 7` (January 1, 1001 12:00).
2. `tfp time add hours 1` until `/tfp debug chunk` reported precipitation at a cold column: 27 hours, January 2 15:00
   (calendar tick 2,343,000), when the climate's rain intensity covers both sites.
3. `weather rain 1000000` (so the vanilla client draws a storm).
4. Cold site: `tp Bot -600 71 0` (land, -2.7 C, rainfall 290.5 mm). Warm site: `tp Bot 0 74 9000` (30.0 C,
   rainfall 482.0 mm; creative for the teleport so the far chunk loads, then survival). Screenshot at each.

`/tfp debug chunk` at the two columns at the time of the screenshots:

```
position -600 0, y 71 | climate now: temperature -2.7°C, average 2.3°C, rainfall 290.5 mm (calendar tick 2345066)
precipitation here: SNOW | snow rule: event ACCUMULATE, would accumulate: yes
position 0 9000, y 72 | climate now: temperature 30.0°C, average 27.1°C, rainfall 482.0 mm (calendar tick 2345816)
precipitation here: RAIN | snow rule: event MELT, would accumulate: no
```

## Evidence

- `cold-site-snow.png`, `cold-site-snow-hud-2x.png`: January 2, -2.7 C on the HUD; snow layers lie on the ground around
  the player (server snow from the SNOW decision, `SnowService`). The falling particles are rain.
- `warm-site-rain.png`, `warm-site-rain-hud-2x.png`: January 2, 30.0 C on the HUD; rain falls, no snow on the ground.

## Verdict

| Criterion | Result |
|---|---|
| Server: snow at a cold January site | Pass: SNOW decision, snow accumulating on the ground at -2.7 C |
| Server: rain at a warm site | Pass: RAIN decision, no snow at 30.0 C |
| Client falling particles show snow at the cold site | Not reached, accepted substitute: the client draws rain there because its particles follow the seasonal variant biome's fixed temperature (NOT-PARITY NP-CLIM-02; the 0 C band edge is PAR-CLIM-05) |

Pass, with the client-particle difference recorded under NP-CLIM-02.

## Notes for screenshot reviewers

- **Rain particles at a sub-zero site are expected until PAR-CLIM-05.** The seasonal variant biomes keep the TFC base
  biome's climate (temperature 0.5, precipitation on; see `SeasonalVariantRainTest`), so the vanilla client always draws
  rain there, at any server temperature. That is the substitute in NOT-PARITY NP-CLIM-02 and the ledger Divergences row
  "The client's snow or rain follows the biome temperature ..." (open in PAR-CLIM-05), not a defect of this item. The
  check for this item is the server decision (`precipitation here: SNOW`) and the snow layers it builds.
- **`precipitation here: NONE` under `/weather rain` is TFC-correct.** TFC's `WeatherHelpers.getPrecipitationAt` reads
  only the climate's rain at the calendar time (`getRain`), the instant temperature and the rainfall, never the vanilla
  rain flag, and TFC's weather cycle overrides `/weather rain`. Outside a climate rain window the decision is NONE while
  the vanilla storm still draws. Coupling vanilla weather to the climate is PAR-CLIM-12 (ledger row starting 'TFC's "raining
  at" is vanilla `isRainingAt` ...'). To see SNOW or RAIN, step the calendar into a rain window as in step 2 above.
- A re-capture should use a January date (step 1), a climate rain tick (step 2), and face into the test area so the
  ground under the rain is in frame.
