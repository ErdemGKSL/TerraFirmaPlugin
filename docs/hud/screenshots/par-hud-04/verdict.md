# PAR-HUD-04: two-layer HUD at GUI scale 2 and 3 — verdict: pass

- Date: 2026-10-22. Verified on tree `241994c` (PAR-INFRA-08); HUD inputs (HUD datagen, generated BetterHud files,
  `integration/betterhud`) unchanged up to `d4ae342`. Jar from `:dist:shadowJar` with the shared datagen output. Reproduced
  on `06ed6ce` (see "Reproduction run" at the end).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents
  2.14.0, `TFP_WORLD=1`. Client: vanilla 1.21.11 under Xvfb 1280x720 (`spikes/client/session.sh`, `guiScale:2`, then a
  relaunch with `guiScale:3`); the CraftEngine pack applied 3 s after each join.
- Site: `spikes/client/testroom.sh <rcon> 96 87 -2063`, then survival, difficulty easy (peaceful refills health and food
  and hides the low state).
- Judged by the main session from the crops below; pixel rows and columns also measured on the raw 1280x720 shots
  (pure-black frame rows, screen centre 640, bottom 720).

## Steps (RCON)

| state | commands |
|---|---|
| full | `gamemode survival Bot`, `effect clear Bot`, `effect give Bot minecraft:saturation 5 10` (scale 3 also `instant_health`), `tfp debug thirst Bot 100` (scale 3 also `tfp debug hunger Bot 20`) |
| low | `effect clear Bot`, `damage Bot 16`, `tfp debug thirst Bot 10`, `tfp debug hunger Bot 4` (gave 144/720, thirst 10, food 4) |
| empty channel | `tfp debug thirst Bot 0`, `tfp debug hunger Bot 0` (scale 2) |
| submerged | `execute at Bot run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:water`, shot after 5 s (Air 180/179), again after 13 s (Air 0/4), then the water removed and a shot after 6 s (Air 300) |

## Crops

| file | scale | state |
|---|---|---|
| `gui2-full-hotbar-4x.png`, `gui2-full-left-end-8x.png`, `gui2-full-right-end-8x.png`, `gui2-topleft-4x.png` | 2 | full |
| `gui2-low-hotbar-4x.png` | 2 | low |
| `gui2-empty-thirst-8x.png` | 2 | thirst 0 % |
| `gui2-submerged-hotbar-4x.png`, `gui2-submerged-red-hotbar-4x.png` | 2 | submerged (blue, red) |
| `gui3-full-hotbar-3x.png`, `gui3-full-left-end-8x.png`, `gui3-full-right-end-8x.png`, `gui3-topleft-3x.png` | 3 | full |
| `gui3-low-hotbar-3x.png`, `gui3-low-health-8x.png` | 3 | low |
| `gui3-submerged-hotbar-3x.png`, `gui3-submerged-red-hotbar-3x.png`, `gui3-after-water-hotbar-3x.png` | 3 | submerged, back on land |

## Checks

| check | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| The fill never covers the frame: the black frame rows and end columns are intact at 100 % and at the fill edge | pass | pass | `*-left-end-8x`, `*-right-end-8x`, `gui3-low-health-8x` |
| The empty channel shows at 0 % (no fill sliver, dark channel inside the full frame) and behind a partial fill | pass | pass | `gui2-empty-thirst-8x`, `*-low-hotbar-*` |
| Fill follows the value. TFC fills `(int)(81 x pct)` px of its 81-px strip; ours shows `floor((round(78 pct) + 1) x 81 / 79) - 1` of 79 interior columns (BetterHud `listener` + `split: 79`, rounding recorded in the ledger Divergences). Health 144/720 = 20 %: 16 columns (TFC 16 px); thirst 10 %: 8 columns (TFC 8 px); food 4/20 = 20 %: 16 columns (TFC 16 px); 0 %: none (TFC 0) | pass | pass | `gui3-low-health-8x`, `*-low-hotbar-*`, rerun column counts below |
| Layer order 1/2/3: fill in front of the background, the health text in front of the fill (the "1" of 144 sits over the red stub) | pass | pass | `*-low-hotbar-*` |
| Vertical trim compensation: frames on the planned rows, identical at both scales: health H-40..H-31, hunger H-37..H-31, thirst H-45..H-39, air H-53..H-47; one empty row between right-column bars; nothing on the XP row (H-29) | pass | pass | measured on the raw shots |
| Air bar only below full air, directly above thirst, blue at 180, red below 60, gone again at 300 | pass | pass | `*-submerged-*`, `gui2-full-hotbar-4x`, `gui3-after-water-hotbar-3x` |
| `offset: left` puts x at the canvas left edge: the frame's top row (canvas x 1..79) spans screen centre-90..-11 (health) and +11..+90 (right column) at both scales, i.e. layout x + 1 + `SCREEN_SHIFT_X` (-1) | pass | pass | measured on the raw shots |
| Health text inside its bar; date and temperature text on the top-left plate; thermometer fill with the empty tube visible | pass | pass | `*-hotbar-*`, `*-topleft-*` |

No layer was off, so the plan §1 fallback (split = interior width or sequence frames) was not needed and `HudLayouts.java`
is unchanged. Not part of this check: the armour bar (PAR-HUD-05), the held-temperature popup (PAR-HUD-08).

## Reproduction run (review rerun, `06ed6ce`)

An independent capture of the same states, with the harness faults of the first rerun fixed (scratchpad runner, not in the
repo):

- `server.properties`: `allow-flight=true`, `difficulty=easy`; `TFP_WORLD=1`. First boot, clean stop, world deleted, warm
  restart: the first boot logs the expected `degraded mode` line (CraftEngine `delay-configuration-load` edit), the second
  boot's `latest.log` has none. No "floating too long" / "Flying is not enabled" line in the server log.
- Room: `forceload add -16 -16 16 16`, the fill retried until the area was loaded, a 13x13 `snow_block` floor at y=100 on dirt,
  two light blocks, noon, clear; `tp Bot 0 101 0 0 30`, then survival. `data get entity Bot Pos` replied `[0.5d, 101.0d, 0.5d]`.
- A pixel probe (no Mojang-red splash, the health frame's top row dark across the bar) passed before the first shot at both
  scales; every shot passed it too.
- RCON replies, logged by the runner: `tfp debug thirst` replied `thirst set to 100.0 / 10.0 / 0.0` every time;
  `tfp debug hunger` replied `Food level of Bot set to N.` (some replies came back empty over RCON; `data get entity Bot
  foodLevel` confirmed 20, 4 and 0 each time). Health after `damage Bot 16`: `4.0f`. Air: 195 after 5 s, -2 / -16 after
  13 s, 300 after the water was removed.

Measured on the raw 1280x720 shots (GUI pixels, rows as H-n, columns from the screen centre), identical at GUI 2 and 3:

| state | health | thirst | hunger | air |
|---|---|---|---|---|
| full | frame H-40..H-31, 79/79 columns (-90..-12), text 850/850 | frame H-45..H-39, 79/79 (+11..+89) | frame H-37..H-31, 79/79 | absent |
| low (144/720, thirst 10, food 4) | 16 columns (-90..-75) | 8 columns | 16 columns | absent |
| empty (thirst 0, food 0) | 16 columns | 0 columns, frame and end columns intact | 0 columns, frame intact | absent |
| submerged 5 s | 79 | 79 | 79 | frame H-53..H-47, light blue, 52 (GUI 2) / 56 (GUI 3) columns |
| submerged 13 s | 79 | 79 | 79 | red, 5 / 3 columns |
| back on land | 79 | 79 | 79 | absent |

The end frame columns (-91, -11, +10, +90) are dark on every interior row in every state; the XP row (H-29) holds only
the vanilla XP bar. This agrees with every check above. At GUI 3 in the full state a vanilla chat line ("Your game mode
has been updated...") was still on screen across the right column; the thirst bar was measured from the later states.
