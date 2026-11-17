# PAR-HEAT-28 verification

- Date: 2026-11-17 (round 4; replaces all earlier PAR-HEAT-28 evidence).
- Code under test: `6381fb6` plus this item's commit (the `TorchService.LightProbe` wait before the resend). Jar built
  from that tree (`:dist:shadowJar`).
- Server / client: Paper 1.21.11 build 132, CraftEngine/BetterModel/BetterHud from the run-paper cache, headless vanilla
  1.21.11 client, bot `Bot`, fresh TFC world (`TFP_WORLD=1`, primed and restarted per `docs/testing/headless.md`).
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (8 tests). `./gradlew check`
  green on this tree.
- Run and judged by the main session (no Haiku agent tool in this worker); every frame below was looked at.
- Verdict: **pass** (after a fix: the stale 14 reproduced on `6381fb6`, see "Before the fix").

## Setup

`debug-profile.json` in the client game dir (`light_levels`, `looking_at_block`, `player_position` = `alwaysOn`), so F3
shows `Client Light: N (S sky, B block)`. `/hud turn off` typed by the bot (as op) after every join and after every
`gamemode survival`, so the BetterHud panel does not cover the F3 lines; dates are read from the `tfp time add` replies.
Test site next to spawn (land, not ocean): stone floor `fill 1478 66 -2392 1512 70 -2362 stone`, air above, a 3-high
stone wall at z -2370..-2362. `weather clear 1000000`, `gamemode survival Bot`, `tfp debug thirst Bot 100`,
`tfp debug hunger Bot 20`, `ce item give Bot tfp:torch 2` per pair.

Every torch was placed by the bot (right click) into the cell it stands in: floor torches looking straight down, wall
torches looking at the wall (pitch 55) from the cell in front of it, so each reading is the F3 light of the torch's own
cell (the right-column `Block:` line equals the torch position in every crop).

Step 4 as written: `gamemode creative Bot`, tp 300+ blocks east (x 1800), wait until `execute if loaded` fails for both
torches (10-11 s), one `tfp time add days 4` while unloaded, tp back into the floor torch cell, wait 8 s,
`gamemode survival Bot`, then F3 in each cell. No relog between the lit and the dead reading of a pair.

## Before the fix (stale 14 reproduced on `6381fb6`)

First pair (floor 1484 71 -2382, wall 1494 71 -2371, GUI 2, June 1 -> June 5): both dead torches read **14 block light**
standing in their cells after the round trip, also 15 s later (`11-…`, `12-…`). After a client relog the wall cell read
0, so the server was right and the client kept stale light. A debug build logged the burnout (viewers=1), the resend 5
ticks later (viewers=1), and the server block light in the dead torch cell: **14 at +1, +2, +5, +10 ticks**, 0 from
+20 (the server was busy with chunk loads; +10 to +20 took 8 s). So the old fixed 5-tick resend sent the stale light
again. Fix: `TorchService.LightProbe` holds the resend until the server block light at every burnt-out position of the
chunk is below 14 (checked every 5 ticks, at most 400 ticks), then resends.

## Results with the fix

| criterion | GUI | torch (cell) | lit | dead after one skip + round trip, no relog | evidence |
|---|---|---|---|---|---|
| floor torch reads 0 at once, no relog | 2 | floor 1480 71 -2386 | `15 sky, 14 block`, targets `minecraft:torch` | `15 sky, 0 block`, targets `torchflower_crop[age=1]` (dead carrier), August 1 | `01-gui2-lit-floor-f3-block14.png`, `03-gui2-dead-floor-f3-block0-no-relog.png` |
| wall torch reads 0 at once, no relog | 2 | wall 1486 71 -2371 (facing north) | `15 sky, 14 block`, targets `minecraft:wall_torch` | `15 sky, 0 block`, targets `big_dripleaf[tilt=full]` (dead wall carrier), August 1 | `02-gui2-lit-wall-f3-block14.png`, `04-gui2-dead-wall-f3-block0-no-relog.png` |
| floor torch reads 0 at once, no relog | 3 | floor 1500 71 -2380 | `15 sky, 14 block`, targets `minecraft:torch` | `15 sky, 0 block`, targets `torchflower_crop[age=1]`, August 6 | `06-gui3-lit-floor-f3-block14.png`, `08-gui3-dead-floor-f3-block0-no-relog.png` |
| wall torch reads 0 at once, no relog | 3 | wall 1511 71 -2371 (facing north) | `14 sky, 14 block`, targets `minecraft:wall_torch` | `14 sky, 0 block`, targets `big_dripleaf[tilt=full]`, August 6 | `07-gui3-lit-wall-f3-block14.png`, `09-gui3-dead-wall-f3-block0-no-relog.png` |
| dead torches look dead | 2 / 3 | both cells of each pair | | charred stick on the wall (left) and on the floor (right) | `05-gui2-dead-wall-and-floor-torch-view.png`, `10-gui3-dead-wall-and-floor-torch-view.png` |

The torches of a pair are 20-21 blocks apart (Manhattan), the GUI 3 pair 20+ blocks from the GUI 2 pair, so no torch adds
light to another's reading. The GUI 3 pair was placed fresh after the relaunch with `guiScale:3`; its lit and dead
readings come from that one session. The GUI 2 lit and dead readings come from one session too. Each crop stacks the F3
left column (`Client Light`, `Targeted Block` and the block state) over the right column's `XYZ` / `Block` lines.
Rain in `05`/`10` is the plugin's weather cycle. Lossless WebP full frames: session scratchpad `ui/PAR-HEAT-28/r4-*.webp`.

Fidelity: TFC (`TFCTorchBlock` / `DeadTorchBlock`) needs no resend because the NeoForge client relights on the block
change; the plugin-side resend is a platform workaround with no gameplay effect, so no divergence row.
