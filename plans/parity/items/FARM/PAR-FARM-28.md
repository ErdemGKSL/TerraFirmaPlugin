---
id: PAR-FARM-28
title: "Fruit tree splicing and hoe overlay lines for fruit trees and bananas"
area: FARM
status: open
wave: 34
deps: [PAR-FARM-16, PAR-FARM-17]
size: M
aliases: ["split from PAR-FARM-16 and PAR-FARM-17"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeBranchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/fruittree/Splic*"
  - "paper/src/test/java/dev/tfp/paper/fruittree/Splic*"
ui: false
in_game_steps: []
plan:
  - "Knife splicing (FruitTreeSaplingBlock.maySplice: the knife in the offhand plus a sapling item used on a sapling or on a branch whose block above allows it) and the hoe overlay lines of the branches, saplings and the banana (hydration and average temperature tooltips, tfc.tooltip.fruit_tree.*)."
tests:
  - "paper: splicing adds a sapling to a sapling block and a branch; the overlay lines name the climate range"
done_criteria:
  - "A sapling spliced onto a sapling block raises its saplings count; the hoe shows the tree's climate lines"
---

# PAR-FARM-28: Fruit tree splicing and hoe overlay lines

Follow-up of PAR-FARM-16 and PAR-FARM-17. Follow `plans/parity/PROTOCOL.md`.
