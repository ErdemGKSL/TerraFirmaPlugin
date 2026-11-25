---
id: PAR-PROC-54
title: "Scribing table: right-click does not open the screen in game"
area: PROC
status: done
wave: 43
deps: [PAR-PROC-15]
size: S
aliases: ["found by PAR-PROC-50 (2026-11-25) in game"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/scribing/**"
  - "paper/src/test/java/dev/tfp/paper/scribing/**"
  - "docs/verification/PAR-PROC-54/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/scribing_table/oak; place it; right-click the tabletop with an empty hand (aim at y 72.8 of the block: the selection box is only the tabletop slab) : an anvil screen titled Scribing Table opens; then the PAR-PROC-50 steps (ink, rename, blank, close)"
plan:
  - "Finding from PAR-PROC-50 (Paper 1.21.11, client 1.21.11): the table placed (`/tfp debug block`: tfp:wood/scribing_table/oak[facing=north]) but three right-clicks with an empty hand on the tabletop (and one with mousedown/up) opened nothing and logged nothing. The same click method opened loom and works for other devices. ScribingListener.onInteract (HIGH, ignoreCancelled false) returns silently when `event.useInteractedBlock() == DENY` (CraftEngine may deny a block it handles) or the hand is not HAND; check which, with a log line, and whether MenuType.ANVIL...build(player) throws silently on Paper 1.21.11. Then do the PAR-PROC-50 checks."
  - "Done: checkReachable(false) (vanilla AnvilMenu.stillValid closed the screen over a non-anvil CE block on its first tick), own reach/table check, title fallback 'Rename Items'. In game (docs/verification/PAR-PROC-54/): screen opens, rename via ink, dye used. MockBukkit cannot build MenuType.ANVIL (builder unimplemented), so no unit test of the open; verified in game A/B. Ink-less, creative and blank steps stay in PAR-PROC-50."
tests:
  - "MockBukkit: interacting with the table opens an anvil view titled Scribing Table"
done_criteria:
  - "Right-clicking the scribing table opens its screen in game"
---

# PAR-PROC-54: Scribing table does not open

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
