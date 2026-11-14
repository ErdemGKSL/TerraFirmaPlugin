# PAR-PROC-09 in-game verification: barrel GUI tank

- Date: 2026-11-14
- Tree: origin/push-to-main/dreamy-heisenberg-f5azym @ 542882a plus the PAR-PROC-09 review fix (`oversized_in_gui` on
  `items/gui/barrel_tank.json`, fluid tiled from the top of the fill), content regenerated with datagen.
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start + restart), CraftEngine pack
  self-hosted on 127.0.0.1.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then relaunched with `MC_OPTIONS="guiScale:3"`; the
  pack applied 3 s after each join.

## Steps

1. `op Bot` over RCON; in chat: `/tfp debug barrelgui 5000` (chat: "tank frame 25"), screenshot.
2. `/tfp debug barrelgui 0`, `200` (frame 1), `10000` (frame 50), screenshot each.
3. The same four at GUI scale 3.

## Evidence

| File | What |
|---|---|
| `01-gui-5000mB-g2.png` | the whole screen at 5000 mB, GUI scale 2 |
| `02-gui-5000mB-g3.png` | the whole screen at 5000 mB, GUI scale 3 |
| `03-tank-0-200-5000-10000mB-g2-x4.png` | the tank at 0, 200, 5000, 10000 mB, GUI scale 2, 4x |
| `04-tank-0-200-5000-10000mB-g3-x3.png` | the same at GUI scale 3, 3x |

## Verdict

| Criterion | Result |
|---|---|
| Screenshot of the barrel GUI with a half-full tank filling from the bottom (GUI scale 2 and 3) | PASS: at 5000 mB the water is one solid column from the tank bottom up to the middle (long) tick, the grey tank above it, the gauge ticks drawn over the water; no band, nothing clipped at the slot edges |
| 1 and 50 pixel frames at the edges | PASS: 0 mB draws no water; 200 mB draws one pixel row at the tank bottom; 10000 mB fills the whole tank to the top border |
| Block, liquid surface, rain and rack | Deferred check tracked in PAR-PROC-35 |

The earlier rejection (thin floating band at half capacity) was the 1.21.6+ client clipping the 2x tank model to its
16 x 16 slot (window rows 36..52); `oversized_in_gui: true` lifts the clip. The water is vanilla's grey `water_still`
tinted with TFC's water colour (recorded divergence).
