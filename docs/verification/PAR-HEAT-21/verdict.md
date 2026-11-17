# PAR-HEAT-21 verification

- Date: 2026-11-17
- Code under test: the repair commit that changes `TorchBlocks.renderYaw` (dead wall torch yaw = (180 - y) mod 360), on top
  of 379e510
- Server: Paper 1.21.11 headless (`spikes/client/server.sh paper 1.21.11`, `TFP_WORLD=1`, priming start, world deleted,
  second start), CraftEngine 26.9.2 self-host on 127.0.0.1; the server was restarted twice after the machine's memory limit
  killed it (world kept, `save-all` after the time skip)
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), 1280x720, GUI scale 2 and 3 (`MC_OPTIONS=guiScale:N`),
  `debug-profile.json` with `minecraft:light_levels`, `looking_at_block`, `player_position` and `game_version` in the F3
  overlay
- Who ran it: the item worker drove the client itself; this session has no subagent tool, so the PROTOCOL 5.3 / 6.1
  delegation to a cheap-model agent could not be done. Every frame below was judged here.

## Setup

Stone floor y 83 at (810..830, 2360..2380), a stone wall on z 2360 and a 1x3 stone pillar at (820, 84..86, 2368). Bot in
survival with thirst 100 and hunger 20, 8 `tfp:torch` and 2 sticks. One torch placed by right click on the floor at
(816, 84, 2366) and one on each face of the pillar at y 85 (facings north, south, east, west). Barrier blocks under the wall
torches so Bot can stand in the torch block for the F3 light reading. The weather stayed rainy (TFC climate) although
`weather clear` was set; it does not touch the torches.

## Results

| step / criterion | result | evidence |
|---|---|---|
| Lit floor and wall torches show the vanilla torch with flame | pass | `01-gui2-lit-floor`, `02-gui2-lit-wall`, `03-gui2-lit-wall-side` |
| Lit torches give light 14 (F3) | pass: standing in the floor torch block and in the north wall torch block, `Client Light: 15 (15 sky, 14 block)`; the targeted block of the wall one reads `minecraft:wall_torch`, `facing: north` | `04-gui2-lit-floor-f3`, `05-gui2-lit-wall-f3`, `06-gui3-lit-wall-f3` |
| Stick on the lit floor torch: one stick taken, a torch given | pass: sticks 2 -> 1, torches 3 -> 4 (inventory read over RCON) | - |
| `gamemode creative`, `tfp time add days 4` (June 1 -> June 5), tp 300 blocks away, 8 s, tp back, survival with needs reset | done | - |
| Both are dead after the chunk reload (torch_off look, no flame) | pass: floor and all four wall torches dead | `07-gui2-dead-floor`, `08-gui2-dead-wall`, `09-gui2-dead-wall-side`, `13-gui3-dead-wall-side` |
| The wall one still faces the wall (the repair) | pass: from the same camera the dead wall torch is exactly where the lit one was, bottom at the pillar face, top leaning out; all four facings | `00-gui2-wall-side-lit-vs-dead` (north and east facings, lit left, dead right), `19-gui2-south-west-facings-lit-vs-dead`, `20-gui2-east-facing-lit-vs-dead`, `21-gui2-vanilla-wall-torch-reference` (a real `minecraft:wall_torch[facing=south]` set by command) |
| Dead torches give no light (F3) | pass after a client relog: `0 block` in the dead floor and dead wall torch blocks. Before the relog the client still read 14 (`18-...-stale-before-relog`): the server's light is right, the client kept the light of the chunk sent before the swap. Follow-up PAR-HEAT-28 | `11-gui2-dead-floor-f3`, `12-gui2-dead-wall-f3`, `14-gui3-dead-floor-f3`, `15-gui3-dead-wall-f3`, `18-gui2-dead-floor-f3-stale-before-relog` |
| Held torch on the dead floor and dead wall torch relights each; the held torch is not placed | pass: both lit again, the wall one on the first click at pitch 3 (the outline is on the wall face where the model is now), held count stays 4 | `16-gui3-relit-side` |
| Fresh torch, then right click and sneak right click on it with a torch: no second torch | FAIL (re-run 2026-11-17 on c872b23): the normal click places nothing (count stays 2), the sneak click places a second torch in front of the lit one (count 2 -> 1). The first run's pass (count 4 -> 3 -> 3) is withdrawn: `TorchListener` has no sneak path, so that click most likely did not send the sneak. Fix needs `TorchListener` (outside files_owned): item blocked | `17-gui3-fresh-torch`; re-run frames `t3-no-second-torch-normal.webp`, `t3-no-second-torch-sneak.webp` in the session scratchpad `ui/PAR-HEAT-21/` |
| Shot list (lit floor, lit wall, dead floor, dead wall x GUI 2 and 3) | pass: all eight are in this folder. GUI 2: lit floor `01`, lit wall `02`, dead floor `07`, dead wall `08`. GUI 3: lit floor `22`, lit wall `23`, dead floor `24`, dead wall `25` (added 2026-11-17 from the same session's frames `gui3-{lit,dead}-{floor,wall}.webp`; HUD at scale 3, June 1 lit / June 5 dead). Lossless WebP of every frame in the session scratchpad `ui/PAR-HEAT-21/` | `01`, `02`, `07`, `08`, `22`-`25` |

## Side check: dead wall torch outline

The dead wall torch's outline is the pressed oak wall button (6 x 4 x 1 px on the wall face), smaller than TFC's
5 x 10 x 5 wall torch outline (visible in `08-gui2-dead-wall`). It stays: every dumped vanilla state with a closer outline
emits light, has a collision box (TFC's torches have none, and a box at the wall would block one-wide tunnels lined with
dead torches) or is placed by players in TFC (unlit redstone torches). `TorchCarrierTest.noFreeCarrierHasACloserDeadTorchOutline`
pins TFC's shapes and this claim; the ledger's "dead torch outlines" divergence row records it.

## Deferred checks (PROTOCOL 6.5, unblocker 2026-11-17)

- The client reading 0 block light at once after a chunk-load burnout, without a relog: deferred check tracked in
  PAR-HEAT-28 (`TorchService` / `TorchDevice` are outside this item's files). Here the server light is 0 and the client
  reads 0 after the chunk is sent again (`11`, `12`, `14`, `15`).
- A dead torch selection outline closer to TFC's than the pressed button: deferred check tracked in PAR-HEAT-29 (wider
  candidate dump: coral wall fans, lever, tripwire hook, signs, banners, ...). This item keeps the pressed button and the
  ledger's "dead torch outlines" divergence row, which now points at PAR-HEAT-29.

## Re-run 2026-11-17 (HEAD c872b23, Paper 1.21.11 headless, pack applied)

Pass: lit floor and wall with flame; relit dead floor (held count unchanged); stick on the lit floor torch (sticks 2 -> 1,
torches 1 -> 2); `tfp time add days 4` kills both; held torch on the dead wall torch relights it, nothing placed; all eight
required shots at GUI scale 2 and 3. Fail: the sneak click with a held torch on a lit torch places a second torch.
F3 block light was not captured in this run (the F3 profile showed no light level); the earlier readings above stand.

Verdict: FAIL on the sneak-click step (item blocked on `TorchListener`, see the item's first plan line); every other
criterion passes; the two deferred checks above are tracked in PAR-HEAT-28 and PAR-HEAT-29.
