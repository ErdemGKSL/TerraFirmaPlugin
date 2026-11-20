---
id: PAR-FARM-05
title: "Wild crops and spreading crops (pumpkin, melon), rotten pumpkins"
area: FARM
status: done
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
ui: false
in_game_steps:
  - "plant pumpkin; tfp time add months 3; screenshot"
plan:
  - "Done: paper/crop/wild (WildRules, WildCropDevice kind tfp:wild_crop, FruitDevice kind tfp:fruit, SpreadingFruit service, WildModule); WildRulesTest 10 tests. Pending in-game check (container cannot run client). Follow-ups: PAR-FARM-23 (CE fruit blocks, hitbox rows, worldgen fruit), PAR-FARM-24 (wire into CropDevice)."
  - "Wild crops drop seeds by maturity month window; spreading crops place fruit blocks that rot lazily into rotten pumpkins/melons."
tests:
  - "paper: maturity window"
done_criteria:
  - "A pumpkin vine sets a pumpkin that later rots (rules and glue done; in-game: Deferred check tracked in PAR-FARM-23)"
---

# PAR-FARM-05: Wild crops and spreading crops (pumpkin, melon), rotten pumpkins


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
