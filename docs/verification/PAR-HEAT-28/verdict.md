# PAR-HEAT-28 verification

- Date: 2026-11-17
- Code under test: commit `36bcc70` (HEAD of the run; `TorchService.LightRefresh`, one chunk light resend 5 region ticks
  after each burnout the commit queue applies, unchanged since `35ab107` plus the `b84781e` log-once repair).
- Server / client: Paper 1.21.11, headless vanilla 1.21.11 client, bot `Bot`.
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (5 tests). `./gradlew check` green
  on `36bcc70` (tester) and on the commit holding this verdict.
- Verdict: **pass** (judged by the screenshot judge; all 8 frames 1280x720 with the F3 `Client Light` line).

## Steps run (the item's corrected `in_game_steps`)

1. `debug-profile.json` in the client game dir (next to `options.txt`) with `minecraft:light_levels`,
   `looking_at_block` and `player_position` set to `alwaysOn`; launch with `MC_OPTIONS=guiScale:2`. Land test spot with a
   stone floor and a 3-high stone wall; tp after a 5 s chunk-load wait.
2. `weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20`
3. `ce item give Bot tfp:torch 2`; floor torch and wall torch 6 blocks apart on x (9 by Manhattan distance); F3 standing
   in each torch block.
4. `gamemode creative Bot; tfp time add days 4`; tp 300 blocks away, wait 8 s, tp back (no relog);
   `gamemode survival Bot`.
5. F3 standing in each dead torch block. Then relaunch with `MC_OPTIONS=guiScale:3`, place two fresh torches 20 blocks
   further along x (in a carved pit, sky light 0) and repeat steps 3-5 in that one session.

Tester deviations (before the baselines, no effect on the result): the wall torch was moved down to y 79, and one stray
setblock was re-placed.

## Results

| torch | GUI | lit (F3 Client Light) | dead after skip + round trip, no relog | evidence |
|---|---|---|---|---|
| floor, Block -203 79 -1 | 2 | `15 (15 sky, 14 block)`, June 1 | `15 sky, 0 block`, June 5, chat "game mode updated to Survival" | `01-gui2-lit-floor-f3-block14.png`, `03-gui2-dead-floor-f3-block0-no-relog.png` |
| wall, Block -197 79 2 (target stone -197 80 3) | 2 | `14 block` | `0 block`, June 5 | `02-gui2-lit-wall-f3-block14.png`, `04-gui2-dead-wall-f3-block0-no-relog.png` |
| floor, Block -183 79 -1 | 3 | `14 (0 sky, 14 block)`, June 5 | `0 (0 sky, 0 block)`, July 1, Survival chat line | `05-gui3-lit-floor-f3-block14.png`, `07-gui3-dead-floor-f3-block0-no-relog.png` |
| wall, Block -177 79 2 | 3 | `14 block` | `0 block`, July 1 | `06-gui3-lit-wall-f3-block14.png`, `08-gui3-dead-wall-f3-block0-no-relog.png` |

The torches of a pair are 9 blocks apart by Manhattan distance, so the other torch adds at most about 5 light: each lit 14
comes from that cell's own torch (this fixes the 35ab107 run's floor baseline of 11). The GUI 3 pair was placed and burnt
out in the relaunched session, so its lit and dead readings are no-relog too. All four torch cells read 14 lit and 0 dead
in one client session each, with the date advancing between shots; no stale 14 remains.

Crops: each PNG is the top F3 band (light line, date, targeted block, position) above a strip of the chat line. The full
frames are lossless WebP in the session scratchpad `ui/PAR-HEAT-28/r2-*.webp`.

Notes (not blocking): the torch models are not in frame, because the bot stands in the torch cell looking at the wall; a
lit 14 block light at the cell (with 0 sky light at GUI 3) only fits a light source in that cell. Fidelity: TFC
(`TFCTorchBlock` / `DeadTorchBlock`) needs no resend because the NeoForge client relights on the block change; the
plugin-side resend is a platform workaround with no gameplay effect, so no divergence row.

History: the 35ab107 run (floor baseline 11, GUI 3 only after a relaunch) and the b84781e re-run (profile under
`config/`, no light line) were rejected; this run replaces both.
