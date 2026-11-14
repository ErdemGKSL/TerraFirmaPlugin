# PAR-PROC-09 in-game verification: barrel GUI tank

- Date: 2026-11-14
- Tree: 01..04 on 542882a plus the PAR-PROC-09 review fix (b08bcdb: `oversized_in_gui` on `items/gui/barrel_tank.json`,
  fluid tiled from the top of the fill); 05..10 recaptured on origin/push-to-main/dreamy-heisenberg-f5azym @ 8597544
  (contains b08bcdb and f71daab), after `tools/worktree/share-build.sh` and `:dist:shadowJar`.
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start + restart), CraftEngine pack
  self-hosted on 127.0.0.1.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then relaunched with `MC_OPTIONS="guiScale:3"`; the
  pack applied 3 s after each join.

## Steps

1. `op Bot` over RCON; in chat: `/tfp debug barrelgui 5000` (chat: "tank frame 25"), screenshot.
2. `/tfp debug barrelgui 0`, `200` (frame 1), `10000` (frame 50), screenshot each.
3. The same four at GUI scale 3.
4. Recapture (8x crops, the item's in_game_steps): a fresh server (priming start + restart), `/tfp debug barrelgui 0`,
   `200` (chat "tank frame 1") and `10000` (chat "tank frame 50") at GUI scale 2, then the client relaunched with
   `MC_OPTIONS="guiScale:3"` and the same three; each tank cropped with its frame and gauge and scaled 8x (nearest
   neighbour).

## Evidence

| File | What |
|---|---|
| `01-gui-5000mB-g2.png` | the whole screen at 5000 mB, GUI scale 2 |
| `02-gui-5000mB-g3.png` | the whole screen at 5000 mB, GUI scale 3 |
| `03-tank-0-200-5000-10000mB-g2-x4.png` | the tank at 0, 200, 5000, 10000 mB, GUI scale 2, 4x |
| `04-tank-0-200-5000-10000mB-g3-x3.png` | the same at GUI scale 3, 3x |
| `05-tank-0mB-g2-x8.png`, `06-tank-200mB-g2-x8.png`, `07-tank-10000mB-g2-x8.png` | the tank at 0, 200, 10000 mB, GUI scale 2, 8x |
| `08-tank-0mB-g3-x8.png`, `09-tank-200mB-g3-x8.png`, `10-tank-10000mB-g3-x8.png` | the same at GUI scale 3, 8x |

## Verdict

| Criterion | Result |
|---|---|
| Screenshot of the barrel GUI with a half-full tank filling from the bottom (GUI scale 2 and 3) | PASS: at 5000 mB the water is one solid column from the tank bottom up to the middle (long) tick, the grey tank above it, the gauge ticks drawn over the water; no band, nothing clipped at the slot edges |
| 1 and 50 pixel frames at the edges | PASS (05..10, 8x): 0 mB draws no water; 200 mB draws one window pixel row (2 screen rows at scale 2, 3 at scale 3, measured on the full screenshots) directly on the white bottom bevel, nothing floating; 10000 mB fills 50 window rows (100 / 150 screen rows) from the bevel to just under the dark top border, no overflow past the border, the gauge or the right bevel; the gauge ticks are drawn over the water at every level |
| Block, liquid surface, rain and rack | Deferred check tracked in PAR-PROC-35 |

The earlier rejection (thin floating band at half capacity) was the 1.21.6+ client clipping the 2x tank model to its
16 x 16 slot (window rows 36..52); `oversized_in_gui: true` lifts the clip. The water is vanilla's grey `water_still`
tinted with TFC's water colour (recorded divergence).
