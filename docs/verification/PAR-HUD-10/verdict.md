# PAR-HUD-10: held temperature popup shows TFC's level text: verdict: pass (recapture)

- Date: 2026-11-07. Tree `8dce508` (the item's change; this review fix touches only evidence, the item file and STATUS),
  content regenerated with `:tools:datagen:generateContent`, then `:dist:shadowJar` in a separate Gradle run.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. A new run folder: priming start,
  world deleted, second start (`spikes/client/server.sh`, vanilla terrain, which a HUD check does not need). Client:
  vanilla 1.21.11 under Xvfb 1280x720 (`spikes/client/session.sh`), one client per GUI scale (`MC_OPTIONS=guiScale:2`,
  then a new client with `guiScale:3`), never both at once; the pack applied 3 s after each join.
- Server log: no error from our plugin (only the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).
- This replaces the first capture of `8dce508`. Its `g2-900-bright-red.png` and `g2-900-text-6x.png` show `Dark Red****`
  in `#aa0000`, not what the old verdict said. That GUI 2 frame was taken late (700-730 C is reached about 30 s after
  900 C). The reviewers' webp set was taken with commands run in parallel, a death and respawn in the middle, and no
  command output per shot, so no file can be tied to a server state. All old crops are replaced or removed.

## How the recapture was run

Strictly sequential, one command at a time, from one script per scale. Every RCON command and its reply are logged with
wall-clock times (`g2-rcon.log`, `g3-rcon.log`). The screen was recorded continuously at 20 fps for the whole sequence
(`ffmpeg -f x11grab -copyts`, so each frame carries its wall-clock time), instead of single `import` shots. Those take
0.8-1.1 s each and could not meet the 0.5 s bound.

1. Scene, the same at both scales: `difficulty peaceful`, `gamerule advance_time false`, `weather clear`,
   `time set noon`, a 17x17 stone floor at y 99 with air above, then `gamemode survival Bot`, saturation,
   `tp Bot 0 100 0 0 30`, `clear Bot`, `ce item give Bot tfp:metal/ingot/copper` (slot 1, selected).
2. 20 s wait, so the chat and the offline-mode chat verification toast are gone before the first frame. The full-screen
   frames (`g*-900-full-screen.png`) show no toast and no chat.
3. For t = 900, 600, 0: `tfp debug heat Bot <t>` (reply `set: <t> C at tick N`), then after 1 s and 3 s
   `tfp debug heat Bot` (reply `stored: ... now tick M`, the temperature now and the level).
4. Cooling run: `tfp debug heat Bot 900`, then a query every 2 s for 12 s, then `tfp debug heat Bot 0`.

`g2-frames.txt` and `g3-frames.txt` hold the timeline: each RCON reply and every frame where the popup changes, with the
exact colour and pixel count in the band above the hotbar. Each evidence frame is the first one at least 0.45 s after its
command's reply.

## Evidence (1x crops of the popup, bars and hotbar; text crops at 3x)

| file | step (reply, tick) | frame | content |
|---|---|---|---|
| `g2-900-bright-red.png`, `g2-900-bright-red-text-3x.png` | `set: 900.0 C at tick 480` | +0.456 s | `Bright Red****` in `#ff5555` |
| `g2-600-dark-red.png`, `g2-600-dark-red-text-3x.png` | `set: 600.0 C at tick 540` | +0.468 s | `Dark Red` in `#aa0000`, no star |
| `g2-0-hidden.png` | `set: 0.0 C at tick 620` | +0.450 s | no popup |
| `g2-900-after-3s.png` | `set: 900.0 C at tick 680` | +3.030 s | `Bright Red***` (below 890 C) |
| `g3-900-bright-red.png`, `g3-900-bright-red-text-3x.png` | `set: 900.0 C at tick 1480` | +0.495 s | `Bright Red****` in `#ff5555` |
| `g3-600-dark-red.png`, `g3-600-dark-red-text-3x.png` | `set: 600.0 C at tick 1540` | +0.458 s | `Dark Red` in `#aa0000`, no star |
| `g3-0-hidden.png` | `set: 0.0 C at tick 1600` | +0.466 s | no popup |
| `g3-900-after-3s.png` | `set: 900.0 C at tick 1660` | +3.028 s | `Bright Red***` |
| `g2-900-full-screen.png`, `g3-900-full-screen.png` | the 900 frames above, whole screen (128-colour PNG) | | no toast, no chat, popup centred |

TFC's star is drawn as ASCII `*`, which the vanilla font draws as a small x-shaped glyph. This is the ledger divergence
for the HUD only; tooltips keep U+066D.

## Checks

| criterion | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| 900 C copper ingot shows `Bright Red` with four stars in RED (`#ff5555`) | pass | pass | 900 crops; only `#ff5555` in the band (576 px at GUI 2, 1296 px at GUI 3) |
| Legible at GUI scale 2 (black shadow on grey stone) | pass | pass | `g2-900-bright-red-text-3x.png` |
| 600 C reads `Dark Red` in DARK_RED (`#aa0000`) with no star and no stale `Bright Red` | pass | pass | 600 crops; from the first changed frame on only `#aa0000` (408 px / 918 px) |
| A cold ingot (0 C) shows no popup | pass | pass | 0 crops; from the first changed frame to the end of the step no popup colour in the band |
| The popup changes right after `tfp debug heat Bot <t>` with no visible lag | pass | pass | first changed frame after the reply: GUI 2 +0.456 / +0.318 / +0.350 / +0.280 s (900, 600, 0, 900 again), GUI 3 +0.295 / +0.358 / +0.266 / +0.428 s; no frame shows a stale level after the change |
| Clear of the bars and centred | pass | pass | all crops and the full-screen frames |

## Cooling, so star loss is not mistaken for lag

TFC's `HeatCapability.adjustTemp` cools by `ticks * 0.8 / capacity`. The copper ingot's `item_heat` capacity is 2.857143,
which gives 0.28 C per tick, about 5.6 C/s. Bright Red is 730-930 C, so the stars sit at 770, 810, 850 and 890 C. From
900 C the fourth star goes at 890 C (36 ticks, about 1.8 s) and the third at 850 C (about 8.9 s). Dark Red with four
stars (above 700 C) is reached only after about 30 s.

Measured in the recording:
- The fourth star went 1.76 / 1.78 s (GUI 2) and 1.85 / 1.33 s (GUI 3) after a set.
- The third star went 8.73 s (GUI 2) and 8.48 s (GUI 3) into the cooling run.
- The server's own readings agree, for example `stored: 900.0 C at tick 680, now tick 720, temperature now: 888.8 C`.

The steps come in whole seconds because the calendar's player ticks advance once per second (`CalendarService`, ledger
Calendar row). That causes the spread of up to about 0.5 s around the 1.8 s mark. It is not a delay in the popup.

## Earlier reports explained

- Heat 0 showing `Dark Red`, or 600 showing nothing: not reproduced. Here 0 hides the popup within 0.27-0.35 s and 600
  shows `Dark Red` within 0.32-0.36 s, at both scales.
- `Faint Red***` after a 900 retry: about 320 C below 900, so about a minute of TFC cooling. That matches a frame taken
  late, not lag.
