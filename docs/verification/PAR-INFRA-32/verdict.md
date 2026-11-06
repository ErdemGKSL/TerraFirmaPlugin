# PAR-INFRA-32 verification

- Date: 2026-11-06. Tree: `0498c89` plus this item (server-time mining: `paper/.../rendered/ServerTimeMining`,
  `MiningSession`, `RenderedMiningModule`; `CraftEngineIntegration` as `MiningDriver`).
- Servers: Paper 1.21.11 and Paper 26.3, flat world, peaceful, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud
  2.2.0-SNAPSHOT-449, PacketEvents 2.14.0, the S2 spike plugin and pack (`spikes/s2-destroy-stages`,
  `docs/spikes/S2-destroy-stages.md` §2: priming start, stop, delete world, start). Server 20.0 TPS (`tick query`).
- Clients: 1.21.11 and 26.3 under Xvfb + Mesa llvmpipe, OpenAL null device, light scene: render distance 2, fast
  graphics, no clouds, minimal particles, no entity shadows, vsync off. F3: 1.21.11 at 60 fps, 26.3 at 58 fps
  (`1.21.11-f3-60fps-scene.png`, `26.3-f3-scene.png`), so both clients hold 20 ticks/s. A second 1.21.11 run capped at
  10 fps (`1.21.11-f3-10fps-scene.png`): the client then runs two ticks per frame, so its swing packets arrive in pairs.
- Steps: stone floor at y -61; Bot in survival at (3.5, -60, 1.3) yaw 0 pitch 40 facing the block at (3, -60, 3);
  `tfp:metal/pickaxe/bronze` in slot 1, `tfp:metal/axe/bronze` in slot 2 (`/ce item give` from the client's chat, op),
  slot 3 empty. Each break: place the block (`s2 place tfps2:metal/block/copper 3 -60 3` or
  `setblock ... iron_block`), `xdotool mousedown 1` until the block is air, `mouseup 1`. Times are the spike's
  `[S2] break ... ticks=` lines (server ticks from the first `BlockDamageEvent` to the `BlockBreakEvent`).
- Formula (`MiningTime`, TFC `Metal.BlockType.blockProperties` strength 5, `TFCTiers.BRONZE` speed 7.3): bronze pickaxe
  7.3/5/30 per tick = 21 ticks; hand and bronze axe (speed 1, not correct) 1/5/100 = 500 in exact arithmetic, 501 as the
  vanilla client and CraftEngine sum the float.

## Measurements (server ticks)

| Run | Copper, bronze pickaxe | Copper, hand | Copper, bronze axe | Iron block control, pickaxe | Iron block control, hand |
|---|---|---|---|---|---|
| Before (PAR-INFRA-17 / blocked note, 1.21.11 at 34 fps) | 23, 22, 24, 21, 22 | 562, 529 | 541, 561 | 19, 20, 18, 20, 18, 20, 18 | 463, 467 |
| 1.21.11, 60 fps | 21, 21, 21, 21, 21 | 501, 501 | 501, 501 | 18, 20, 18, 20, 18 | 462, 465 |
| 1.21.11, 10 fps (bunched swings) | 21, 21, 21 | 501 (holding a paper-based TFC item, not a tool) | — | 18, 20, 18 | — |
| 26.3, 58 fps | 22, 21, 21, 21, 21 | 503, 501 | 501, 501 | 18, 20, 18, 20, 18 | 463, 466 |

## Verdicts

| Criterion | 1.21.11 | 26.3 |
|---|---|---|
| Copper block with the correct tool matches TFC's formula within one tick-rounding plus the iron-block control's latency | pass: 21 every time (formula 21), also at 10 fps | pass: 21-22 (formula 21; the control itself spreads 18-20) |
| Copper block by hand | pass: 501 (formula 501 summed / 500 exact) | pass: 501, 503 |
| Copper block with a wrong tool (bronze axe) | pass: 501 | pass: 501 |
| Break time independent of the client's swing rate | pass: identical at 60 fps and at 10 fps | not run at a capped rate (same code path) |

No errors from our plugin in either server log (the only exception is PacketEvents' update check, HTTP 403 from GitHub).
The vanilla iron block control by hand measures 462-466 ticks against the formula's 501 (the same as before this item:
463, 467); the vanilla dig is decided by the client and its stop packet, so the spike's server-side window does not
time it exactly. The pickaxe control (18-20 for 21) shows the same early end. Renderer cubes are counted entirely on
the server, so they now land on the formula itself.
