# Visual checklist: precipitation per position (PAR-CLIM-02)

How to check by eye that the server decides snow or rain per position as TFC does. Steps and commands are the item's
`in_game_steps` (`plans/parity/items/CLIMATE/PAR-CLIM-02.md`); the last run is `docs/verification/PAR-CLIM-02/verdict.md`.

## Setup

- [ ] Fresh TFC world with a fixed seed (`level-seed=1234` reproduces the sites below), headless client with the pack.
- [ ] `tfp time add months 7` shows January in the HUD date line.
- [ ] A climate rain window is open: with seed 1234, `tfp time add hours 109` after the month step (January 5, 19:00).
  For another seed, step one hour at a time until a readout leaves NONE.

## Cold site (sub-zero)

- [ ] HUD temperature below 0 C (seed 1234: 8 125 -1976, about -18 C).
- [ ] Bot's `/tfp debug chunk` prints `precipitation here: SNOW`.
- [ ] Outside the window (January 1) the same site prints `NONE` under the same `/weather rain` storm.
- [ ] Cleared 9x9 patch, Bot away for 6 in-game hours (chunk unloaded), back: new snow layers on the patch, same camera.
- [ ] Falling particles are rain, not snowflakes: expected (NOT-PARITY NP-CLIM-02, revisit in PAR-CLIM-05). Do not fail
  the check for it.

## Warm site

- [ ] HUD temperature above 0 C on land under the open sky (seed 1234: -256 67 9600, about 30 C; 0 74 9000 is ocean).
- [ ] Bot's `/tfp debug chunk` prints `precipitation here: RAIN` in the same window.
- [ ] Under water, under leaves or a roof the readout is `NONE` (vanilla's `isRainingAt` gates).

## Always

- [ ] The HUD date, temperature and bars are readable, with no missing-texture boxes.
