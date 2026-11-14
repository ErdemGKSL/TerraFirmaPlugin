---
id: PAR-PROC-40
title: "Charcoal sneak click adds two layers: ignore Paper's re-fired RIGHT_CLICK_BLOCK of the same click"
area: PROC
status: open
wave: 15
deps: [PAR-PROC-37]
size: S
aliases: ["follow-up of PAR-PROC-37 (defect found by its in-game run, docs/verification/PAR-PROC-10/verdict.md)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/InteractionManager.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/charcoal/CharcoalListener.java"
  - "paper/src/test/java/dev/tfp/paper/charcoal/CharcoalListenerClickTest.java"
  - "docs/verification/PAR-PROC-40/**"
ui: true
in_game_steps:
  - "sneak + right click once with charcoal on a stone floor: one layer, one charcoal used"
  - "sneak + right click once with charcoal on that pile: two layers; repeat to eight"
  - "sneak + right click once with an oak log on a floor and on a log pile: one log each"
plan:
  - "In the PAR-PROC-37 in-game run (2026-11-14) one sneak click with charcoal on a stone floor used two charcoal and made a two-layer pile. Cause: the vanilla client sends ServerboundUseItemPacket after the use-item-on that the server answered without a client-side change; CraftBukkit's handleUseItem ray-traces again and, when the hit position differs from the use-item-on position it remembered (firedInteract / interactPosition), fires a second PlayerInteractEvent RIGHT_CLICK_BLOCK. The new ray hits the charcoal pile just placed, so CharcoalListener.charcoal adds a layer. TFC adds one layer per click."
  - "Fix in CharcoalListener: remember per player the tick of a handled charcoal (and log) click and ignore a second RIGHT_CLICK_BLOCK of the same hand in the same server tick (deny it so CraftEngine and vanilla do nothing either). A MockBukkit test fires the two events of one click and expects one layer and one charcoal used; a click in the next tick still adds a layer."
  - "Logs took one log per click in the same run (the second event lands on the new pile, whose insert path is guarded by the double-click memory); check the log path with the same test."
tests:
  - "paper: CharcoalListenerClickTest"
done_criteria:
  - "One sneak click with charcoal adds exactly one layer in game"
---

# PAR-PROC-40: Charcoal sneak click adds two layers: ignore Paper's re-fired RIGHT_CLICK_BLOCK of the same click

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
