---
id: PAR-INFRA-37
title: "Recapture the ore overlay feel shots PAR-INFRA-16 missed: down face, distance set, 26.3 night/torch, tool timing, cave walk, fresh-world budget"
area: INFRA
status: done
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
  - "Before any frame: build the box first, then `place`; record the place reply (it reads the cell back: `now: tfp:ore/...`) and `tfp debug oreoverlay show <pos>` with Bot near (tables exist only for chunks sent to a viewer, so `stats` reads `ores 0` with nobody near); keep Bot outside the box (creative or on a platform, e.g. 3+ blocks west at the ore's y facing +x) so it cannot suffocate (PAR-INFRA-16 verdict, recheck section)"
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
  - "Done: all steps run on fresh TFC worlds on Paper 1.21.11 + client and Paper 26.3 + client (jar of 485d53e, no code change). Pass: distance set 16-96 m (no ring, no pop), 26.3 noon/midnight/torch (speck/rock ratio 1.03 in all three), tool timing (bronze pickaxe ore 28-31 / raw granite 28-30 ticks, expected 28; bronze shovel and hand 670-673 for both, expected 670), hand-dig cracks over the specks from the first stage, 200-block cave walk (100 frames each, no pop-in), fresh-world budget (1.21.11: 85 tables, avg 3.5 ms, max 32 ms, copy 0.67 ms; 26.3: 121 tables, avg 2.5 ms, max 33 ms, copy 0.41 ms). Down face: orientation equals TFC (241/256 texels) but the specks are 0.38 of TFC's brightness relative to the rock (dark brown); side faces 26% dark: follow-up PAR-INFRA-41, ledger divergence row. Evidence and verdict: docs/verification/PAR-INFRA-37/"
tests:
  - "None beyond the in-game steps"
done_criteria:
  - "Frames in docs/verification/PAR-INFRA-37/ on 1.21.11 and 26.3 for every step; verdict.md per criterion, mining ticks per tool recorded next to raw granite's"
---

# PAR-INFRA-37: Recapture the ore overlay feel shots PAR-INFRA-16 missed

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Follow-up of the PAR-INFRA-16 review: these feel-checklist shots were not shown or not usable in its in-game run. It does
not block PAR-INFRA-16 (no dep cycle).

## Review notes (non-blocking, for the PAR-INFRA-41 rerun)

- Row 1 has one shared frame (`down-face-vs-tfc.png`) for both versions; the rerun should keep a separate 26.3 down-face crop.
- Row 4's evidence is the RCON tick table, as the step allows ("server log or a stopwatch over frames"); the rerun should keep the raw poll log or a CSV of the timings in the evidence folder.
- Time hand-mining only after chunk generation has settled (the 689-tick outlier came from terrain-generation load).
- The final review round's in-game capture did not start because another agent's server held the harness; no frames were needed, the commit changed only notes.
