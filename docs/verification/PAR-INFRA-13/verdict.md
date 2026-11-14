# PAR-INFRA-13 / PAR-INFRA-23: hitbox carrier in-game verification

- Date: 2026-11-14
- Jar built from `a45baaf` (the carrier tables are unchanged up to `0645f49`; `reports/hitbox-carriers-1.21.md` and `-26.md` give
  the same allocation for every class below)
- Servers: Paper 1.21.11-132 and Paper 26.3-244, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0, TFC world (`TFP_WORLD=1`), the S2 spike plugin on the 26.3 run (sound log)
- Clients: vanilla 1.21.11 and 26.3 under Xvfb (`spikes/client/session.sh`), 1280x720, GUI scale auto, pack applied on join
- Who ran it: the item worker ran the session itself (no subagent tool in that session) and judged every frame

## Method

- Test row: stone floor at y 99 from (1398, -1792) to (1440, -1776), air above, a smooth quartz wall at z -1786 behind the row
  for outline contrast, clear weather, `tfp time set noon` before each step. One block per class at (x, 100, -1785),
  x = 1402, 1405, ... 1432, placed with `/tfp debug place <state>` typed in the client's chat (the command needs a player
  sender) while Bot looks at a glass block in that spot.
- Client block state: the 1.21.9+ debug entry `minecraft:looking_at_block` set to *always on* (F3+F6 screen; the file
  `debug-profile.json` was copied to the 26.3 game dir), so every screenshot names the state the client holds for the
  targeted position. Outline: Bot at (x+0.5, 100, -1782.5), yaw 180, pitch 35.
- Stand height: Bot in survival is teleported to (x+0.5, 101.5, -1784.5) and falls onto the block; `data get entity Bot
  Pos[1]` after 1.5 s (`OnGround` 1). Player movement is client-authoritative, so the value is the client's collision.
- Break: survival, empty hand, `xdotool mousedown 1` for 6-7 s; frames at +1 s, release, +2 s; then `execute if block ... air`.
- Prediction: Bot holds the item in slot 1 and right-clicks once at pitch 22 (the top face); 2 s recorded at 20 fps with
  ffmpeg x11grab, the mean brightness of the block's top area per frame shows any flash.

## Results per class

| class (demand) | carrier (both families) | client state seen 1.21.11 / 26.3 | outline | stand height 1.21.11 / 26.3 (floor 100) | expected collision | break | verdict |
|---|---|---|---|---|---|---|---|
| charcoal forge `tfp:charcoal_forge[heat_level=0]` | `snow[layers=7]` model | snow layers 7 / snow layers 7 | 16x14x16, coincides with the heat_0 model (edge crop 8x) | 100.75 / 100.75 | 12 px | breaks in under 1 s; client shows air (stone below targeted), 7 charcoal drop; no white cube | outline, stand, break PASS; held snow FAIL (below) |
| bellows `tfp:bellows[facing=north]` | `dirt_path` vanilla | dirt_path / dirt_path | 16x15x16 | 100.9375 / 100.9375 | 15 px | crack overlay on the dirt-path look, bellows item drops, air after | PASS |
| firepit `tfp:firepit` | `daylight_detector[inverted=false,power=0]` vanilla | daylight_detector power 0 / same | 16x6x16 | 100.375 / 100.375 | 6 px | breaks, drops, air after | PASS |
| quern base `tfp:quern[has_handstone=false]` | `stonecutter[facing=east]` model | stonecutter facing east / same | 16x9x16 | 100.5625 / 100.5625 | 9 px | breaks, quern drops, air after | PASS |
| quern with handstone `tfp:quern[has_handstone=true]` | `snow[layers=6]` model | snow layers 6 / snow layers 6 | 16x12x16 box above the base model | 100.625 / 100.625 | 10 px | breaks, drops, air after | outline, stand, break PASS; held snow FAIL (below) |
| barrel upright unsealed `tfp:wood/barrel/oak[sealed=false]` | `cactus[age=10]` transparent + renderer | cactus age 10 / same | 14x16x14 | 100.9375 / 100.9375 | 15 px | breaks, barrel drops, air after; **no crack overlay** (the carrier draws nothing) | PASS, crack residual |
| barrel upright sealed `[sealed=true]` | `cactus[age=10]` transparent + renderer | cactus age 10 / same | 14x16x14 (same as unsealed, as the table says) | 100.9375 / 100.9375 | 15 px | as unsealed | PASS, crack residual |
| composter `tfp:composter[stage=0]` | `composter[level=0]` model | composter level 0 / same | vanilla composter (hollow) | 100.125 / 100.125 | floor 2 px (Bot fits inside) | breaks, composter drops, air after | PASS |
| scraping `tfp:scraping` | `heavy_weighted_pressure_plate[power=0]` transparent | - | - | - | - | - | not verifiable: the block removes itself at once without a scraped item (stone below targeted on both clients) |

Screenshots: `1.21.11-outline-forge.png`, `1.21.11-forge-outline-edge-8x.png`, `1.21.11-outline-bellows-firepit-quern.png`,
`1.21.11-outline-barrel-composter.png`, `1.21.11-outline-composter-scraping.png`, `26.3-outlines.png`, `26.3-client-states.png`
(the 26.3 BetterHud date panel overlaps the debug text; the states read as listed above), `1.21.11-break-*.png`,
`26.3-break-forge-bellows-firepit-barrel.png`, `1.21.11-barrel-no-crack-overlay.png`.

## Ghost-cube regression of 2026-10-23 (forge break)

Not reproduced on either family. After the survival break the client's targeted block is the stone below (the position is
air), the forge's 7 charcoal drop (PAR-INFRA-19 is done, so the drop is expected now), and the frames 0.3 s and 2 s after the
break show no white or full-height cube (`1.21.11-break-forge.png`, `26.3-break-forge-bellows-firepit-barrel.png`). No packet
trace was needed: the server and the client agree on air at once. The earlier white cube came from a stale client state that
this build no longer leaves; the regression itest proposed in the item stays a proposal (not adopted: the visual check passes).

## Client prediction

- **Held vanilla snow layer on a snow-layer carrier: FAIL on 1.21.11 and 26.3.** Right-clicking the top face of the forge
  (`snow[layers=7]`) while holding `minecraft:snow` makes the client predict `snow[layers=8]`: for 1-3 frames (50-150 ms) the
  forge is drawn as a white full snow block with a full-cube outline, then the server's resend restores the forge
  (`1.21.11-held-snow-ghost-forge.png`, `26.3-held-snow-ghost-forge-quern.png`; 3 of 3 tries on 1.21.11, 2 of 2 on 26.3).
  On the quern with handstone (`snow[layers=6]`) the predicted `snow[layers=7]` is the forge's model carrier, so the quern
  flashes as a charcoal forge (`1.21.11-held-snow-quern-and-cobble-ghost.png`, `26.3-held-snow-ghost-forge-quern.png`).
  The PAR-INFRA-13 note "no ghost layer on the forge" came from a click on the side face, where vanilla does not stack.
  The server never changes the block. Not fixable from `unusable.json`: its rows only act on override classes, and the
  forge, quern and grill are ranked; every `snow[layers<8]` carrier (forge, quern handstone, grill, charcoal, bloom and ingot
  piles) has the same prediction. Follow-up: PAR-INFRA-51 (selector rule or override; ledger row added).
- Held block item (cobblestone) on the forge's top face: the client predicts the cobblestone above the forge for 2 frames
  and drops it when the server refuses the placement (the forge's use handler takes the click). Not a carrier fault (any CE
  device whose right-click is consumed server-side does this); recorded in PAR-INFRA-51 for a decision.
- Iron hoe on the bellows (`dirt_path`): no farmland flash on either client (40 frames), the server keeps the bellows; the
  till sound is the known ledger residual (no sound device in the headless client, not heard).
- Grill (`snow[layers=5]`): not loaded (`content/.../tfp/devices/grill.yml` is outside `configuration/`; PAR-HEAT-08); by the
  result above it will predict a sixth layer.

## Block sounds (deferred from PAR-INFRA-33)

26.3, firepit only (`26.3-firepit-sound-log.txt`, one client): every hit was rewritten to `minecraft:block.stone.hit` 0.25/0.5;
the break sent exactly one `minecraft:block.nether_wart.break` 1.0/0.8 to Bot, and the second copy for the breaker was paired
with a `[tfp-sound] DROP` line. PASS for the firepit on 26.3. Not done: the grill and pot (not loaded, PAR-HEAT-08/09), the
1.21.11 sound run and the second watching client; the log excerpt for those goes to `docs/verification/PAR-INFRA-33/` with
the grill and pot run.

## Classes not verifiable yet (no loaded CE block at this commit)

| class | carrier in the report | waits for |
|---|---|---|
| grill, pot | `snow[layers=5]`, `cactus[age=0]` | PAR-HEAT-08, PAR-HEAT-09 (move into `configuration/devices/`) |
| composter stages 1-8 (`composter[level=2/3]`, `stonecutter[facing=north]`, `farmland[moisture=0]`) | as listed | the composter loads, but a stage needs compost items (`/tfp debug place` with stage>0 is reset to the contents, stage 0); the first green/brown CE items (plants, PAR-WORLD-09 / food items) |
| scraping | `heavy_weighted_pressure_plate[power=0]` transparent | the scraping gesture with a soaked hide (removes itself when empty) |
| charcoal pile 1-8 | pressure plate, `snow[layers=n]` | PAR-PROC-37 |
| loose rocks, small ores, groundcover (SMALL, MEDIUM, FLAT, PIXEL_HIGH) | buttons, pressure plate, petals | PAR-WORLD-09, PAR-GATHER-01 |
| placed items (pressure-plate outline, quadrants, gesture negatives) | `heavy_weighted_pressure_plate[power=0]` | PAR-POT-01 |
| large vessel plain and glazed (heavy_core step, wall fallback) | `heavy_core[waterlogged=false]` | PAR-POT-06 |
| barrel on its side, barrel rack, powderkeg, nest box, bowls, crucible, tool rack, shelf | not allocated yet | their family items (barrel rack/side: barrel family; others: their POT/METAL/WOOD items) |
| seal toggle by gesture (barrel, powderkeg, large vessel) | - | the sealing interaction items; the debug-placed sealed and unsealed barrels already show the same 14x16x14 outline |

No `unusable.json` row was added: every class checked keeps its carrier for outline, collision and break; the one failed
check (held snow) is not an override class and goes to PAR-INFRA-51.

## Criteria

| done criterion | result |
|---|---|
| Screenshots show each pinned class's outline on 1.21.11 and 26.x matching the table | PASS for the 8 loaded classes above |
| Stand heights match the carrier collision | PASS (identical on both families) |
| No ghost prediction with a held snow layer | FAIL for `snow[layers<8]` carriers (forge, quern handstone); follow-up PAR-INFRA-51 |
| Classes not verifiable yet listed with the item that adds them | PASS (table above) |
| Firepit, grill, pot sounds on both families | firepit on 26.3 PASS; the rest pending with PAR-HEAT-08/09 |
