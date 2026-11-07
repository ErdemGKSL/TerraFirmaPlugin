# PAR-INFRA-37 verification: ore overlay feel shots

Date 2026-11-07. Jar built from 485d53e (no code change in this item). Paper 1.21.11 build 132 with the 1.21.11 client,
then Paper 26.3 with the 26.3 client, headless (`docs/testing/headless.md`), CraftEngine 26.9.2, PacketEvents 2.14.0,
shipped look `host_rock`, client render distance 10 (the harness default 6 puts 96 m at the fog edge), 1280x720 window
(the harness size; 1080p was not available). Each version ran on a fresh TFC world (priming start, world wiped, second
start). Scenes were built high above the spawn area (x 1600-1800, y 200-233, z 1050-1203) with vanilla `fill` and
`tfp debug oreoverlay place` (every place reply read the cell back as `now: tfp:ore/normal_native_gold/granite`, every
step checked `tfp debug oreoverlay show` with Bot near: the face listed under "sent to Bot"). Bot stood on barrier
platforms outside the blocks. Every judgement below was made by looking at the frames in this folder.

Disk note: the first 1.21.11 run died mid-way (the machine's disk filled up and the server hit OutOfMemoryError after a
Watchdog stall in chunk saving; its run folder was removed by a cleanup). Its down-face, light, distance and tool timing
results were kept; the hand-dig, cave-walk and fresh-world shots were retaken on a second fresh 1.21.11 world.

| # | Step / criterion | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Down face: ore in a granite ceiling, air below, Bot looking straight up (facing north); specks in TFC's orientation | **orientation pass**: sampled to 16x16, the in-game specks equal TFC's `normal_native_gold.png` unrotated in 241/256 texels (best of the 8 rotations/flips; the next best scores 163) (`down-face-vs-tfc.png`). `show`: "down (outcrop), up (outcrop); sent to Bot: down, up" | orientation pass (same frame texel for texel, `down-face-vs-tfc.png`) |
| 1b | Down face brightness (seen in the same frames) | **fail**: the specks read dark brown, not gold. Rock R 60 = 0.40 of TFC's granite (the vanilla 0.5 down shade x sky light); specks R 32 = 0.15 of TFC's gold, i.e. 0.38 of where TFC would draw them relative to the rock (`down-face-vs-tfc.png`, third panel = TFC rock+overlay x 0.40). The ceiling specks in both cave walks show the same dark brown. Follow-up **PAR-INFRA-41** | same fail (identical numbers) |
| 2 | Distance set 16/32/48/64/96 m, pitch level, vein on a cliff face against the sky | pass: the vein is drawn at every distance, no square ring, nothing pops (outcrop faces are sent at full range: `show` "north (outcrop), sent to Bot" at all five distances); at 96 m the specks are 1-2 px (`1.21.11-distance-16-96m.png`, `1.21.11-distance-far-zoom.png` 3x). The spherical cave-face cutoff did not show as a pop anywhere in the cave walks (row 6) | pass (`26.3-distance-16-96m.png`) |
| 3 | 26.3 light: exposed gold face at noon, midnight (`tfp time set midnight` + vanilla `time set midnight`), torch 2 blocks away | pass (also run here): specks follow the rock, speck/rock red ratio 1.03 / 1.03 / 1.02 at noon / midnight / torch (`1.21.11-light-noon-midnight-torch.png`) | **pass**: ratio 1.03 at noon, midnight and with the torch; never black, never full-bright (`26.3-light-noon-midnight-torch.png`). Side-face note: TFC's own rock+overlay ratio is 1.39, so side-face specks are about 26% darker than TFC's; recorded with row 1b in PAR-INFRA-41 |
| 4 | Mining time: ore vs raw granite per tool (TFC hardness 6.5 + 0.2 = 6.7, `requiresCorrectToolForDrops`) | bronze pickaxe: ore 28-31 ticks (7 runs: 31, 29, 30, 31, 28, 28, 28), raw granite 28-30 (7 runs: 28, 29, 29, 28, 28, 30, 29), the order ore/raw swapped in the last three pairs; expected 28 (7.3 / 6.7 / 30 per tick). Bronze shovel (wrong tool): ore 673, raw granite 672; hand: ore 671, raw granite 673 (a second hand run on the ore, during the crack frames on the fresh second world, took 689: the server was still generating terrain around the new scene); expected 670 (1 / 6.7 / 100 per tick). **pass**: ore = raw granite for each tool within the ±2-tick resolution of the method | bronze pickaxe: ore 28, 29; raw granite 30, 28. Shovel: ore 671, raw 670. Hand: ore 672, raw 671. **pass** |
| 5 | Hand dig cracks: empty hand held on the ore, frames every 2 s for 20 s | pass: the crack pattern crosses the gold specks from the first stage (2 s) to 20 s (`1.21.11-hand-dig-cracks-0-20s.png` block crops, `1.21.11-hand-dig-full-frames.png` 0/4/18 s) | pass (`26.3-hand-dig-cracks-0-20s.png`, `26.3-hand-dig-full-frames.png`) |
| 6 | Cave walk: Bot walks (W held, survival, night vision) a 200-block 3x3 tunnel lined with ore (both walls every 2 blocks, ceiling every 4), frames every 0.5 s | pass: 100 frames, Bot 1601 -> 1799; every frame shows the near and middle wall overlays, no frame with a near ore missing its specks, nothing appears at chunk borders (`1.21.11-cave-walk-1.png`, `-2.png`, `-zoom.png` full-size consecutive pairs). A zombie (frames 2-7) and bats were in the tunnel (normal difficulty) | pass (`26.3-cave-walk-1.png`, `-2.png`, `-zoom.png`; peaceful, bats only) |
| 7 | Budget: fresh world, `tfp debug oreoverlay stats` near spawn, typed by Bot | first join, after loading settled: tables 85, ores 95 078, exposed faces 0, builds 85, avg 3.52 ms, max 32.4 ms CPU per chunk, region-thread copy avg 0.67 ms, entities 0 / cap 8000, chunks loaded 85 (radius 4), deferred 0 (`1.21.11-budget-fresh-world.png`). First (lost) world: tables 86, avg 2.53 ms, max 31.2 ms, copy 0.28 ms, entities 7 | tables 121, ores 99 242, exposed faces 0, builds 121, avg 2.50 ms, max 33.4 ms, copy avg 0.41 ms, entities 0 / cap 8000, chunks loaded 121 (radius 4), deferred 0 (`26.3-budget-fresh-world.png`) |

Row 7, why "exposed faces 0" is expected near this spawn: this build's world generation has no caves yet (the cave
tunnels and the noise and noodle caves are not ported, M2-GEN-09, ledger divergence rows "TFC's cave and canyon tunnels"
and "TFC's terrain is a per-block density"), and veins are written only at rock positions the surface builder leaves as
rock (ledger row "TFC writes veins over whatever rock the terrain has"). So a generated vein can only be exposed where the
terrain density itself opens bare rock (shore cliffs, arches, cenotes, river banks); both spawns are flat plains under
soil, so the tables near spawn hold ores but no exposed face, and no overlay entity is sent. That the exposure scan and
the overlay spawning run on generated chunks is not shown by these frames; a `tfp debug oreoverlay show` on a naturally
exposed vein (a shore cliff) is an in-game step of PAR-INFRA-41. "chunks loaded N (radius 4)" mixes two numbers: N is
the chunks sent to the viewer (client view distance 10, still loading at first join), 4 is the overlay cutoff radius in
chunks; the label belongs to PAR-INFRA-16's files and its rename is in PAR-INFRA-41's plan.

Scenes (for a rerun; the frames in this folder come from these, on the worlds described above):
- Distance set (row 2): granite pillar x 1700-1708, y 200-212, z 1150-1152, vein placed on its north face (z 1150). Bot
  in creative at x 1704.5, y 204, z 1134.5 / 1118.5 / 1102.5 / 1086.5 / 1054.5 (16 / 32 / 48 / 64 / 96 m), yaw 0,
  pitch 0 (facing south, toward the vein; the sky is behind the pillar).
- Down face (row 1): ore in a granite ceiling x 1660-1664, y 200, z 1100-1104, air below; Bot at x 1662.5, y 197,
  z 1102.5, pitch -90. Light, mining time and cracks (rows 3-5): granite walls x 1670-1676 and 1680-1692, y 200-204,
  z 1100-1102, Bot on barrier platforms at z 1096-1099 facing south (yaw 0).
- Cave walk (row 6): tunnel x 1601-1799, y 229-231, z 1199-1201 in a granite block x 1600-1800, y 228-233, z 1197-1203.
- Budget (row 7): Bot at the world spawn on the surface at first join, no teleport.
A later recheck at another scene (x -2400, z -1950) is not evidence for this verdict: its frames were not kept, and
the frames in this folder are the only evidence. A rerun attempt made after the review notes took no frames (the
capture stopped before the client joined); it changed nothing here.

Commands (both versions): scene builders and step scripts were kept outside the repo; the replies above come from
RCON. Mining times were measured as the time from `xdotool mousedown 1` to the first RCON
`execute if block <cell> minecraft:air` that passed (polled every ~50 ms), in survival with the item given through
`ce item give`.

Observation (not this item's criterion): `tfp time set noon|midnight` moves the calendar but not the world's day time
(`time query daytime` stayed at 5862 -> 5864 across `tfp time set midnight`), so the light step also ran vanilla
`time set`. TFC's `TimeCommand` moves the world's day time with the calendar (`ServerCalendar.skipForwardBy`
-> `updateDayTime`); the ledger's Calendar row lists the missing `ServerCalendar` sync, and PAR-CLIM-01 (ServerCalendar,
doDaylightCycle sync) is the open item for it.

Verdict: **done**. Every step has frames on 1.21.11 and 26.3 and the mining ticks are recorded per tool. Row 4's
evidence is its tick table (no frames); row 1's single frame `down-face-vs-tfc.png` stands for both versions, which
matched texel for texel. Rows 2-7 pass; row 1 passes for orientation. The down-face brightness failure found here (and the smaller side-face shortfall) is a
defect of PAR-INFRA-16's overlay lighting and goes to follow-up PAR-INFRA-41.
