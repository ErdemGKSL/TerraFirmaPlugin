# PAR-HUD-16: HUD season follows the viewer's hemisphere: verdict: pass

- Date: 2026-11-13. Tree `d5eac3d` plus the PAR-HUD-16 change (uncommitted at the run, the same code as the PAR-HUD-16
  commit), jar from `:dist:shadowJar` with the shared generated content (`tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, priming start, world deleted, second start),
  CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb 1280x720
  (`spikes/client/session.sh`, `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker). Log: no error from our plugin
  (only the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).

## Steps

RCON: `tfp time set noon`, `weather clear`, `tfp time add months 3` (September), stone platforms at y 199 around
(0, 0) and (0, 20000) with both areas force-loaded (a first try without them got Bot kicked for flying). The server runs
TFC's default 8-day months, so day 30 does not exist: the frames show September 8 (after a detour through December, which
already showed "Early Winter" at z 0 and "Early Summer" at z 20000). Per GUI scale: `tp Bot 0 200 0`, wait 5 s, frame;
`tp Bot 0 200 20000`, wait 5 s, frame; `tp Bot 0 200 0`, wait 5 s, frame.

With the world's hemisphere scale 20 000 (`Solar.inNorthernHemisphere`), z 0 is northern and z 20 000 southern (the band
10 000..50 000).

## Crops (top-left plate; GUI 2 at 3x, GUI 3 at 2x)

| file | position | plate |
|---|---|---|
| `1-north-g2.png` | z 0 | September 8, 1001  (Early Autumn) |
| `2-south-g2.png` | z 20000 | September 8, 1001  (Early Spring) |
| `3-back-g2.png` | z 0 again | September 8, 1001  (Early Autumn) |
| `1-north-g3.png` | z 0 | September 8, 1001  (Early Autumn) |
| `2-south-g3.png` | z 20000 | September 8, 1001  (Early Spring) |
| `3-back-g3.png` | z 0 again | September 8, 1001  (Early Autumn) |

## Checks

| criterion | GUI 2 | GUI 3 |
|---|---|---|
| North: September shows "(Early Autumn)" fully on the plate | pass | pass |
| South: September shows "(Early Spring)" within 5 s of the teleport, fully on the plate | pass | pass |
| Going back north restores "(Early Autumn)" | pass | pass |

The criterion's text names day 30, which this server's 8-day months cannot reach; the line width with a two-digit day was
measured for the plate in PAR-HUD-15 (179 px for "September 30, 1000  (Early Autumn)" inside the 189 px plate), and the
southern name is shorter than the northern one, so it fits wherever the northern one does.
