# PAR-HUD-12: HUD and needs per game mode: verdict: pass

- Date: 2026-11-05. Tree `6e72642` plus this item's fix (`PlayerNeedsHudService.onGameModeChange` settles the thirst clock
  and publishes the flag of `event.getNewGameMode()` inside the event; `onJoin` republishes on the next tick), jar from
  `:dist:shadowJar` with the shared generated content (`tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start),
  CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb 1280x720 (`spikes/client/session.sh`,
  `guiScale:2`, relaunched with `guiScale:3`, relaunched again with `guiScale:2`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker). Log: no error from our plugin (only
  the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).

## Why this rerun

The first run (review 2026-11-05) failed at GUI 3: every frame showed the HUD of the previous mode. The handler read the old
mode correctly but published nothing until a task on the player's next tick, so a slow tick left the old `needsActive` in
the snapshot until the 1 s throttle refreshed it. The fix publishes inside the event; `GameModeNeedsMockBukkitTest.
theSnapshotBuiltDuringTheEventHoldsTheNewMode` asserts the snapshot right after the handler returns, before the mode is
applied and before any tick, for survival→creative→survival→spectator→adventure.

## Steps (RCON)

`forceload add -16 -16 16 16`, stone floor at y 99, `weather clear`, `tfp time set noon`, join, `tp Bot 0 100 0 0 30`,
`gamemode survival Bot`, `tfp debug thirst Bot 50`; then per scale (`steps.sh`): survival, creative, spectator, adventure,
each with a frame about 0.3 s and 3 s after the command; then `gamemode survival Bot`, `tfp debug thirst Bot 50`,
`gamemode creative Bot`, `time add 2400`, `tfp debug player Bot`, `gamemode survival Bot`, `tfp debug player Bot`.
Timing check per scale (`burst.sh`): after each switch (creative, spectator, adventure, creative, survival) 8 back-to-back
frames (about 0.25 s apart, 2 s in all). Join check: `gamemode creative Bot`, client relaunched (GUI 2).

## Crops

| file | content |
|---|---|
| `step1-survival-g{2,3}.png` | survival: health 103/826, thirst (blue) and hunger (orange) bars above the hotbar |
| `step2-creative-3s-g{2,3}.png` | creative: hotbar only, no TFC bar, no XP bar |
| `step3-spectator-3s-g{2,3}.png` | spectator: no TFC bar, no hotbar |
| `step4-adventure-3s-g{2,3}.png` | adventure: the bars as in survival |
| `step5-survival-after-creative-2s-g{2,3}.png` | survival again after the creative stretch: bars back |
| `burst-<mode>-g{2,3}.png` | the 8 frames after switching to `<mode>` (left to right, top to bottom); `creative` is the switch from adventure |
| `join-creative-g2-half.png` | whole screen at half size after a join in creative: info plate top left, hotbar, no TFC bar |

## Checks

| check | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| Survival and adventure show health, thirst and hunger bars | pass | pass | step1, step4, step5 |
| Creative shows no TFC bar (and no held-item popup: the hand is empty, the popup carries the same gate, `HudLayoutsTest`) | pass | pass | step2 |
| Spectator shows no TFC bar | pass | pass | step3 |
| The HUD follows the switch at once: in every burst frame the TFC bars match the vanilla state drawn in the same frame (XP bar, hotbar, chat line of the new mode); the first frame that shows the new mode's chat line or hotbar already shows the new mode's bars, so no frame shows the previous mode's HUD | pass (32 frames) | pass (32 frames) | burst sheets |
| Thirst paused in creative, no catch-up: 50.0 before creative, 50.0 after `time add 2400` in creative, 50.0 back in survival (`tfp debug player Bot`, both scales) | pass | pass | RCON output |
| A join in creative shows no TFC bar | pass | n/a | join crop |
| The top-left date/temperature plate stays in every mode (ledger divergence) | as designed | as designed | join crop |

The frames about 0.3 s after the RCON command (not stored) show the client before it applied the switch at all (old chat
line, old hotbar and XP bar); the HUD there matches that old vanilla state too. The previous run's defect was the opposite:
the vanilla state of the new mode with the TFC HUD of the old one.
