# PAR-METAL-10 charcoal forge in-game check: verdict

Two runs. The first run (captures 01 to 10) used a jar built from `4f65c3b`. The rerun (captures 11 to 14, steps 6r to 9r)
used a jar built from `fd5bd4f`, which was HEAD when this verdict was revised. That jar was built after
`tools/worktree/share-build.sh` linked the datagen output: `unzip -l` lists 21374 `content/` entries, among them
`content/data/id_map.json`, the vein tags and `textures/block/devices/charcoal_forge/*`. A jar built without the datagen
output has no `content/`, so the server starts without any `tfp:` block and this check cannot run.

- Dates: 2026-10-22 (both runs)
- Server: Paper 1.21.11 build 132, Java 25; CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents 2.14.0; vanilla terrain (the forge does not need a TFC world), pack self-hosted on 127.0.0.1
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), GUI scale auto, bot `Bot`; software GL on a shared
  4-core machine
- Judged by: the main session, from the PNGs below and the frame classification

## TFC's animation (`/opt/tfc` @ c3b4d9536)

`textures/block/devices/charcoal_forge/{1..7}.png` are 16x208: 13 frames, `.mcmeta` `frametime: 2`, frames 0 to 12, so
one cycle is 26 ticks, about 1.3 s. Pixel for pixel the 13 frames hold **9 distinct images**, in the order
**0 1 2 3 4 5 6 7 8 4 3 2 0** (the same for all seven levels). Frames 5 to 8 differ only slightly (16 to 18 pixels, channel
deltas up to 22 on level 1, 38 on level 4 and 45 on level 7). `0.png` is a single still 16x16 image.

## Deterministic pack check

The served pack is TFC's animation by construction: each of `0.png`, `1..7.png` and `1..7.png.mcmeta` is byte-equal to
TFC's file in three places: the datagen output (`content/generated/.../textures/block/devices/charcoal_forge/`), the plugin
jar (`content/craftengine/tfp/resourcepack/...`) and the pack CraftEngine built and served
(`plugins/CraftEngine/generated/resource_pack.zip`, `assets/tfp/textures/block/devices/charcoal_forge/`). sha256 prefixes:
`0.png` 562b20a67977, `1.png` d4fa7bc46da5, `2.png` 3bc1558d1d63, `3.png` c86c6deedd46, `4.png` 082da80fd10e,
`5.png` e91a1c01cd97, `6.png` db80f105566f, `7.png` f36763e0b0cc, every `.mcmeta` 98b23402202a. So the screenshots only
need to show that the top animates and which of TFC's frames it shows.

## Scene

First run: a stone floor at y 119 around (96, -2060); forge A at (93, 120, -2060) alone, forge B at (99, 120, -2060) with
stone on all eight neighbours at y 120. Rerun: a stone floor at y 200; forge A at (0, 201, 0) alone, forge B at
(6, 201, 0) with stone on all eight neighbours at y 201. In both, forge B's 14 px top sits 2 px below the rim, and each
heat level was placed with `/tfp debug place tfp:charcoal_forge[heat_level=N]` typed in the client's chat (the command is
player-only).

## Steps and results

| # | step | capture | result |
|---|---|---|---|
| 1 | `tfp time set noon` + `time set 6000`, heat 0, 4, 7 on both forges | `01-overview-noon-midnight-heat-0-4-7.png` (left column) | pass: level 0 shows the still cold top, 4 and 7 the glowing tops; sides are TFC's charcoal pile |
| 2 | `tfp time set midnight` + `time set 18000`, heat 0, 4, 7 | `01-...png` (right column), `07-light-pool-midnight-heat7.png` | pass: lit levels stay bright at night, level 0 goes dark like any block; the stone floor around a level 7 forge is lit warm (CE `luminance: 14` reaches the client's light engine), level 4 lights it less, level 0 not at all |
| 3 | forge B in the stone ring, top view at both times | `02-stone-ring-top-heat-0-4-7.png`, `03-stone-ring-rim-8x-noon-heat4.png`, `04-stone-ring-rim-midnight-heat7.png` | pass: the four inner rim faces (2 px of stone above the 14 px top) are drawn on every side; no x-ray strip, no missing faces (the `snow[layers=7]` carrier does not occlude its neighbours) |
| 4 | forge B from grazing angles, front and diagonal | `05-stone-ring-grazing.png` | pass: no gap or see-through line between the forge top and the rim |
| 5 | forge A close up, side faces at a shallow angle, level 7 at midnight | `06-display-over-block-model-no-zfight-midnight-heat7.png` | pass: no z-fighting between the 1.002 item display and the block model on the top or the sides; the display covers the block model completely |
| 6 | first run: 24, 40 and 80 screenshots of the level 7 top about 250 ms apart | `08-animation-heat7-24-captures.png` | superseded by 6r: at that capture rate the screenshots fell into 6 clusters because TFC's frames 5 to 8 (nearly equal) merged into one; that run cannot tell them apart |
| 6r | rerun: the level 1, 4 and 7 tops filmed straight from above (`ffmpeg x11grab`, 30 captures/s, 8 s each, 240 captures per level, one top texel about 21 screen px); each capture sampled at the 16x16 texel centres and matched by normalised correlation against TFC's 9 distinct frames | `11-animation-9-frames-heat-1-4-7.png` (per level: the first capture matched to each frame above TFC's frame) | pass: every capture matches one of TFC's frames exactly (best correlation 1.0000 for all 720 captures); all 9 distinct frames are seen on every level, in TFC's order 0 1 2 3 4 5 6 7 8 4 3 2 0, two to three full cycles per level, frame 0 held twice as long as the others (TFC's frames 12 and 0 are both image 0) |
| 7 | first run: state change while watching forge A: 7, 4, 7, 0 (70 screenshots) | `09-state-change-7-4-7-0.png` | pass: each change swaps the top directly from one level to the next; no capture shows the cold `heat_0` block model or an empty top between two lit levels, and the floor light follows the level |
| 7r | rerun: forge A filmed from above at 30 captures/s while placing 7, 4, 7, 0 (480 captures) and classified against all 8 levels' frames | - | pass: the runs are 7 (114), 4 (165), 7 (158), 0 (43); every capture matches a TFC frame of the level just placed (worst best correlation 0.99995), none shows an empty, cold or mixed top between two lit levels |
| 8 | first run: turn away and back; teleport 375 blocks away (chunks and the display entity unloaded), wait 8 s, return | `10-out-of-view-and-back.png` | pass: after turning back the lit top is there in the first capture; after the return the first three captures show unloaded terrain and the fourth the complete lit forge, with no capture of the cold top first |
| 8r | rerun of 8 (teleport 375 blocks away, 8 s, back; 10 captures 0.3 s apart) | `13-rerun-out-of-view-and-back.png` | pass: three captures of sky or empty terrain, then the forge with its lit top in every capture after it; no cold top first |
| 9r | rerun of 1 to 4: heat 0, 4, 7 at noon and midnight, forge A in the open and forge B in the stone ring from above | `12-rerun-noon-midnight-ring-heat-0-4-7.png` (rows heat 0, 4, 7; columns open noon, open midnight, ring noon, ring midnight) | pass: as steps 1 to 4 |
| 10 | top orientation: the level 0 top (TFC's `heat_0` drawn by the block model) and the lit tops (TFC's `heat_N` drawn by the item display) from the 6r/7r captures, against TFC's textures | `14-lit-top-turned-180-vs-block-model.png` | **defect**: the level 0 capture matches `0.png` turned 180° (the block model's up face, as TFC draws it), the lit captures match `N.png` unturned. The item display draws the lit tops turned 180° about the vertical axis relative to TFC's block model, so every lit forge shows its glow pattern rotated half a turn from TFC's. The sides use one texture on all four faces and look the same. Follow-up: PAR-METAL-20 |
| 11 | server log of the rerun on `fd5bd4f` (second start to `stop`; the first start only wrote CraftEngine's `delay-configuration-load: false` and built the pack) | - | pass: no error or warning from TerraFirmaPlugin or CraftEngine; the only stack trace is PacketEvents' update check (HTTP 403 from GitHub) |

## Done criteria

Verdict: **pass, with one defect filed**. Night, neighbouring full blocks, animation frames, lit-display flicker and the
light all pass. The lit tops are turned 180° against TFC's (step 10); the fix is in `ForgeDevices` (a `rotation` on the
`entity_renderer` element or a turned render model), a file this item does not own, so it is follow-up item
PAR-METAL-20 with capture 14 as its evidence.

## Limits of this check

- TFC's top holds 9 distinct frames (0 1 2 3 4 5 6 7 8 4 3 2 0); frames 5 to 8 differ in only 16 to 18 pixels. The first
  run's 250 ms screenshots merged them into one cluster and reported 6 images; the rerun's 30 captures/s at about 21
  screen px per texel separates all 9 (step 6r). The byte-equal pack check is what pins the animation to TFC's.
- Cadence: the client on software GL advanced the animation slower than TFC's 1.3 s (2.4 to 3.1 s per cycle in the
  rerun, about 5 s in the first run), because the starved client ran fewer client ticks. The `.mcmeta` is TFC's own, so on
  a normal client the cadence is TFC's; a run on a GPU client should confirm it.
- Flicker: the rerun sees one capture every 33 ms (the first run one every 150 to 290 ms); a flicker shorter than one
  client frame would not show. None was seen in 480 captures around four state changes.
- Lighting: the lit tops are item displays with a fixed brightness (block light `heat * 2`, sky light 15), so they get no
  ambient occlusion; in the stone ring TFC's lit block model would be shaded by vanilla smooth lighting at its rim edges.
  The level 0 top is the block model and is shaded like TFC's (darker in the ring than in the open).
- Collision: the carrier `snow[layers=7]` collides at 12 px against TFC's 14 px (ledger divergence). If PAR-INFRA-13 moves
  the forge to another carrier, steps 3, 4, 5 and 9r must be redone.
- `tfp time set noon|midnight` moves only the TFC calendar; the world clock (sky light) stayed where it was, so both runs
  also used vanilla `time set`. Linking the world day time to the calendar is PAR-CLIM-01 ("doDaylightCycle sync").
