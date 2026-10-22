---
id: PAR-FARM-05
title: "Wild crops and spreading crops (pumpkin, melon), rotten pumpkins"
area: FARM
status: open
wave: 30
deps: [PAR-FARM-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/crop/WildCropBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/SpreadingCropBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/crop/wild/**"
  - "paper/src/test/java/dev/tfp/paper/crop/wild/**"
ui: true
in_game_steps:
  - "plant pumpkin; tfp time add months 3; screenshot"
plan:
  - "Wild crops drop seeds by maturity month window; spreading crops place fruit blocks that rot lazily into rotten pumpkins/melons."
tests:
  - "paper: maturity window"
done_criteria:
  - "A pumpkin vine sets a pumpkin that later rots"
---

# PAR-FARM-05: Wild crops and spreading crops (pumpkin, melon), rotten pumpkins

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
