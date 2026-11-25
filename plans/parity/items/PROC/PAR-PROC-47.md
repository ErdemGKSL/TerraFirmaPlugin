---
id: PAR-PROC-47
title: "Sluice in-game verification: placement of both halves, water, soil intake, loot, break"
area: PROC
status: open
wave: 33
deps: [PAR-PROC-13, PAR-WORLD-31]
size: S
aliases: ["moved from PAR-PROC-13 (2026-11-25, PROTOCOL 6.5): the deposit items come with PAR-WORLD-31"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java"
files_owned:
  - "docs/verification/PAR-PROC-47/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/sluice/oak; place it on a bank facing downhill with a one-block-wide flowing stream ending at the block above-behind the upper half (flowing level 1): both halves appear and the item-display model is drawn; water appears at the output two blocks ahead and one down"
  - "Throw 4 tfp:deposit/* items onto the upper half: after about 1 s each disappears (one per slot); every 5 s one deposit is consumed, the pickup sound plays and loot items appear above the output water"
  - "Remove the water source: the countdown stops; break one half: the other half and the output water go, soil is dropped"
plan:
  - "Verification of PAR-PROC-13 once deposit items exist. Also check the selection outline of both halves against TFC (hitbox-only carriers farmland / daylight detector) and the model alignment with the display rotation per facing."
  - "Partial in-game run 2026-11-25 (docs/verification/PAR-PROC-47/verdict.md): placement and output water pass; water arrival is not noticed and thrown deposits gave no loot in 25 s. Pending in-game check, fixes in PAR-PROC-53; rerun the steps after it."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-47/"
done_criteria:
  - "A sluice processes deposit items into loot in-game; facing, placement, water and break behave as TFC"
---

# PAR-PROC-47: Sluice in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
