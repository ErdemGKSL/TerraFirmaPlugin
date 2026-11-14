# PAR-INFRA-13 / PAR-INFRA-23: hitbox carrier in-game verification

- Date: 2026-11-14, second run (the answer to the REJECT of 432d697). The first run of the same day is summarised at the end.
- Jar: `dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar` built from `432d697` (no code or content change since `a45baaf`;
  `reports/hitbox-carriers-1.21.md` and `-26.md` give the allocation below).
- Servers: fresh run dirs, Paper 1.21.11-132 and Paper 26.3-244, CraftEngine 26.9.2, BetterModel 3.5.1,
  BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0, TFC world (`TFP_WORLD=1`, priming start, world deleted, second start),
  the S2 spike plugin and `EXTRA_JVM=-Dtfp.blocksound.log=true` on both (sound log).
- Clients: vanilla 1.21.11 and 26.3 under Xvfb (`spikes/client/session.sh`), 1280x720, GUI scale auto, pack applied on
  join, `ALSOFT_DRIVERS=null`, subtitles on, the 1.21.9+ debug entry `minecraft:looking_at_block` always on (the
  `debug-profile.json` of the first run). `Bot` is the player under test; `Viewer` (a second client of the same version,
  `mcclient.py launch <mc> --name Viewer`) watches the sound step from 3 blocks away. On 26.3 the BetterHud date panel was
  turned off for Bot (`/hud turn off Bot`) so it no longer covers the debug text.
- Who ran it: the item worker drove the clients itself; this session had no subagent tool, so the PROTOCOL 5.3 / 6.1
  delegation to a cheap-model agent could not be done. Every frame below was judged here.

## Method

- Test row, built after Bot was teleported there and its chunks were loaded (the first run's 26.3 shots were taken away from
  the row after a watchdog restart): a sky platform at y 119 (stone floor) with air above, 1.21.11 at x 1176..1214,
  z 208..226 (spawn 1180, 65, 219), 26.3 at x 1468..1506, z 328..346 (spawn 1472, 75, 336). A smooth quartz row directly
  behind the test blocks (z 214 / 334) and **one smooth quartz block directly east of the forge** (1181 120 215 /
  1473 120 335), so the forge's 14 px top meets a 16 px light block at its side and its back edge is seen against quartz.
  One block per class at y 120, x = 1180, 1183, ... 1201 (26.3: 1472, 1475, ... 1493), each placed with
  `/tfp debug place <state>` typed in Bot's chat while Bot looks at a glass block in that spot. `tfp time set noon`, clear.
- Outline: Bot 2 blocks south of the block (x+0.5, 120, z+2.5), yaw 180, pitch 35, so the crosshair sits on the block's
  front face (every shot shows the crosshair and the targeted state). Two screenshots of the same frame position: one as
  is, one with F1 (hides the HUD and the selection outline). The pixels that differ are the outline (plus HUD, hand and
  chat, masked). Each `<ver>-outline-<class>.png` shows: top left the targeted shot, top right the F1 shot with the outline
  pixels painted red, below them the 1:1 centre crops, and (26.3) the debug lines at 3x.
- Forge height check (camera at eye y 121.62, pitch 35, vertical FOV 70, 1280x720): the outline's back top edge is
  predicted at screen row 189 for a 14 px box and row 162 for a 16 px box; its bottom front edge at row 471.
- Stand height: Bot in survival is teleported to (x+0.5, 121.6, z+0.5) above the block; `data get entity Bot Pos[1]` and
  `OnGround` 1.6 s later. Player movement is client-authoritative, so the value is the client's collision.
- Break: survival, empty hand, Bot 2 blocks south at pitch 35, `xdotool mousedown 1` until the server reports air (polled
  every 0.1 s), release; frames 0.3 s and 2 s after; then the item entities within 2.5 blocks and Bot's inventory.
- Prediction: Bot holds the item in a hotbar slot, looks at the top face (pitch 22 from 2 blocks, crosshair on the top),
  right-clicks once while ffmpeg records 2.5 s at 20 fps (`x11grab`); the mean colour of the block's area per frame shows any
  flash. Then an **empty-hand** frame on the forge top (debug: targeted state) and one on the floor block in front of the
  forge, plus `execute if block` above and in front of the forge on the server.

## Results per class (both families)

| class (demand) | carrier | client state (debug) 1.21.11 / 26.3 | outline (diff) | stand 1.21.11 / 26.3 (floor 120) | expected collision | break | verdict |
|---|---|---|---|---|---|---|---|
| charcoal forge `tfp:charcoal_forge[heat_level=0]` | `snow[layers=7]` model | snow layers 7 / snow layers 7 | 16x14x16 box: back top edge at row 188 on both (14 px predicts 189, 16 px 162), bottom at 471 (predicted 471); against the quartz the edge row darkens from (158,153,148) to (95,92,89) | 120.75 / 120.75 | 12 px | 0.3-0.4 s; air, 7 `minecraft:charcoal` on the ground, no white or stone cube at 0.3 s or 2 s | PASS |
| bellows `tfp:bellows[facing=north]` | `dirt_path` vanilla | dirt_path / dirt_path | 16x15x16 (1 px under the 16 px quartz beside it) | 120.9375 / 120.9375 | 15 px | 4.5-4.7 s by hand; air, `tfp:bellows` item | PASS (26.3 mesh lag below) |
| firepit `tfp:firepit` | `daylight_detector[inverted=false,power=0]` vanilla | daylight_detector power 0 / same | 16x6x16 | 120.375 / 120.375 | 6 px | 0.7 s; air, the CE drop picked up by Bot (1.21.11: `tfp:powder/wood_ash`) | PASS |
| quern base `tfp:quern[has_handstone=false]` | `stonecutter[facing=east]` model | stonecutter facing east / same | 16x9x16 (the box under the base model's rim) | 120.5625 / 120.5625 | 9 px | 0.9 s; air, `tfp:quern` | PASS |
| quern with handstone `tfp:quern[has_handstone=true]` | `snow[layers=6]` model | snow layers 6 / same | 16x12x16 box above the base model | 120.625 / 120.625 | 10 px | 0.9 s; air, `tfp:quern` | PASS (held snow: see Prediction) |
| barrel upright unsealed `tfp:wood/barrel/oak[sealed=false]` | `cactus[age=10]` transparent + renderer | cactus age 10 / same | 14x16x14 | 120.9375 / 120.9375 | 15 px (cactus 14x15x14) | 3.9 s; air, `tfp:wood/barrel/oak` | PASS |
| barrel upright sealed `[sealed=true]` | `cactus[age=10]` transparent + renderer | cactus age 10 / same | 14x16x14, the same as unsealed | 120.9375 / 120.9375 | 15 px | 3.9 s; air, `tfp:wood/barrel/oak` | PASS |
| composter `tfp:composter[stage=0]` | `composter[level=0]` model | composter level 0 / same | vanilla composter shape with the inner rim | 120.125 / 120.125 | floor 2 px | 1.0 s; air, `tfp:composter` | PASS |

Files: `1.21.11-outline-<class>.png` and `26.3-outline-<class>.png` (forge, bellows, firepit, quern, quern-handstone,
barrel-unsealed, barrel-sealed, composter), `1.21.11-forge-edge-8x.png` and `26.3-forge-edge-8x.png` (8x crop of the
forge's back top edge against the quartz: left targeted, right F1; the one-pixel outline row is the only difference),
`1.21.11-break-0.3s-2s.png` and `26.3-break-0.3s-2s.png` (pairs of frames per class; the 1.21.11 firepit break ran in the
sound step), `26.3-break-mesh-lag-bellows-vs-vanilla.png`.

Drops: every break left only the CE drop on the ground or in Bot's inventory (charcoal x7, bellows, quern, barrel x2,
composter, wood ash); a scan of all item entities over the test row found no `minecraft:snow`, `dirt_path`, `cactus`,
`daylight_detector`, `stonecutter` or vanilla `composter` item.

26.3 bellows mesh lag: on 26.3 the bellows' `dirt_path` look stays drawn for about 0.3 s after the break while the client
already targets the stone below (its own state is air), then disappears (twice, frames 100 ms apart). A vanilla
`dirt_path` broken the same way on 26.3 lingers just as long (`26.3-break-mesh-lag-bellows-vs-vanilla.png`: bellows at
+0.1 s, bellows at +0.5 s, vanilla dirt path at +0.1 s), and 1.21.11 shows neither. It is the 26.3 client's section
rebuild, not a carrier fault and not the 2026-10-23 ghost cube.

No-collision classes: none is loaded at this commit (the scraping removes itself at once without a scraped item; loose
rocks, small ores, groundcover and placed items have no CE block yet), so the walk-through check has nothing to run on;
it moved with those classes to PAR-INFRA-53.

## Ghost-cube regression of 2026-10-23 (forge break)

Not reproduced on either family, in this run or the first: after the survival break the client targets the stone below
(the position is air), the 7 charcoal drop, and the frames 0.3 s and 2 s after the break show no white or full-height
cube. No packet trace was taken, so the cause of the 2026-10-23 cube is not established; it is recorded as not
reproduced. The regression itest proposed in the item is closed (not built).

## Client prediction

- **Held vanilla snow layer on the top face of a `snow[layers<8]` carrier: FAIL on 1.21.11 and 26.3.**
  - Forge (`snow[layers=7]`): the crosshair is on the forge's top face and the client names `snow layers 7` before the
    click. One right-click with `minecraft:snow` makes the client draw the forge as a white full snow block with a full-cube
    outline for 4 frames (200 ms) on 1.21.11 and 2 frames (100 ms) on 26.3, then the forge returns.
  - After the click, with an empty hand, the client again names the forge `snow layers 7` and the floor block in front of
    the forge `stone` (no layer on the floor). On the server, the block above the forge and the one in front of it are air,
    and Bot still holds 16 snow. So the white block is the client's predicted `snow[layers=8]` at the forge's own position.
    It is not a placed layer and not a sixth or eighth layer anywhere else.
  - The white slab at the bottom of the flash frame is the held snow item in the swing. It is gone in the empty-hand frame.
  - Quern with handstone (`snow[layers=6]`): one frame (50 ms) drawn as the charcoal forge on both families (the predicted
    `snow[layers=7]` is the forge's model carrier).
  - Frames: `1.21.11-prediction.png` and `26.3-prediction.png`. Top row: aim (crosshair and debug), the flash, restored,
    then the empty hand on the forge. Bottom row: the empty hand on the floor, the quern flash and the hoe on the bellows.
  - The grill (`snow[layers=5]`) is not loaded (PAR-HEAT-08). Its check and the fix belong to PAR-INFRA-51, which now owns
    this criterion.
- Held block item (cobblestone) on the forge's top face (first run, `1.21.11-held-snow-quern-and-cobble-ghost.png`): the
  client predicts the cobblestone above the forge for 2 frames and drops it when the forge's use handler takes the click.
  The decision is recorded in PAR-INFRA-51.
- **Iron hoe on the bellows (`dirt_path`)**: on 1.21.11 there is no flash in 50 frames, only the client's till sound
  (subtitle "Hoe tills"). **On 26.3 the bellows flashes as the composter for 2 frames (100 ms)**. The 26.x client predicts
  the till to `farmland[moisture=0]`, which is the model carrier of composter stages 6-7. The server keeps the bellows and
  no farmland is placed. This is the same class of fault as the held-snow case: a model-mode carrier is the predicted result
  of another carrier. It is added to PAR-INFRA-51 and to the ledger prediction row.

## Block sounds (deferred from PAR-INFRA-33)

Firepit on both families, with Bot breaking it and Viewer watching. Excerpts:
`docs/verification/PAR-INFRA-33/firepit-1.21.11-sound-log.txt` and `firepit-26.3-sound-log.txt`.

- Every hit while mining went to Bot as `minecraft:block.stone.hit` 0.25/0.5. Each line is paired with a
  `[tfp-sound] SET 0.5/0.5 -> 0.25/0.5` line.
- The break sent exactly one `minecraft:block.nether_wart.break` 1.0/0.8 to Bot and one to Viewer. Bot's second copy is
  marked `cancelled=true` and paired with a `[tfp-sound] DROP` line.

PASS on 1.21.11 and 26.3. The grill and pot half moved to PAR-INFRA-52 (deps PAR-HEAT-08 and PAR-HEAT-09, which load them),
together with the step and the criterion.

## Barrel `facing=north`

`/tfp debug place tfp:wood/barrel/oak[facing=north]` answers "CraftEngine refused to place" on both families. This is
not a CraftEngine property error. The generated barrel block (`configuration/barrel/barrel.yml`) declares only the
`sealed` property, because PAR-PROC-32 emitted the upright barrel only. A state with an undeclared property does not
exist, so CraftEngine refuses it. The barrel on its side, its rack and the tap are PAR-PROC-38 (open). Its carrier is
checked with the rest of the containers in PAR-INFRA-53.

## Plugin defect seen in the run (not a carrier)

On 26.3, a vanilla `/time set` makes `CalendarModule.onTimeSkip` (CalendarModule.java:213) throw `NoSuchMethodError` on
`TimeSkipEvent.getSkipReason()` (`26.3-timeskip-nosuchmethod.txt`). In the 26.3 API, `getSkipReason()` moved to the new
superclass `ClockTimeSkipEvent` and returns `ClockTimeSkipEvent$SkipReason`. Our `tfp time set` is not affected. The
file is outside this item, so the fix is a new item: PAR-CLIM-22.

## Classes not verifiable yet (no loaded CE block at this commit)

Their in-game steps (outline, stand, walk-through, break, containers, placed items, gesture negatives, seal toggles, the
heavy_core step) moved to PAR-INFRA-53. It depends on the items below and not on PAR-INFRA-23.

| class | carrier in the report | waits for |
|---|---|---|
| grill, pot | `snow[layers=5]`, `cactus[age=0]` | PAR-HEAT-08, PAR-HEAT-09 (their sounds: PAR-INFRA-52; the grill's held-snow check: PAR-INFRA-51) |
| composter stages 1-8 (`composter[level=2/3]`, `stonecutter[facing=north]`, `farmland[moisture=0]`) | as listed | nothing (the composter is loaded, PAR-PROC-34): `/tfp debug place` with stage>0 is reset to the contents, so the stages are reached by filling the bin as PAR-PROC-34's check did (wheat or grass as greens, sticks or straw as browns); not run here, first step of PAR-INFRA-53 |
| scraping | `heavy_weighted_pressure_plate[power=0]` transparent | the scraping gesture with a soaked hide (it removes itself when empty) |
| charcoal pile 1-8 | pressure plate, `snow[layers=n]` | PAR-PROC-37 |
| loose rocks, small ores, groundcover (SMALL, MEDIUM, FLAT, PIXEL_HIGH) | buttons, pressure plate, petals | PAR-WORLD-09, PAR-GATHER-01 |
| placed items (pressure-plate outline, quadrants, gesture negatives) | `heavy_weighted_pressure_plate[power=0]` | PAR-POT-01 |
| large vessel, plain and glazed (heavy_core step, wall fallback) | `heavy_core[waterlogged=false]` | PAR-POT-06 |
| barrel on its side, barrel rack | `snow[layers=6]`, leaves pool, iron trapdoor | PAR-PROC-38 |
| powderkeg | cactus, composter | PAR-PROC-18 |
| nest box | `daylight_detector` | PAR-FAUNA-07 |
| wooden and ceramic bowl blocks | iron trapdoor (bottom) | PAR-POT-09 |
| crucible | cactus | PAR-METAL-12 |
| tool rack, shelf | open iron trapdoor, iron trapdoor (top) | PAR-DECO-05 |
| seal toggle by gesture (barrel, powderkeg, large vessel) | - | barrel: nothing (PAR-PROC-08 is done; the toggle runs in PAR-INFRA-53), powderkeg PAR-PROC-18, large vessel PAR-POT-06. The debug-placed sealed and unsealed barrels already show the same 14x16x14 outline |

No `unusable.json` row was added. Every class checked keeps its carrier for outline, collision and break. The failed
prediction checks are not override classes and are owned by PAR-INFRA-51.

## Criteria

| done criterion | result |
|---|---|
| Outline of each loaded class on 1.21.11 and 26.x matches the table | PASS for the 8 loaded classes on both families (per-class shots, the forge with the quartz edge and the 14 px height check) |
| Stand heights match the carrier collision | PASS (identical on both families) |
| No ghost prediction with a held snow layer | FAIL for `snow[layers<8]` carriers (forge, quern handstone). The fix and the recheck are tracked in PAR-INFRA-51, which owns this criterion (PROTOCOL 6.5). The 26.3 hoe-on-bellows composter flash went there too |
| Classes not verifiable yet are listed with the item that adds them | PASS (table above; their steps are in PAR-INFRA-53) |
| Firepit, grill and pot sounds on both families, excerpt in docs/verification/PAR-INFRA-33/ | Firepit PASS on 1.21.11 and 26.3 with a second viewer. The grill and pot are tracked in PAR-INFRA-52 |

## First run (same day, kept for the record)

The first run (jar from `a45baaf`) found the same client states, stand heights and drops on both families. It also
checked these, which are still fine:

- the 1.21.11 quern, barrel and composter outlines;
- the ghost-cube regression on both families;
- the held-snow flash;
- the barrel's missing crack overlay (`1.21.11-barrel-no-crack-overlay.png`, ledger row).

Its forge outline evidence was rejected: no light block was beside the forge, so the black outline did not show on the
black model. All its 26.3 outline shots were taken away from the test row after a watchdog restart. This run replaces
those files.
