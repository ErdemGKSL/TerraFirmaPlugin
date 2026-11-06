# PAR-SURV-04: Thirst completion: verdict: pass (repair round)

## Repair round (after the independent REJECT of `4e6e960`)

The independent check (plan steps, no `tfp time set`) saw rain on screen while looking up and thirst stayed 5.0. The
first pass gated rain drinking on the TFC ambient temperature (above 0 C), but the vanilla client draws the vanilla storm
through the biome it is sent, whatever the TFC climate says; the first pass only worked because it was set up at a warm
spot (5.0 C). The repair gates on the rain the client draws (storm, open sky, the client's biome precipitation and
temperature; ledger row "raining at"), and `/tfp debug rain [name]` prints every gate.

- Server: Paper 1.21.11, `TFP_WORLD=1`, priming start, world deleted, second start; jar from `:dist:shadowJar` of the repair
  tree. Client: vanilla 1.21.11, Xvfb 1280x720, GUI scale 2. Calendar at its default (June 1, 1000), no time set.
- Steps (RCON): `difficulty normal`, `gamemode survival Bot`, `forceload add -16 -16 16 16`, stone floor at y 99 with air
  above, `tp Bot 0 100 0 0 30`, `tfp debug thirst Bot 5`, `weather rain`, `tp Bot 0 100 0 0 -85`, `tfp debug rain Bot`, then
  `tfp debug player Bot` every 2 s.

| case | gates (`tfp debug rain Bot`) | thirst over time | verdict |
|---|---|---|---|
| spawn (0, 100, 0), TFC ambient 8.0 C | pitch -85.0, storm true, open to sky true, client biome seasonal variant (precipitation true, 0.464), drinks rain true | 5.0, 13.3 (2 s), 21.7, 30.0, 38.3 (8 s), 46.7 (10 s) | pass |
| cold place (0, 200, -9000), TFC ambient -19.8 C: the case the first pass got wrong (the old gate said snow while the client drew rain) | same gates, client biome 0.339 at y 201, drinks rain true | 5.0, 13.3 (2 s), 21.7, 30.0, 34.2 (8 s; one reading fell outside the window) | pass |

`lastDrinkTick` stays `never` while drinking rain, as in TFC: `onPlayerTick` calls `addThirst`, which does not touch the
drink cooldown (only `onDrink` does). Not a failure.

| file | content |
|---|---|
| `repair-rain-t0.png`, `repair-rain-t10.png` | looking up in the rain at spawn, thirst 5 then about 47 (blue bar, top right) |
| `repair-cold-t0.png`, `repair-cold-t8.png` | the cold place: the HUD reads -19.8 C, the client draws rain, thirst rises |

Salt water was not exercised in game (unit and MockBukkit coverage only); its steps are in the plan's `in_game_steps` for
the next capture.

## First pass (superseded for rain drinking)

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
