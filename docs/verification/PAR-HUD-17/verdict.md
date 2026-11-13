# PAR-HUD-17: HUD display styles follow hud.* after /tfp reload: verdict: pass

- Date: 2026-11-13. Tree `776ad52` (the PAR-HUD-17 code commit `532b5b1` and later items), jar from `:dist:shadowJar`
  with the shared generated content (`tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start),
  CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker). Log: no error from our plugin
  (only the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).

## Steps

RCON: `forceload add -16 -16 16 16`, `weather clear`, `tfp time set noon`, stone floor at y 99, `tp Bot 0 100 0 0 30`,
`gamemode survival Bot`, `difficulty easy`, `damage Bot 3` (so the health text is not full). Per scale: frame with the
defaults (TFC, CELSIUS); edit `plugins/TerraFirmaPlugin/config.yml` to `health-display-style: VANILLA`,
`climate-temperature-style: FAHRENHEIT`, `tfp reload config`, frame; back to TFC, CELSIUS, `tfp reload config`, frame.
At GUI 2 also VANILLA_CURRENT + KELVIN and TFC_CURRENT + RANKINE. Health regenerates between frames (natural regen), so
the numbers differ slightly from frame to frame.

## Crops (top-left plate, then the bars above the hotbar)

| file | style | plate | health text |
|---|---|---|---|
| `1-defaults-g2.png` | TFC, CELSIUS | 6.8°C | 843/850 |
| `2-vanilla-fahrenheit-g2.png` | VANILLA, FAHRENHEIT | 44.3°F | 17.0/17.0 |
| `3-defaults-again-g2.png` | TFC, CELSIUS | 6.9°C | 730/850 |
| `4-vanilla-current-kelvin-g2.png` | VANILLA_CURRENT, KELVIN | 280.0 K | 13.6 |
| `5-tfc-current-rankine-g2.png` | TFC_CURRENT, RANKINE | 504.0°R | 681 |
| `6-defaults-g3.png` | TFC, CELSIUS | 6.9°C | 557/826 |
| `7-vanilla-fahrenheit-g3.png` | VANILLA, FAHRENHEIT | 44.5°F | 11.1/16.5 |
| `8-defaults-again-g3.png` | TFC, CELSIUS | 6.9°C | 557/826 |

## Checks

| criterion | GUI 2 | GUI 3 |
|---|---|---|
| `health-display-style` changes the health text after `/tfp reload config` (VANILLA `17.0/17.0`, `11.1/16.5` = 557/826 / 50; VANILLA_CURRENT `13.6`; TFC_CURRENT `681`) | pass | pass |
| `climate-temperature-style` changes the plate after `/tfp reload config` (6.9 C -> 44.5°F, 280.0 K, 504.0°R; unit symbols as TFC) | pass | pass |
| Defaults unchanged: back to TFC, CELSIUS shows `NNN/MMM` and the Celsius plate as before (frames 3 and 8 match 1 and 6) | pass | pass |
| Text stays inside the bar and the plate for every style | pass | pass |
