# PAR-HUD-06: HUD polish (frame symmetry, thermometer, info plate, pulse): verdict: pass, with one follow-up

- Date: 2026-11-05. Tree `b855a39` (no HUD input changed by this item), jar from `:dist:shadowJar` with freshly generated
  content.
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start), CraftEngine
  26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Judged by the main session from the crops below and from rows and columns measured on the raw 1280x720 shots. The
  project owner's acceptance of these crops is the item's done criterion and is still to be given.

## Steps (RCON)

`forceload add -16 -16 16 16`, stone floor at y 99, `tp Bot 0 100 0 0 30`, `gamemode survival Bot`, `weather clear`,
`tfp time set noon`, `tfp debug thirst Bot 100`, `effect give Bot minecraft:instant_health 1 5`, wait 6 s, screenshot.
June shots on June 1 and 2 (8.7 °C and 5.9 °C at the spot); December shots after `tfp time add months 6` (-11.5, -9.8 °C).
`tfp debug chunk 0 0` printed the temperature at each shot.

## Crops

| file | scale | content |
|---|---|---|
| `gui2-june-topleft-4x.png`, `gui2-december-topleft-4x.png` | 2 | plate, date, thermometer, temperature |
| `gui2-december-hotbar-3x.png` | 2 | bars over the hotbar |
| `gui2-health-left-end-8x.png`, `gui2-right-column-right-end-8x.png` | 2 | outer frame columns at both ends |
| `gui3-june-topleft-3x.png`, `gui3-december-topleft-3x.png` | 3 | plate, date, thermometer, temperature |
| `gui3-december-hotbar-2x.png` | 3 | bars over the hotbar |

## Checks

| check | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| Frame symmetry: the 4.5 px asymmetry of 2026-10-19 (`plans/gaps.md` M3-SURV-06) came from the first, ornate art. The current masters are mirror-symmetric (alpha mask equals its mirror for every bar master, background and fill), and re-deriving every bar with `tools/images/hud_layers.py` reproduces the shipped layers bit for bit. In game the outer frames span health -91..-11 and the right column +10..+90 around the screen centre (GUI 2: screen 458..821, midpoint 639.5; GUI 3: 366..911, midpoint 638.5), exactly the hotbar's extent and centre | pass | pass | hotbar crops, 8x end crops, raw shots |
| Thermometer fill `(t + 20) / 60` of the 62 interior columns (`HudValues.thermometer`, BetterHud split): 8.7 °C gives 30 (29.7 exact), 5.9 °C 26 (26.8), -11.5 °C 9 (8.8), -9.8 °C 11 (10.5); within one column of the exact value, the bar fill rounding already in the ledger | pass | pass | top-left crops, column scans |
| Plate behind the date and temperature lines in the top-left; thermometer and temperature text on the plate | pass | pass | June crops |
| The date line stays on the plate in every month | **fail** | **fail** | December crops: "December 6, 1000  (Winter)" (134 GUI px) runs about 10 px past the 124 px plate; "September 30, 1000  (Autumn)" would be 148 px |
| The plate is fully on screen | pass | minor | at GUI 3 the info HUD sits 5/3 GUI px left of its layout x, so the plate's left frame column is 1 screen px wide |

## Outcome

The overflow is fixed by a wider plate, but a wider plate alone does not work: a 158x22 plate (re-postprocessed from the
same raw) moved the plate about 17 GUI px right and the date and thermometer about 17 px left in game, because BetterHud
re-centres the info line by the image width. The compensation belongs to `BetterHudPack`/`HudLayouts`, outside this
item's files, so the plate stays 124x22 here and the fix is follow-up PAR-HUD-13 (with the GUI 3 left-edge shift).

Pulse animation: not built. TFC does not flash or pulse any bar at low values (`IngameOverlays` draws health, food and
thirst with a fixed alpha; the only alpha effect is the overheat tint on the thirst bar, already a ledger divergence), so
no pulse is the parity choice. The owner can ask for one as a new item.

Side observation (not this item's scope): after `tfp time set noon` moved a day forward, a `tfp debug thirst Bot 100`
sent right after read back as 50 (`tfp debug player Bot`) about 20 s later, as if the skipped day's drain was applied after the set.
