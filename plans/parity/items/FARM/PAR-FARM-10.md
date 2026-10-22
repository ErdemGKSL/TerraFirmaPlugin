---
id: PAR-FARM-10
title: "Sapling growth into trees"
area: FARM
status: open
wave: 32
deps: [PAR-WORLD-11, PAR-INFRA-11]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java"
  - "src/main/java/net/dries007/tfc/world/feature/tree"
files_owned:
  - "core/src/main/java/dev/tfp/core/sapling/**"
  - "core/src/test/java/dev/tfp/core/sapling/**"
  - "core/src/test/resources/dev/tfp/core/sapling/**"
  - "paper/src/main/java/dev/tfp/paper/sapling/**"
  - "paper/src/test/java/dev/tfp/paper/sapling/**"
ui: true
in_game_steps:
  - "plant oak sapling; tfp time add days 20; reload chunk; screenshot"
plan:
  - "Sapling placement timestamp; catch-up kind grows the tree (the TFC tree feature for that wood) when the days have passed and space allows, placed by the commit queue."
tests:
  - "paper: catch-up grows"
done_criteria:
  - "An oak sapling becomes a tree after its days"
---

# PAR-FARM-10: Sapling growth into trees

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
