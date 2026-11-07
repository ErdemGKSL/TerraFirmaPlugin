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
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3; the PAR-INFRA-37 scenes (docs/verification/PAR-INFRA-37/verdict.md): an ore in a granite ceiling seen from below, an ore flush in a north-facing wall of tfp:rock/raw/granite, noon, midnight, torch 2 blocks away"
  - "Measure the speck/rock red-channel ratio on each face (down, north, east, up) and compare with TFC's rock+overlay composite (1.39 for normal_native_gold on granite)"
plan:
  - "Current (PAR-INFRA-37, 2026-11-07, both versions): on the down face the rock is drawn at 0.40 of TFC's granite (vanilla down shade 0.5 x light) but the specks at 0.15 of TFC's gold: 0.38 of where TFC draws them relative to the rock, so they read dark brown (`docs/verification/PAR-INFRA-37/down-face-vs-tfc.png`, the ceiling specks in the cave walks). North-face specks: speck/rock ratio 1.03 at noon, midnight and torch light vs TFC's 1.39 (about 26% dark). The orientation is right."
  - "Likely cause: the face quad is an item_display item, and the client lights display items with the entity directional lights (both from above), on top of the `OverlayFace` shade tint (down 0x808080, `shade: false` quad): a downward normal only gets the ambient term. Options to try: per-face tint compensation (the down tint can at most double, not enough alone), a quad whose normal points up (geometry flipped by the display rotation so the lit normal is the up one), or `shade`/normal tricks in the item model; whichever matches TFC's ratio on all six faces without breaking the night and torch light following (PAR-INFRA-37 row 3)."
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
