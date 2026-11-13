# PAR-INFRA-41 verification: ore overlay speck brightness per face

Date 2026-11-13. Jar built from this item's working tree (the commit that adds this file). Paper 1.21.11 with the 1.21.11
client, then Paper 26.3 with the 26.3 client, headless (`docs/testing/headless.md`), CraftEngine 26.9.2, PacketEvents
2.14.0, render distance 8, 1280x720. Each version ran on a fresh TFC world (priming start, world wiped, second start). The
runs were made from the main session (this session had no subagent tool to delegate them); every judgement below was made
by looking at the frames in this folder.

Scenes (both versions; built with `tfp debug oreoverlay place`, every reply read the cell back, `show` listed the faces
"sent to Bot"): a 3x3 of `tfp:rock/raw/granite` with `tfp:ore/normal_native_gold/granite` in the middle, one per face:
ceiling y 200 at x 1660-1662 z 1100-1102 (down), wall z 1110 (north), wall x 1670 (east), floor y 190 at x 1680-1682
(up). Bot in spectator, F1, eye 2.5 blocks from the face centre on the face normal (`tp` yaw/pitch 0/-90, 0/0, 90/0,
0/90), so the ore face is the screen square x 549-731, y 269-451. Noon = `time set 6000`, midnight = `time set 18000`
(daylight cycle off, clear weather). Light row: a light source 2 blocks from the open cell, at midnight: torches on
1.21.11 for down/north/east; TFC torches burn out (the 1.21.11 up-face torch and later ones gave no light), so the up
face on 1.21.11 (a second floor at y 195, x 1690-1692) and every face on 26.3 used glowstone.

Measurement (`ratio.py`, kept outside the repo): sample the 16x16 texel centres of the face square, take TFC's
`normal_native_gold.png` alpha as the speck mask in the best of the 8 rotations/flips (side faces matched unrotated, down
and up rotated 180 degrees, as vanilla lays the texture for that view), and compare the mean red of speck texels over rock
texels with the same number on TFC's granite + overlay composite: **1.391**. "game/TFC" is the in-game ratio over 1.391.

| Face | 1.21.11 noon | midnight | light | 26.3 noon | midnight | light |
|---|---|---|---|---|---|---|
| down | 1.298 (0.93) | 1.287 (0.93) | 1.439 (1.03) | 1.292 (0.93) | 1.293 (0.93) | 1.443 (1.04) |
| north | 1.386 (1.00) | 1.386 (1.00) | 1.438 (1.03) | 1.385 (1.00) | 1.386 (1.00) | 1.447 (1.04) |
| east | 1.379 (0.99) | 1.368 (0.98) | 1.432 (1.03) | 1.379 (0.99) | 1.368 (0.98) | 1.434 (1.03) |
| up | 1.386 (1.00) | 1.387 (1.00) | 1.436 (1.03) | 1.387 (1.00) | 1.387 (1.00) | 1.443 (1.04) |

Before (PAR-INFRA-37): down 0.38 of TFC, north 1.03/1.39 = 0.74. The tilt sign is right on both clients (a wrong sign
would have darkened the side faces below the old 0.74). Frames: `1.21.11-faces-noon-midnight-light.png` and
`26.3-faces-noon-midnight-light.png` (rows noon / midnight / light; columns down, north, east, up, TFC's composite): the
specks read gold on every face, as in TFC's column, and follow the rock at night and in block light.

| # | Criterion | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Speck/rock ratio within 10% of TFC's on the down, a side and the up face, at noon, midnight and with a light | **pass**: 0.93-1.03 | **pass**: 0.93-1.04 |
| 2 | Cracks still drawn over the specks (tilted crack twin; the down face is mirrored) | pass: hand dig, frame at 10 s, the crack lines cross the gold texels on the north and down faces (`1.21.11-hand-dig-crack-north.png`, `-down.png`) | pass (`26.3-hand-dig-crack-north.png`, `-down.png`) |
| 3 | Natural exposure (`show` on a generated vein exposed by terrain) | not found: `stats` at four areas (0,0), (3000,-2000), (-2500,2500), (1500,4000) gave tables with 0 to 50 887 ores and 0 exposed faces; this build has no caves (M2-GEN-09), see PAR-INFRA-37's verdict row 7. Not a criterion of this item | not run |
| 4 | `stats` label | "viewer chunks loaded N; overlay radius R chunks" (seen in the RCON replies) | same |

The down face stays about 7% under TFC at sky light (0.93) while it is at 1.03 in block light; the rock and the specks then
take slightly different light (smooth lighting of the block face vs the entity's single light sample), within the
criterion.

Verdict: **pass** on both versions.
