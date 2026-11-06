# PAR-SURV-04: Thirst completion: verdict: pass (repair round)

## Unblock run (after the in-game REJECT of `8068b05`)

The REJECT had two causes, both in the plan and the test run, not in the code: the tester used pitch -60, which TFC's gate
(`ForgeEventHandler.java:771-772`, `wrapDegrees(pitch) <= -80`, our `RainDrinking.MAX_PITCH`) refuses, and the old step asked
for a slowness icon that TFC never shows (`new MobEffectInstance(SLOWDOWN, 160, 1, false, false)`). The plan's `done_criteria`
and `in_game_steps` now follow TFC. Code unchanged (`8068b05`).

- Server: Paper 1.21.11, `TFP_WORLD=1`, priming start, world deleted, second start; jar from `:dist:shadowJar`. Client:
  vanilla 1.21.11, Xvfb 1280x720, GUI scale 2. Calendar default (June 1, 1000), TFC ambient 7.3 C.
- Before the join: `forceload add -16 -16 16 16`, stone floor at y 99 confirmed with `execute if block`, air above,
  `setworldspawn 0 100 0`, `difficulty normal`. The Bot joined on the floor: no fly kick.

| step | observed | verdict |
|---|---|---|
| `tfp debug thirst Bot 5`, 10 s, `data get entity Bot active_effects` | `minecraft:slowness` and `minecraft:mining_fatigue`, `amplifier: 1b`, duration 140/141 (refreshed to 160 every 100 ticks), `show_particles: 0b`, `show_icon: 0b`; health 20.0 unchanged at thirst 5 | pass |
| `tfp debug thirst Bot 0`, survival, normal, health read every 2.5-5 s | 20 -> 19 at the first check; then 19 held while `foodLevel` was 20 (vanilla natural regeneration at food >= 18 heals 1 per 4 s, replaced only by PAR-SURV-05); once food was 17: 17, 15, 14, 13, 12 ... 10, one point per ~5 s (100 ticks); HUD 413/826, thirst bar empty | pass |
| control: `weather rain`, `tp Bot 0 100 0 0 -60`, thirst 5, 8 s | `tfp debug rain Bot`: `pitch -60.0 (looks up: false, needs <= -80)`, every other gate true; thirst stays 5.0 (the in-game tester's run) | pass |
| `tp Bot 0 100 0 0 -90` in the same rain | gates all true, `drinks rain: true`; thirst 5.0 -> 17.5 (2 s) -> 25.8 (4 s) -> 34.2 (6 s), `lastDrinkTick` never (TFC's `addThirst`); the bar grows from a sliver to about a third | pass |

The 7.3 C reading in rain (and the -1.7 C reading the in-game reviewer saw with rain, not snow) is the ledger divergence
"raining at": the client draws the vanilla storm through the biome it is sent, whatever the TFC climate says.

| file | content |
|---|---|
| `unblock-thirst5-hud.png` | HUD strip (2x) at thirst 5: thirst sliver, health full, no effect icon (as TFC) |
| `unblock-thirst0-hud.png` | HUD strip (2x) at thirst 0: thirst empty, health 413/826 after the dehydration hits |
| `unblock-rain-before.png`, `unblock-rain-before-hud.png` | rain on screen, pitch -60, thirst 5 (sliver) |
| `unblock-rain-after.png`, `unblock-rain-after-hud.png` | looking straight up (-90) about 6 s later, thirst bar about a third |

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

The four `repair-*` captures are crops (PROTOCOL 6.3, under 200 KB): the top 120 rows of the 640x360 frame (climate panel
and rain-filled sky) above the bottom-centre HUD strip (health, thirst bar above the hunger bar, hotbar); the black areas
are outside the crops.

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
