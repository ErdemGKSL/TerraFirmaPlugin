---
id: PAR-INFRA-41
title: "Ore overlay specks as bright as TFC's: down-face specks read dark brown (0.38 of TFC relative to the rock), side faces 26% dark"
area: INFRA
status: open
wave: 9
deps: [PAR-INFRA-37]
size: M
aliases: ["PAR-INFRA-37 in-game finding: down face brightness (verdict row 1b) and the side-face speck/rock ratio (row 3)"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/resources/assets/tfc/textures/block/ore/normal_native_gold.png"
files_owned:
  - "core/src/main/java/dev/tfp/core/visuals/OverlayFace.java"
  - "core/src/test/java/dev/tfp/core/visuals/OverlayFaceTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/oreoverlay/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/oreoverlay/**"
  - "paper/src/main/java/dev/tfp/paper/oreoverlay/**"
  - "paper/src/test/java/dev/tfp/paper/oreoverlay/**"
  - "docs/verification/PAR-INFRA-41/**"
  - "paper/src/main/java/dev/tfp/paper/packet/display/OverlayDisplayPackets.java"
  - "paper/src/test/java/dev/tfp/paper/packet/display/OverlayDisplayPacketsTest.java"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3; the PAR-INFRA-37 scenes (docs/verification/PAR-INFRA-37/verdict.md): an ore in a granite ceiling seen from below, an ore flush in a north-facing wall of tfp:rock/raw/granite, noon, midnight, torch 2 blocks away"
  - "Measure the speck/rock red-channel ratio on each face (down, north, east, up) and compare with TFC's rock+overlay composite (1.39 for normal_native_gold on granite)"
  - "Natural exposure: on a fresh world find a generated vein exposed by the terrain (a shore cliff or river bank; there are no caves until M2-GEN-09) and run `tfp debug oreoverlay show <pos>` with Bot near: the face is listed and 'sent to Bot', and the specks show in a frame (PAR-INFRA-37 row 7 read 'exposed faces 0' at two plains spawns)"
plan:
  - "UNBLOCKED (manager, 2026-11-08): OverlayDisplayPackets.java and its test are now in files_owned. Implement the design in the BLOCKED line (tilted quad, RIGHT_ROTATION untilt, DOWN mirror), update the golden-value tests accordingly, then redo the lighting captures."
  - "Blocked (2026-11-08): the fix needs `paper/src/main/java/dev/tfp/paper/packet/display/OverlayDisplayPackets.java` and its test `paper/src/test/java/dev/tfp/paper/packet/display/OverlayDisplayPacketsTest.java` (PAR-INFRA-16 files, not in files_owned): `transform` sends only LEFT_ROTATION, and the test pins `OverlayFace.rotation()` and the old tints 0x808080/0xCCCCCC/0x999999 per face by golden value, so any change to `OverlayFace` breaks a test this item may not edit. Add both paths to files_owned and reopen. Cause (checked in the unobfuscated 26.3 client: `Lighting`, `light.glsl`, `entity.vsh`, `VertexConsumer.putBakedQuad`, `FaceBakery.calculateFacing`, `PoseStack$Pose.mulPose`, `ItemTransforms`): an item quad is lit with light = min(1, 0.4 + 0.6 * (max(0, n.L0) + max(0, n.L1))), L0 = (0.2, 1, -0.7) and L1 = (-0.2, 1, 0.7) normalised, n = pose normal matrix x the quad direction snapped to the nearest axis at bake time; `shade: false` does not change it. That gives down 0.40, north/south 0.74, east/west 0.50, up 1.0, which matches the measured 0.38 (down) and 1.03/1.39 = 0.74 (north). Tint compensation alone cannot work (it needs tint > 1 on the down and side faces), texture brightening clips (half of the gold texels exceed 255 at x1.21) and display context NONE ignores item-model display transforms. Design for the reopened item: (1) datagen quad element `rotation {origin [8,8,8], axis x, angle 22.5 or -22.5}` (normal still snaps to -z) so the lit normal tilts 22.5 degrees toward the texture top; (2) the display sends RIGHT_ROTATION = the untilt (geometry flat again, normal tilted toward world up on side faces), LEFT_ROTATION = the face rotation as now, SCALE (1, 1, -1) on DOWN only (a mirror across the quad plane: vertices and winding unchanged, normal flipped to up); (3) tint = round(255 x vanilla shade / light(world normal)): north/south light 0.899 -> 0xE3E3E3, east/west 0.771 -> 0xC6C6C6, up 1.0 -> 0xFFFFFF, down mirrored 1.0 -> 0x808080, so speck/rock = TFC on every face at any light level (the lightmap multiplies both); (4) the crack layer (`tfp:item/render/overlay_crack_N`, untilted) needs tilted twins emitted by `OreOverlays` or it would show 22.5 degrees off. The tilt sign and the 1.21.11 client (obfuscated, assumed the same as its measured ratios matched) must be checked in game. Nether lights differ (overworld only)."
  - "Current (PAR-INFRA-37, 2026-11-07, both versions): on the down face the rock is drawn at 0.40 of TFC's granite (vanilla down shade 0.5 x light) but the specks at 0.15 of TFC's gold: 0.38 of where TFC draws them relative to the rock, so they read dark brown (`docs/verification/PAR-INFRA-37/down-face-vs-tfc.png`, the ceiling specks in the cave walks). North-face specks: speck/rock ratio 1.03 at noon, midnight and torch light vs TFC's 1.39 (about 26% dark). The orientation is right."
  - "Likely cause: the face quad is an item_display item, and the client lights display items with the entity directional lights (both from above), on top of the `OverlayFace` shade tint (down 0x808080, `shade: false` quad): a downward normal only gets the ambient term. Options to try: per-face tint compensation (the down tint can at most double, not enough alone), a quad whose normal points up (geometry flipped by the display rotation so the lit normal is the up one), or `shade`/normal tricks in the item model; whichever matches TFC's ratio on all six faces without breaking the night and torch light following (PAR-INFRA-37 row 3)."
  - "Stats label (PAR-INFRA-37 review): `tfp debug oreoverlay stats` prints 'chunks loaded N (radius R)', which reads as a contradiction (N is the chunks sent to the viewer by view distance, R the overlay cutoff radius); rename to e.g. 'viewer chunks loaded N; overlay radius R chunks' (`OreOverlayModule.stats`)."
tests:
  - "OverlayFaceTest / OverlayModelTest: the per-face tint or normal rule"
  - "In-game ratios per face within 10% of TFC's on both versions"
done_criteria:
  - "Speck/rock ratio within 10% of TFC's on the down, a side and the up face, at noon, midnight and with a torch, on 1.21.11 and 26.3; frames and numbers in docs/verification/PAR-INFRA-41/"
---

# PAR-INFRA-41: Ore overlay specks as bright as TFC's

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Follow-up of PAR-INFRA-37: the lighting of the overlay faces belongs to PAR-INFRA-15 (`OverlayFace`, the overlay items) and
PAR-INFRA-16 (the runtime), outside PAR-INFRA-37's files.
