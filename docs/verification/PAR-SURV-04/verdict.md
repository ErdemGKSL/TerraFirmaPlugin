# PAR-SURV-04: Thirst completion: verdict: pass

- Date: 2026-11-06. Tree `820c7b9` plus this item's change, jar from `:dist:shadowJar` with the shared generated content
  (`tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start), CraftEngine
  26.9.2, BetterHud; feature modules log ends with `thirst`. Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, GUI scale 2); the pack applied 3 s after the join.
- Run and judged by the main session (no subagent tool was available to this worker). Log: no error from our plugin (only the
  EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).

## Steps (RCON)

`forceload add -16 -16 16 16`, stone floor at y 99, `weather clear`, `tfp time set noon` (June 1, ambient 5.0 C),
`difficulty normal`, join, `gamemode survival Bot`, `tp Bot 0 100 0 0 30`, health restored to 20.

1. `tfp debug thirst Bot 5`, wait 10 s, `data get entity Bot active_effects`.
2. `tfp debug thirst Bot 0`, `data get entity Bot Health` (20.0), wait 22 s, health again.
3. `tfp debug thirst Bot 50`, `weather rain`, `tp Bot 0 100 0 0 -90` (looking up), wait 10 s, `tfp debug player Bot`.
4. Controls: thirst 50 looking level in the rain for 6 s; thirst 50 looking up in clear weather for 6 s.

## Results

| criterion | observed | verdict |
|---|---|---|
| Thirst 5 shows slowness | `minecraft:slowness` and `minecraft:mining_fatigue`, amplifier 1, duration up to 160, `show_particles: 0b`, `show_icon: 0b` (TFC's `new MobEffectInstance(effect, 160, 1, false, false)` has no icon, so the screenshot shows no icon by design; `thirst5-hud-g2.png`) | pass |
| Periodic damage at 0 | health 20.0 then 15.0 22 s later (one point per 100 ticks); HUD 619/826 with an empty thirst bar (`thirst0-hud-g2.png`) | pass |
| Rain drinking raises thirst | 50.0 to 91.67 in about 10 s (10 readings x 20 ticks x 5/24 = 4.17 each), thirst bar near full in the rain (`rainup-hud-g2.png`, `rainup-g2-small.png`) | pass |
| Controls | looking level in rain: 50.0 stays 50.0; looking up in clear weather: 50.0 stays 50.0 | pass |

Only GUI scale 2 was captured: this item adds no HUD element (the thirst bar is PAR-HUD's, verified at both scales there).

## Crops

| file | content |
|---|---|
| `thirst5-hud-g2.png` | bars at thirst 5, health full |
| `thirst0-hud-g2.png` | thirst empty, health 619/826 after the dehydration hits |
| `rainup-hud-g2.png` | bars after 10 s of looking up in the rain: thirst near full |
| `rainup-g2-small.png` | the whole screen (reduced) looking up into the rain |
