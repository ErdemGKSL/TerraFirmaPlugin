---
id: PAR-PROC-48
title: "Loom in-game verification and the in-progress cloth visual (wool, silk, burlap, paper)"
area: PROC
status: open
wave: 42
deps: [PAR-PROC-06]
size: S
aliases: ["moved from PAR-PROC-06 (2026-11-25, PROTOCOL 6.5): the in-progress texture of TFC's loom block entity renderer is not drawn yet"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/LoomBlockEntityRenderer.java"
files_owned:
  - "docs/verification/PAR-PROC-48/**"
  - "paper/src/main/java/dev/tfp/paper/loom/**"
  - "paper/src/test/java/dev/tfp/paper/loom/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/loom/oak; place it; hold tfp:wool_yarn and click until 16 are in (one per click); push with an empty hand every second: the weave sound plays, progress counts to 16, the cloth appears; take it with an empty hand"
  - "Sneak + empty hand takes the yarn back; the loom faces the player when placed"
plan:
  - "Verify PAR-PROC-06 in game. Then draw the cloth in progress (TFC renders the recipe's texture growing with the progress and the push animation getAnimPos) as an item display / entity_renderer element keyed by the stored progress; the loom recipe's texture field names the vanilla or TFC texture."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-48/"
done_criteria:
  - "Weaving 16 wool yarn gives wool cloth in game; the in-progress cloth is visible on the loom"
---

# PAR-PROC-48: Loom in-game verification and in-progress visual

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
