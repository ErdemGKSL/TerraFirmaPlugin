---
id: PAR-PROC-20
title: "Knapping in-game verification: axe head from two granite loose rocks, clay and leather screens"
area: PROC
status: open
wave: 20
deps: [PAR-TOOL-01]
size: S
aliases: ["follow-up of PAR-PROC-01 (its in_game_steps and screenshot done-criterion)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/KnappingScreen.java"
files_owned:
  - "docs/verification/PAR-PROC-01/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:rock/loose/granite 2"
  - "right-click air"
  - "Screenshot at GUI scale 2 and 3: the knapping art (TFC knapping.png re-laid into a 6-row chest), 25 granite cells on the slots of columns 1 to 5, rows 0 to 4, the arrow and the output frame under chest slot 26; 8x crop of one cell and of the output frame"
  - "click the axe pattern (cells off: (0,0) (2,0) (3,0) (4,0) (4,1) (4,3) (0,4) (2,4) (3,4) (4,4)); screenshot: the granite stack is 1, the clicked cells show the panel, tfp:stone/axe_head/igneous_intrusive in the output; take it"
  - "give Bot 5 minecraft:clay_ball; right-click air; click one cell: the off cell shows clay_ball_disabled; close: no clay consumed"
  - "give Bot minecraft:leather; right-click air; click one cell: the leather is gone and the window stays open"
plan:
  - "Deferred screenshot check of PAR-PROC-01 (PROTOCOL §6.5): the axe head output needs the stone tool head CE items of PAR-TOOL-01; before that the output of a rock recipe is an unknown item and stays empty. Store crops and verdict.md in docs/verification/PAR-PROC-01/."
tests: []
done_criteria:
  - "Knapping an axe head from two granite loose rocks in-game"
  - "Clay shows the disabled texture on off cells; leather hides them"
---

# PAR-PROC-20: Knapping in-game verification: axe head from two granite loose rocks, clay and leather screens

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
