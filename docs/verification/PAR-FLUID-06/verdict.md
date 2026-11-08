# PAR-FLUID-06 in-game check: river current on a boat and a dropped item

- Date: 2026-11-08
- Code: branch HEAD `efa7e56` (PAR-FLUID-03's `core/worldwater` and `paper/worldwater` unchanged since), jar built from this
  worktree (`:dist:shadowJar`, shared generated content).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents 2.14.0, TFP world generator
  (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted). Log: `Feature modules: ... worldwater, bucket, ...`; no
  errors from our plugin. The server was killed twice by the machine's OOM killer (other agents' servers); it was
  restarted on the same world each time and the steps resumed.
- Client: vanilla 1.21.11 under Xvfb, 1280 x 720, GUI scale 2 (`spikes/client/session.sh`), pack applied on join, started
  with `MC_OPTIONS="entityDistanceScaling:5.0"`: at the default 1.0 a dropped item (0.25 block box) is only rendered within
  16 blocks, so the stick was invisible from a camera high enough to hold three frames.
- Run and judged by the worker session itself (no subagent tool was available in this session).

## Steps

1. River found by teleporting Bot along z = 1472 and asking `/tfp debug water Bot` (typed by Bot in chat; the reply is
   asynchronous and does not come back over RCON): `Bot at 1456 87 1472: river water, river flow sww, biome river`.
   Water column at (1468, 1472): y 55..63, surface block 63.
2. Flow along the test stretch (Bot in the water): (1478, 1470) `ssw`, (1475, 1477) `ssw`, (1471, 1483) `sww`,
   (1468, 1472) `sww`, (1460, 1490) `sww`. ssw / sww = towards +z and -x.
3. Bot in spectator at (1462.5, 95, 1477.5) looking straight down (F1, HUD hidden): screen up = +z (south),
   screen right = -x (west), about 13.6 px per block at the water surface. `summon minecraft:oak_boat 1474.5 63.6 1475.5`
   and a stick item at (1478.5, 63.95, 1470.5) (both `Glowing:1b` so they read through the water; the stick has a pickup
   delay), then 3 frames 2 s apart with the positions read over RCON just before each frame:

| frame | boat (x, z) | stick (x, y, z) | file |
|---|---|---|---|
| t = 0 s | 1474.50, 1475.50 | 1478.50, 62.60, 1470.50 | `01-frame-t0s.png` (boat at 173,328; stick at 123,395) |
| t = 2 s | 1469.34, 1479.12 | 1471.63, 60.13, 1483.61 | `02-frame-t2s.png` (boat 232,283; stick 225,220) |
| t = 4 s | 1462.36, 1482.03 | 1450.28, 60.59, 1496.71 | `03-frame-t4s.png` (boat 329,243; stick 488,59) |

   Final motion: boat (-0.1164, 0, +0.0482) = 0.126 blocks/tick; stick (-0.424, +0.019, +0.096) = 0.43 blocks/tick.
   Crops: 640 x 460 at native scale (x 300..940, y 60..520 of the 1280 x 720 frame).
4. Player: Bot in survival with water breathing, teleported into the river at (1470.5, 62.5, 1474.5), no input; position
   every 2 s: x/z stayed 1470.5 / 1474.5 exactly, y 61.60 -> 60.32 -> 59.05 (vanilla sinking, motion y -0.025, x/z 0)
   (`04-player-in-river-sinks-straight-down.png`, 640 x 360).

## Verdict

| criterion | result |
|---|---|
| The boat moves with the reported flow across the 3 frames | pass: up and to the right in every frame (+z and -x, the `ssw`/`sww` flows), 6.4 and 7.6 blocks per 2 s step |
| An empty boat drifts at up to about 0.13 blocks per tick | pass: 0.126 blocks/tick at the end, as `RiverCurrentTrackerTest` predicts |
| The stick moves with the reported flow | pass: mostly south while in the `ssw` stretch (frame 1 -> 2: dx -6.9, dz +13.1), then south-west in the `sww` stretch (frame 2 -> 3: dx -21.3, dz +13.1). It sinks about 3 blocks on entry and rises slowly, as a vanilla item does. Its speed (about 0.43 blocks/tick) is vanilla's: the 0.014 push per tick against an item's 0.98 x 0.99 water drag settles near 0.47; TFC's river water uses the same vanilla item physics, so this is not a divergence |
| A player swimming in the river is not pushed by the plugin | pass: x/z unchanged over 4 s |
| Tests green | pass: `:core:test :paper:test :tools:datagen:test` (no code changed in this item) |

Observation (not part of this item): before the first boat was summoned, a `kill @e[type=item]` removed 192 item entities
in the loaded chunks, at least one of them a lily pad item drifting in the river; the river carries many lily pads. Whether worldgen or a
neighbour rule pops lily pads on river water was not investigated here.
