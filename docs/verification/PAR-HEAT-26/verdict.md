# PAR-HEAT-26 in-game check: pending (verify stage)

Date: 2026-11-18. The worker that wired the candles had no agent tool to drive the headless Paper 1.21.11 server and
client, so the item's `in_game_steps` were not run. `./gradlew check` is green on the commit that marks the item done.

What the verify run must show (screenshots and F3 crops under `docs/verification/PAR-HEAT-11/`, GUI scale 2 and 3):

| Criterion | Expected |
|---|---|
| CraftEngine loads `configuration/light/candle/candle.yml` | no "Found N issue(s)" for `tfp:candle*`, `tfp:candle_cake*`, `tfp:jack_o_lantern` |
| Stacking | clicking a placed `tfp:candle` with `tfp:candle` adds one (4 candles, vanilla four-candle look) |
| Lit light | F3 block light at the four candles 14 and at the red one 5 (TFC `3n+2`); if the client reads 12 / 3 (vanilla's `3n` of the carrier), record it under the ledger divergence "TFC's lit candles emit 3 * candles + 2" |
| Extinguish | empty-hand click puts the lit red candle out with vanilla's extinguish sound and smoke |
| Water | a water bucket and a dispenser put a lit candle out at once; a waterlogged candle does not light; the CraftEngine waterlogging fires `PlayerBucketEmptyEvent` / `BlockDispenseEvent` (else note that the candle goes out at its next trigger) |
| Burnout | after `tfp time add days 13` and a chunk reload both candles are unlit, still four candles |
| Jack-o'-lantern | lighting a carved pumpkin gives `tfp:jack_o_lantern` with the same facing (light 15); after `tfp time add days 5` and a chunk reload it is a carved pumpkin again |
