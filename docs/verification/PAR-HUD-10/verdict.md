# PAR-HUD-10: held temperature popup shows TFC's level text: verdict: PASS (criteria 1 and 2)

- Date: 2026-11-07. Tree: this commit (the heat-write fix and the U+2605 star), `:dist:shadowJar` from the shared
  generated content (no datagen change in this commit).
- Server: Paper 1.21.11, CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449, BetterModel 3.5.1, started with
  `spikes/client/server.sh` (vanilla terrain). Client: vanilla 1.21.11 under Xvfb 1280x720 (`spikes/client/session.sh`),
  one client per GUI scale (`MC_OPTIONS=guiScale:2`, then a new client with `guiScale:3`); the pack applied 3 s after
  each join. Server log: no error from our plugin.

## What was wrong (root cause)

1. `/tfp debug heat` wrote the main hand on the player's thread but then called `PlayerNeedsHudService.refreshHeld`,
   which republishes the held heat on the player's **next** tick (`scheduler.entity`). BetterHud then picked the value
   up on its own async update, one more tick later at best. Measured before the fix with the same recording as below
   (`g3-before-fix-timeline.txt`): the popup changed 0.06-0.22 s after the reply (900 C after 0.12 s and 0.22 s, 0 C
   after 0.06 s and 0.21 s).
2. The earlier evidence overstated the lag. The `import -window root` screenshots (reviewers' webp set, `shot.sh`) log
   the time when `import` returns, after it has grabbed the X screen and encoded a 1280x720 PNG; the grab itself happens
   near the start of that call. A shot "taken +0.42 s after the reply" was grabbed well before that, inside the window in
   which the old build had not yet shown the new level; that is why every such frame showed the previous level. The
   20 fps recording of the previous round used the same server-side path and found 0.27-0.46 s with a slower capture.

Fixed (decide on the player's thread, apply there, no hop):

- `PlayerNeedsHudService.heldHeatWritten(player)`: player thread only; publishes the main-hand heat record inline.
  `TfpPlugin.debugSetHeat` and `debugWarm` call it right after `setItemInMainHand`.
- `publishHeld` publishes only a changed record, and on a change calls `HudBridge.push(uuid)`:
  `HudSnapshotCache.push` -> `BetterHudIntegration.push` -> BetterHud `HudPlayer.update()`, which renders the player's
  HUD from the snapshot and sends the boss-bar line at once, instead of on BetterHud's next timer run. The next-tick
  republish after a hotbar/swap/click/drag/pickup/drop event pushes too when the held record changed.
- The popup settings (`default: true`, no `unique`, `duration` -1, so `always-check-condition` is off because BetterHud
  only enables it with `queue`) are right for this popup. Read from BetterHud's bytecode (`PopupImpl.show0`,
  `PopupIteratorGroupImpl.next`): a default popup is shown again on every update; each update adds a fresh iterator only
  while `tfp_held_temp > 0` holds, drops the older one (index past the last layout), and drops an iterator at once when
  its condition (re-read every frame through `available()`) is false. No setting delays a value change, and the
  recordings show no blank or intermediate frame.

## How it was measured

`room.sh` (snow floor, light blocks, `gamemode survival Bot`, saturation, `clear Bot`, `ce item give Bot
tfp:metal/ingot/copper`, `tfp debug heat Bot 0`), 20 s wait, then `measure.sh`: the screen is recorded at 30 fps with
wall-clock frame times (`ffmpeg -f x11grab -use_wallclock_as_timestamps 1 -copyts`) while, strictly one at a time,

1. a vanilla control: `title Bot times 0 40 0`, then three times `title Bot title {"text":"XXXX","color":"red"}`
   2.5 s apart (shows the pipeline latency: server packet, client frame, capture);
2. `tfp debug heat Bot <t>` for t = 900, 600, 0, 900, 0, 2.5 s apart.

Every RCON send and reply is logged with its wall-clock time (`g*-rcon.log`). `analyze.py` classifies every frame by
the exact colours in the band above the hotbar (`#ff5555` RED, `#aa0000` DARK_RED, else none) and the title's red;
`*-timeline.txt` lists each reply, every frame where the state changes, and the first changed frame after each reply.
`frames.py` saves the first frame at least 0.15 s after each heat reply (full screen and a 3x crop of the popup band).
(The `title ... times` row of the latency list is not a measurement: that command shows nothing.)

## Results

| | vanilla `/title` (control) | `tfp debug heat` 900 / 600 / 0 / 900 / 0 |
|---|---|---|
| GUI 2 (`g2-timeline.txt`) | 0.06-0.12 s | 0.11 / 0.09 / 0.10 / 0.09 / 0.06 s |
| GUI 3 (`g3-timeline.txt`) | 0.08-0.09 s | 0.09 / 0.09 / 0.07 / 0.07 / 0.06 s |
| GUI 3, before the fix (`g3-before-fix-timeline.txt`) | 0.06-0.10 s | 0.12 / 0.14 / 0.06 / 0.22 / 0.21 s |

After the fix the popup follows a heat write as fast as a vanilla title follows `/title`: the change lands in the first
client frames after the reply, with no stale-level frame, no blank frame and no intermediate level (900 to 600 goes
straight from RED to DARK_RED).

## Evidence (each frame is the first one at least 0.15 s after its reply; all within 0.25 s)

| file | reply | frame | content |
|---|---|---|---|
| `g2-900-crop-3x.png`, `g2-900-full.png` | `set: 900.0 C` | +0.180 s | `Bright Red★★★★` in `#ff5555`, black shadow |
| `g2-600-crop-3x.png`, `g2-600-full.png` | `set: 600.0 C` | +0.152 s | `Dark Red` in `#aa0000`, no star |
| `g2-0-crop-3x.png`, `g2-0-full.png` | `set: 0.0 C` | +0.165 s | no popup (cold ingot) |
| `g3-900-crop-3x.png`, `g3-900-full.png` | `set: 900.0 C` | +0.158 s | `Bright Red★★★★` in `#ff5555`, black shadow |
| `g3-600-crop-3x.png`, `g3-600-full.png` | `set: 600.0 C` | +0.153 s | `Dark Red` in `#aa0000`, no star |
| `g3-0-crop-3x.png`, `g3-0-full.png` | `set: 0.0 C` | +0.174 s | no popup (cold ingot) |

## Checks

- Criterion 1: 900 C shows `Bright Red` and four stars in RED `#ff5555` at GUI 2 and 3, legible at GUI 2; the cold
  ingot (0 C) shows no popup. The stars are U+2605 (vanilla's 7x7 bitmap star, on the baseline, with the shadow), the
  HUD stand-in for TFC's U+066D (ledger divergence); the ASCII `*` used before read as a faint checkered strip.
- Criterion 2: the level text and colour change 0.06-0.11 s after each `tfp debug heat Bot <t>` reply at both scales,
  the same as the vanilla `/title` control, with no stale or blank frame.
- TFC fidelity: Heat.java levels (DARK_RED 580-730 -> ChatFormatting.DARK_RED `#aa0000`, BRIGHT_RED 730-930 -> RED
  `#ff5555`); 900 C has 4 stars, 600 C none (HudClimateValuesTest).
- Layout: the popup is centred 70 GUI px above the bottom centre, above the HUD bars and clear of vanilla's item-name
  line; no missing-texture purple/black; the top-left date/climate panel and the bars are intact.

## Note for re-checks

Take the frame at least about 0.1 s after the RCON reply (or record and pick by frame time, as here): a frame grabbed
right at the reply shows the previous level for a vanilla title too, because the client has not drawn the packet yet.
`import` logs its return time, not its grab time.
