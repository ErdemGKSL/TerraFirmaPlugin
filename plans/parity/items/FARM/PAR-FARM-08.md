---
id: PAR-FARM-08
title: "Fruit trees: saplings, growing branches, seasonal leaves, fruit"
area: FARM
status: done
wave: 31
deps: [PAR-FARM-06, PAR-INFRA-10]
size: M
aliases: ["M6-DECO-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/FruitFamilies.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FruitFamilies*"
  - "content/generated/content/craftengine/tfp/families/fruit_tree.yml"
  - "content/generated/content/craftengine/tfp/configuration/fruittree/**"
  - "paper/src/main/java/dev/tfp/paper/fruittree/**"
  - "paper/src/test/java/dev/tfp/paper/fruittree/**"
  - "core/src/main/java/dev/tfp/core/berry/FruitTree*"
  - "core/src/test/java/dev/tfp/core/berry/FruitTree*"
ui: true
in_game_steps:
  - "plant tfp:plant/cherry_sapling; tfp time add years 1; screenshot"
plan:
  - "Done (core + Paper glue): core/berry FruitTreeGrowth (branch geometry), FruitTreeState; paper/fruittree device kinds tfp:plant/<tree>_sapling|_growing_branch|_leaves (24, one handler), catch-up kind tfp:fruit_tree, FruitTreeModule. The CE blocks (40), models, hitboxes, loot, worldgen and knife splicing could not be done inside files_owned (hitbox files are shared, content/generated is a datagen output): follow-up PAR-FARM-16. Pending in-game check (container cannot run client)"
  - "Load the 40 fruit tree blocks; growing branch geometry decided async from the calendar (FruitTreeRules), seasonal leaf states via the catch-up sweep, fruit picking."
tests:
  - "core: lifecycle goldens"
  - "core: FruitTreeGrowthTest (9), FruitTreeStateTest (2); paper: FruitTreeMockBukkitTest (11)"
done_criteria:
  - "A planted cherry sapling grows branches over a year (time add) and fruits in season"
---

# PAR-FARM-08: Fruit trees: saplings, growing branches, seasonal leaves, fruit


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Done: core `FruitTreeGrowth` (TFC's `grow` geometry as placements, `addLeaves`), `FruitTreeState` (+ tests, 11); `paper/fruittree` (`FruitTreeDevice`, `FruitTreeEffects`, `PaperFruitTreeEffects`, `FruitTreeModule`) with `FruitTreeMockBukkitTest` (11); ledger row and divergences; CE visuals split to PAR-FARM-16. Pending in-game check (container cannot run client).
