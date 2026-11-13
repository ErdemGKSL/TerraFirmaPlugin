# PAR-CLIM-01 in-game check

- Date: 2026-11-13
- Tree: base 859c5ed plus the PAR-CLIM-01 change (the commit that adds this file)
- Server: Paper 1.21.11 (`spikes/client/server.sh`, TFC world), CraftEngine self-hosted pack; client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), bot `Bot`, default GUI scale
- Run by the main session (no subagent tool was available in this run)

## Steps

1. `difficulty peaceful`, a stone floor and a red bed (foot 819 150 -2784, head 820, facing east) next to Bot.
2. `tfp time set night` -> "moved 6834 calendar ticks forward, now June 1, 1000 19:12"; `time query daytime` = 13201 (= 19:12 + 18000, mod 24000); `gamerule advance_time` = true (the calendar runs).
3. `tfp time add hours 1` -> 20:25 (TFC's beds open at 8 pm; TFC's `set night` is 19:12). Screenshot `scene-before.png`, `hud-date-before.png`: night sky, HUD "June 1, 1000 (Early Summer)".
4. Bot right-clicks the bed (xdotool), sleeps (server log: `Bot has made the advancement [Sweet Dreams]`), and the night is skipped.
5. Screenshot `scene-after.png`, `hud-date-after.png`: dawn sky, HUD "June 2, 1000 (Early Summer)"; `time query daytime` = 23066, i.e. calendar 5:04 (5 am plus the seconds until the query), the world held to the calendar.
6. `gamerule advance_time false` from the console -> "The game rule minecraft:advance_time is controlled by the TFC calendar; use /tfp time instead.", the rule stays true.
7. `time add 1000` -> the world stays at the moved time (185 a second later), so the calendar moved with it rather than snapping the world back.

`tfp debug player Bot` shows no date at this commit, so the date was read from the HUD and from `time query daytime`.

## Criteria

| Criterion | Result |
|---|---|
| Sleeping through a night advances the date shown by the HUD by the same amount | PASS: June 1 20:25 -> June 2 05:00 (about 8 580 calendar ticks, to TFC's 5 am); HUD June 1 -> June 2; vanilla sky at dawn matching the calendar |
