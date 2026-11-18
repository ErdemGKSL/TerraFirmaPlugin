# PAR-HEAT-28 verification

- Date: 2026-11-18 (repair round 8; replaces all earlier PAR-HEAT-28 evidence).
- Code under test: `a1a0d65` (TorchService `LightProbe` wait plus the neighbour-chunk resend, `chunksInReach`; unchanged
  since `54dd05f`). Jar built from that tree (`:dist:shadowJar`).
- Server / client: Paper 1.21.11 (run-paper cache), CraftEngine/BetterModel/BetterHud from the run-paper cache, headless
  vanilla 1.21.11 client (`spikes/client/session.sh`), bot `Bot`, TFC world (`TFP_WORLD=1`, primed and restarted).
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (10 tests). `./gradlew check` green
  on this tree.
- Every frame was looked at and judged by the main session.
- Verdict: **pass**. Both cross-chunk pairs read 14 block light lit and 0 dead in every torch cell, at once and with no
  relog, at GUI 2 and GUI 3.

## Setup and steps (as written in the item)

- `debug-profile.json` in the client game dir itself (`light_levels`, `looking_at_block`, `player_position` =
  `alwaysOn`): F3 shows `Client Light: N (S sky, B block)`.
- Right after each client join the bot typed `/hud turn off` in chat (xdotool `t`, type, Return); BetterHud answered
  "Successfully turned the HUD off." (`00-gui2-hud-off-chat.png`) and no BetterHud panel is drawn in any frame, so the F3
  lines are unobstructed.
- Test site on land: spawn column 1536 80 160 is cherry-grove land (`tfp debug chunk 1548 157`: open to the sky, not
  ocean). Stone floor at y 90 over the land column (x 1540-1595, z 145-170), air cleared above, a 3-high stone wall at
  z 150 (y 91-93). Floor seen from the torch spots shows grass and cherry leaves at the platform edge.
- `weather clear 1000000`, `gamemode survival Bot`, `tfp debug thirst Bot 100`, `tfp debug hunger Bot 20`,
  `ce item give Bot tfp:torch 2`; the bot placed both torches itself with a right click.
- Step 4: `gamemode creative Bot`, tp 300 blocks east (x 1848 / 1886) onto the surface (`positioned over world_surface`),
  `execute if loaded` on both torch cells returned "Test failed" twice, then one `tfp time add days 4`, tp back into the
  floor torch cell, 8 s wait, `gamemode survival Bot`, then the F3 readings. No relog between the lit and the dead reading
  of a session.

| session | floor torch (cell, chunk) | wall torch (cell, chunk) | apart (taxicab) | skip (chat reply) |
|---|---|---|---|---|
| GUI 2 (`guiScale:2`) | 1548 91 157, chunk 96,9 | 1553 91 151 facing south, chunk 97,9 | 11 | June 1 -> June 5, 1000 08:21 |
| GUI 3 (relaunched, `guiScale:3`, fresh torches) | 1586 91 157, chunk 99,9 | 1581 91 151 facing south, chunk 98,9 | 11 | June 5 -> July 1, 1000 10:06 |

The GUI 3 pair is 28+ blocks from the GUI 2 pair. Each pair sits across a chunk border, with each torch's light reaching
the other's chunk (the case that failed on `946ba98`).

## Results

Each F3 crop is the top band of the frame: left `Client Light`, `Targeted Block` and the targeted block id; right `XYZ`,
`Block` (the cell the bot stands in) and `Chunk`.

| criterion | GUI | cell | lit | dead after the skip and round trip, no relog | evidence |
|---|---|---|---|---|---|
| floor torch 14 lit, 0 dead at once | 2 | 1548 91 157 | `15 sky, 14 block`, target `minecraft:torch` | `15 sky, 0 block`, target `minecraft:torchflower_crop[age=1]` (dead torch carrier) | `01-gui2-lit-floor-f3-block14.png`, `03-gui2-dead-floor-f3-block0-no-relog.png` |
| wall torch 14 lit, 0 dead at once | 2 | 1553 91 151 | `15 sky, 14 block` | `15 sky, 0 block` | `02-gui2-lit-wall-f3-block14.png`, `04-gui2-dead-wall-f3-block0-no-relog.png` |
| floor torch 14 lit, 0 dead at once | 3 | 1586 91 157 | `15 sky, 14 block`, target `minecraft:torch` | `15 sky, 0 block`, target `minecraft:torchflower_crop[age=1]` | `07-gui3-lit-floor-f3-block14.png`, `09-gui3-dead-floor-f3-block0-no-relog.png` |
| wall torch 14 lit, 0 dead at once | 3 | 1581 91 151 | `15 sky, 14 block` | `15 sky, 0 block` | `08-gui3-lit-wall-f3-block14.png`, `10-gui3-dead-wall-f3-block0-no-relog.png` |
| dead wall torch in view | 2 / 3 | 3 blocks south of the wall torch | | charred torch on the wall, target `minecraft:big_dripleaf[facing=south,tilt=full]`, `0 block` | `05-gui2-dead-wall-torch-view.png`, `11-gui3-dead-wall-torch-view.png` |
| dead floor torch in view | 2 / 3 | 3 blocks south of the floor torch | | charred stick on the floor, target `minecraft:torchflower_crop[age=1]`, `0 block` | `06-gui2-dead-floor-torch-view.png`, `12-gui3-dead-floor-torch-view.png` |
| BetterHud panel hidden | 2 | chat after join | | "Successfully turned the HUD off." | `00-gui2-hud-off-chat.png` |

In the wall cells `Targeted Block` is the stone above the torch (optional per the steps); the `Block` line shows the bot
stands in the torch cell. The neighbour torch's light is also gone in every dead cell (0, not 14 - 11 = 3).

The stale 14 (and the stale neighbour 5 of `946ba98`) did not reproduce with the resend in place. PAR-HEAT-29 already read
0 once without it, so the stale light is intermittent; it did reproduce on `6381fb6` before the `LightProbe` wait and on
`946ba98` before the neighbour resend. The resend stays as a guard (one chunk resend per affected chunk, only with
viewers).
