# PAR-HUD-19: health and thirst bar states: verdict: pass

- Date: 2026-11-13. Tree `7cea677` plus the PAR-HUD-19 change (the commit that adds this file), jar from `:dist:shadowJar`
  with the generated content of the same tree (`tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start),
  CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker). Log: no error from our plugin
  (only the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub). BetterHud loaded the layouts with the
  new placeholders (`registered 12 number placeholder(s) ... 8 listener(s)`), no unknown-placeholder warning.

## Steps

RCON: `forceload add -16 -16 16 16`, `weather clear`, `tfp time set noon`, stone floor at y 99, `tp Bot 0 100 0 0 30`,
`difficulty easy`. Per scale (`step.sh`): `gamemode survival Bot`, `effect clear Bot`, `tfp debug hunger Bot 20`,
`effect give Bot minecraft:saturation 1 5 true`; `damage Bot 6`, frame after 0.3 s (1) and after 3 s more (2);
`effect give Bot minecraft:absorption 60 1`, frame (3); `effect clear`, `instant_health`, `effect give Bot
minecraft:regeneration 30 0` at full health, frame (4); `gamemode creative` with absorption and regeneration, frame (5);
`gamemode spectator`, frame (6). Overheat: no column above 30 °C was found near spawn in a few probes (`tfp debug chunk`;
the hottest band of this seed is z = 10000, 26-27 °C at noon in July), so Bot stood on a stone pad at 6000, 67, 10000
(26.4 °C on the HUD plate) with thirst at 70: TFC's alpha `(26.4 - 22) / 12 = 0.37`, published as level 1 of 4, frame (7).
At GUI 3 the step frames were taken at the same hot spot, so the thirst ring is tinted in them too.

## Crops (bars above the hotbar; 7-plate = top-left plate)

| file | state |
|---|---|
| `1-hurt-0s-g2/3.png` | 550/850 right after `damage Bot 6`: white highlight ring |
| `2-hurt-3s-g2/3.png` | 553/850 three seconds later, hurt with saturation: ring stays |
| `3-absorption-g2/3.png` | Absorption II (8 HP = 340 shown) on 554: text `894/850`, fill full, yellow surplus over 40 % (`clamp(1 + 8/20 - 1)` = 0.4) |
| `3b-absorption-hurt-saturation-g2.png` | a repeat at 13 HP with saturation 17 and 8 absorption (checked with `data get entity Bot ...`): ring and yellow surplus together |
| `4-regen-full-g2/3.png` | 850/850 under Regeneration: ring |
| `5-creative-g2/3.png`, `6-spectator-g2/3.png` | bars, ring and surplus hidden |
| `7-overheat-g2/3.png`, `7-plate-g2/3.png` | thirst frame `#210000` (TFC `#820000` at alpha 1/4 over black) beside hunger's black frame, 26.4 °C |

## Checks

| criterion | GUI 2 | GUI 3 |
|---|---|---|
| Hurt glow appears within 1 s of a hit and stays while hurt with saturation > 0 | pass | pass |
| Regeneration at full health glows | pass | pass |
| Absorption is in the fill and the text; the yellow surplus bar fills by TFC's formula (40 % for 8 HP over a full bar) | pass | pass |
| Thirst overheat tint shows at a temperature above 22 °C (stepped level 1 at 26.4 °C; pixel `(33, 0, 0)` measured on the thirst frame) | pass | pass |
| All of it hidden in creative and spectator | pass | pass |
| Ring and surplus stay on the health bar's own pixels (no overlap with armour, mount or air rows) | pass | pass |

Ring measured on the raw GUI 2 shots (white pixels in the health bar area, text included): 204 before the hit (text
only), 528 in frames 1, 2, 3, 3b and 4 (text plus the 324-pixel ring at 2x). Frame 3 is hurt with saturation as well, so
the ring shows there together with the yellow surplus.
