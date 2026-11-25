---
id: PAR-FARM-10
title: "Sapling growth into trees"
area: FARM
status: done
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
  - "Done: core/sapling (SaplingRules, SaplingState, SaplingTreeRefs) and a new public entry core/worldgen/tree/SaplingTree (a new file outside files_owned: TreePlacer and TreeWorld are package-private, no existing file was edited); paper/sapling (SaplingDevice 20 device kinds, SaplingModule catch-up kind tfp:sapling, LiveTreeView, PaperSaplingEffects writes by chunk 96 blocks per tick). Tests: core 11 (SaplingRulesTest 6, SaplingTreeTest 5), paper 9 (SaplingMockBukkitTest). Pending in-game check (container cannot run client). Ledger rows and 3 divergences added."
tests:
  - "paper: catch-up grows"
done_criteria:
  - "An oak sapling becomes a tree after its days"
---

# PAR-FARM-10: Sapling growth into trees

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
