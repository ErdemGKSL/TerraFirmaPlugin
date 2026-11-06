---
id: PAR-INFRA-37
title: "Recapture the ore overlay feel shots PAR-INFRA-16 missed: down face, distance set, 26.3 night/torch, tool timing, cave walk, fresh-world budget"
area: INFRA
status: open
wave: 8
deps: [PAR-INFRA-16]
size: S
aliases: ["PAR-INFRA-16 review follow-up: in-game captures missing or unusable (in-game follow-ups 1-3 and 7, verdict rows 7, 13, 14)"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
files_owned:
  - "docs/verification/PAR-INFRA-37/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3 (docs/testing/headless.md); survival unless noted; ores placed with `tfp debug oreoverlay place <x> <y> <z> <state>` into granite boxes high above spawn"
  - "Down face: an ore in a ceiling with air below; Bot looks up from below; 8x crop: specks in TFC's orientation on the down face (compare with TFC's normal_native_gold.png)"
  - "Distance set: tp Bot to 16, 32, 48, 64, 96 m from an exposed cliff vein (pitch level, against the sky); no square ring, the spherical cutoff pop is not noticeable at 1080p"
  - "26.3 light: the exposed gold face at midnight (`tfp time set midnight`) and with a torch 2 blocks away; specks lit like the rock next to them"
  - "Mining time: break normal_native_gold/granite and raw granite with the hand, a bronze pickaxe and a wrong tool (bronze shovel); record ticks to break (server log or a stopwatch over frames); ore equals raw granite for each tool (TFC hardness)"
  - "Hand dig cracks: hold left click with an empty hand for 20 s; frames every 2 s: the crack pattern crosses the specks from the first stage (regression check of the PAR-INFRA-16 stage-0 / CraftEngine BLOCK_DESTRUCTION fix)"
  - "Cave walk: Bot walks a 200-block tunnel lined with ore (`fill` pattern); frames every 0.5 s: no overlays popping in at chunk borders"
  - "Budget: fresh world, `tfp debug oreoverlay stats` near spawn: entities sent to Bot, chunks in radius, table builds and ms"
plan:
  - "Current: PAR-INFRA-16 is done with verdict rows 7 (tool timing not measured), 13 (distance set), 14 (fresh world) and the down face, 26.3 night/torch and cave walk shots missing or unusable in its in-game run. The down-face orientation is unit-tested (PAR-INFRA-15 OverlayModelTest)."
  - "Run the in_game_steps with a cheap-model agent where available; the main session judges every frame. A failure is fixed through a follow-up of PAR-INFRA-16's files."
tests:
  - "None beyond the in-game steps"
done_criteria:
  - "Frames in docs/verification/PAR-INFRA-37/ on 1.21.11 and 26.3 for every step; verdict.md per criterion, mining ticks per tool recorded next to raw granite's"
---

# PAR-INFRA-37: Recapture the ore overlay feel shots PAR-INFRA-16 missed

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Follow-up of the PAR-INFRA-16 review: these feel-checklist shots were not shown or not usable in its in-game run. It does
not block PAR-INFRA-16 (no dep cycle).
