# PAR-CLIM-02 in-game check

Status: **not passed, re-run required** with the item's current `in_game_steps`. The earlier pass is withdrawn.

## Run 2 (commit 1942d21, 2026-11-13): fail

- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`), CraftEngine, BetterModel, BetterHud, PacketEvents as in
  `docs/testing/headless.md`. Client: vanilla 1.21.11 under Xvfb.
- Cold site (HUD: January 2, 1001, Winter, -5.6 C): the client draws rain (expected under NOT-PARITY NP-CLIM-02), and
  the snow layers on the ground were already there under a clear sky, so they are world-generation snow, not snow built
  from the SNOW decision.
- Warm site (20.6 C): rain over bare ground. Correct.
- `tfp debug chunk <x> <z>` over RCON returned nothing for non-zero coordinates, and the client typing coordinates got
  `Incorrect argument`. Without the per-position readout there is no evidence of the server's SNOW decision at the cold
  site, and it is not known whether the capture was inside a climate rain window.

| Criterion | Result |
|---|---|
| Server: SNOW at a cold January site | Not shown: no readout, ground snow from world generation |
| Server: RAIN at a warm site | Rain shown; no readout |
| Client falling particles show snow at the cold site | Not reachable, accepted substitute: NP-CLIM-02, PAR-CLIM-05 |

Fixes for run 3:
- The readout is taken from Bot's own `/tfp debug chunk` (no coordinates, Bot's position, through
  `PrecipitationService.readNow` with vanilla's rain gates), typed in the client, and read from the `[CHAT]` lines of the
  client log. The console readout over RCON is follow-up PAR-CLIM-17 (outside this item's files).
- The cold site gets a freshly cleared 9x9 patch (`fill ... air replace snow`), captured before and after six in-game
  hours away (chunk unloaded, `SnowService` catch-up on reload). Snow on the cleared patch can only come from the SNOW
  decision.

## Run 1 (working tree of e665d20): withdrawn

It reported SNOW at -600 0 (-2.7 C) and RAIN at 0 9000 (30.0 C) from `/tfp debug chunk`, with the screenshots
`cold-site-snow.png` and `warm-site-rain.png`. Run 2 could not reproduce the readout, and nothing in run 1 separates snow
built by `SnowService` from world-generation snow, so it does not count toward acceptance.

## Notes for screenshot reviewers

- **Rain particles at a sub-zero site are expected until PAR-CLIM-05.** The seasonal variant biomes keep the TFC base
  biome's climate (temperature 0.5, precipitation on; see `SeasonalVariantRainTest`), so the vanilla client always draws
  rain there, at any server temperature. That is the substitute in NOT-PARITY NP-CLIM-02 and the ledger Divergences row
  "The client's snow or rain follows the biome temperature ..." (revisit in PAR-CLIM-05). The cold-site check for this
  item is the readout `precipitation here: SNOW` plus snow built on the cleared patch.
- **`precipitation here: NONE` under `/weather rain` is TFC-correct.** TFC's `WeatherHelpers.getPrecipitationAt` reads
  only the climate's rain at the calendar time (`getRain`), the instant temperature and the rainfall, never the vanilla
  rain flag, and TFC's weather cycle overrides `/weather rain`. Outside a climate rain window the decision is NONE while
  the vanilla storm still draws. Coupling vanilla weather to the climate is PAR-CLIM-12. Step the calendar an hour at a
  time until the readout shows SNOW.
