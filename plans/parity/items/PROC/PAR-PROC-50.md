---
id: PAR-PROC-50
title: "Scribing table in-game verification and TFC screen art"
area: PROC
status: open
wave: 42
deps: [PAR-PROC-15, PAR-PROC-49]
size: S
aliases: ["moved from PAR-PROC-15 (2026-11-25, PROTOCOL 6.5): untested against a real server"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/ScribingTableScreen.java"
files_owned:
  - "docs/verification/PAR-PROC-50/**"
  - "paper/src/main/java/dev/tfp/paper/scribing/**"
  - "paper/src/test/java/dev/tfp/paper/scribing/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/scribing_table/oak; place it; right-click: an anvil screen titled Scribing Table opens"
  - "Put an iron ingot in slot 1 and a black dye (tfc:scribing_ink tag) in slot 2, type a name: the result shows without an XP cost; taking it (click and shift-click) gives the renamed ingot and uses one dye; without dye the click does nothing; creative needs no dye"
  - "Blank text on a renamed item gives the unnamed item; closing the screen returns the items"
plan:
  - "Verify the PrepareAnvilEvent result path and the manual result take (cost 0, no vanilla level check) on 1.21.11. If the client greys the result at cost 0, switch the screen to a textured chest-title GUI. Then add TFC's scribing screen art (textures/gui/scribing_table.png via the CE title glyph technique)."
  - "In-game run 2026-11-25: right-click on the placed table opens no screen (docs/verification/PAR-PROC-50/verdict.md). Pending in-game check; follow-up PAR-PROC-54 (fixed 2026-11-25: screen opens, ink rename and take pass; remaining here: no-ink, creative, blank, close-returns-items)."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-50/"
done_criteria:
  - "Renaming at a scribing table works in game as TFC (ink use, creative, blank clears)"
---

# PAR-PROC-50: Scribing table in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
