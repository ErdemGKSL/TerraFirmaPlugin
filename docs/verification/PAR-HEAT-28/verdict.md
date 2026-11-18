# PAR-HEAT-28 verification

> Status 2026-11-18 (round 7): the evidence below (t6 on `54dd05f`) was rejected by the unblocker for its procedure
> (BetterHud panel over the first F3 line because `/hud turn off` was sent from the console, Targeted Block on stone, no
> dead floor torch view). No code change in round 7; `./gradlew check` green on `a1a0d65`. The re-run with the corrected
> `in_game_steps` (the bot types `/hud turn off` itself after each join, stone platform over a dry land column, both dead
> torch views, a fresh GUI 3 pair in the relaunched session) is left to the verify stage, which replaces this file and the
> crops. It must show 14 lit and 0 dead with no relog in all four cells; if the stale 14 or a neighbour 5 reproduces, the
> fix belongs in `TorchService` (files_owned).

- Date: 2026-11-17 (run "t6"; replaces all earlier PAR-HEAT-28 evidence, including the rejected round 3 and round 4 crops).
- Code under test: `54dd05f` (the `TorchService.LightProbe` wait plus the neighbour-chunk resend, `chunksInReach`). Jar
  built from that tree (`:dist:shadowJar`).
- Server / client: Paper 1.21.11 build 132, CraftEngine/BetterModel/BetterHud from the run-paper cache, headless vanilla
  1.21.11 client, bot `Bot`.
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (10 tests, including the two-chunk
  pair). `./gradlew check` green on this tree.
- Every frame was looked at and judged by the main session.
- Verdict: **pass**. Both two-chunk pairs read 14 block light lit and 0 dead in every torch cell, at once and with no relog.

## Setup and steps

`debug-profile.json` in the client game dir (`light_levels`, `looking_at_block`, `player_position` = `alwaysOn`), so F3
shows `Client Light: N (S sky, B block)`. `weather clear 1000000`, `gamemode survival Bot`, `tfp debug thirst Bot 100`,
`tfp debug hunger Bot 20`, `ce item give Bot tfp:torch 2` per pair.

- GUI 2 session (`guiScale:2`): floor torch at 14 91 6 (chunk 0,0), wall torch at 17 91 1 (chunk 1,0), 8 blocks apart
  (taxicab), so each torch's light reaches the other's chunk: the case that failed on `946ba98` (floor cell read 5).
- GUI 3 session (relaunched with `guiScale:3`): fresh floor torch at 31 91 6 (chunk 1,0), wall torch at 35 91 1
  (chunk 2,0), again 8 blocks apart across a chunk border, and 17+ blocks from the first pair.
- Step 4 as written: `gamemode creative Bot`, tp 1800 blocks away, `forceload` removed, wait until `execute if loaded`
  fails for the torch chunks, one `tfp time add days 4` while unloaded, tp back into the floor torch cell, wait 8 s,
  `gamemode survival Bot` (the chat line "Your game mode has been updated to Survival Mode" is in the dead frames), then F3
  in each cell. No relog between the lit and the dead reading of a session.

## Results

Each F3 crop stacks the left column (`Client Light`, `Targeted Block`) over the right column (`XYZ`, `Block`, `Chunk`);
the `Block` line is the torch cell the bot stands in. GUI 2 crops are scaled 2x (nearest).

| criterion | GUI | cell | lit (date) | dead after one skip + round trip, no relog (date) | evidence |
|---|---|---|---|---|---|
| floor torch reads 0 at once, no relog | 2 | floor 14 91 6, chunk 0 | `15 sky, 14 block` (June 1) | `15 sky, 0 block` (June 5) | `01-gui2-lit-floor-f3-block14.png`, `03-gui2-dead-floor-f3-block0-no-relog.png` |
| wall torch reads 0 at once, no relog | 2 | wall 17 91 1, chunk 1 | `15 sky, 14 block` (June 1) | `15 sky, 0 block` (June 5) | `02-gui2-lit-wall-f3-block14.png`, `04-gui2-dead-wall-f3-block0-no-relog.png` |
| floor torch reads 0 at once, no relog | 3 | floor 31 91 6, chunk 1 | `15 sky, 14 block` (June 5) | `15 sky, 0 block` (July 1) | `06-gui3-lit-floor-f3-block14.png`, `08-gui3-dead-floor-f3-block0-no-relog.png` |
| wall torch reads 0 at once, no relog | 3 | wall 35 91 1, chunk 2 | `15 sky, 14 block` (June 5) | `15 sky, 0 block` (July 1) | `07-gui3-lit-wall-f3-block14.png`, `09-gui3-dead-wall-f3-block0-no-relog.png` |
| dead torch looks dead | 2 / 3 | dead wall torch seen from the dead floor cell | | charred stick on the wall | `05-gui2-dead-wall-torch-view.png`, `10-gui3-dead-wall-torch-view.png` |

The GUI 2 floor reading of 14 is the floor torch's own light: the wall torch is 8 blocks away and would give about 6
there. The neighbour torch's light is gone in both dead cells of both pairs (0, not 14 - 8 = 6), which is what the
`chunksInReach` resend fixes.

The stale 14 (and the stale neighbour 5 of `946ba98`) did not reproduce with the resend in place. PAR-HEAT-29 already
read 0 once without it, so the stale light is intermittent; the stale 14 did reproduce on `6381fb6` before the
`LightProbe` wait, and the stale 5 on `946ba98` before the neighbour resend. The resend is kept as a guard (one chunk
resend per affected chunk, only with viewers).

## Deviations from the steps

1. The BetterHud date/temperature panel overlaps the first F3 line (`/hud turn off` in this setup is not BetterHud's
   command and did not hide it). The light values (`15 sky, 14 block` / `15 sky, 0 block`) stay readable in every crop and
   the date is read from the HUD panel itself.
2. The test site is a stone platform at y 90 above terrain at heights 81-91, not built on land. Chunk unload and load
   behaved normally (forceload removed, 1800-block tp away, `execute if loaded` failed before the skip).
3. `Targeted Block` points at the stone behind or below the torch cell, not at the torch carrier; the `Block` line shows
   the bot stands in the torch cell, which is what the steps ask for.
4. Full frame `t6-g2-01-lit-floor.webp` (session scratchpad) is mislabeled (a GUI 3 frame of 35 91 1 on July 1) and is
   not used; the GUI 2 lit floor evidence is `t6-g2-01-lit-floor-b.webp`.

Lossless WebP full frames: session scratchpad `ui/PAR-HEAT-28/t6-*.webp`.

## History

- `6381fb6`: both dead torches read 14 after the round trip (server engine still held 14 ten ticks after the swap); fixed
  by the `LightProbe` wait.
- `946ba98`: two-chunk pair, dead floor cell read 5 (the neighbour wall torch's light) until a relog; fixed by
  `chunksInReach` in `54dd05f`, verified above.

Fidelity: TFC (`TFCTorchBlock` / `DeadTorchBlock`) needs no resend because the NeoForge client relights on the block
change; the resend is a client-sync workaround with no gameplay effect. The Folia limit of the light probe is a
Divergences row in `docs/porting/ledger.md`.
