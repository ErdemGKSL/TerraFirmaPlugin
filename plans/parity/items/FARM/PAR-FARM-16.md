---
id: PAR-FARM-16
title: "Fruit tree blocks: CraftEngine states, models, hitbox carriers, loot, splicing"
area: FARM
status: open
wave: 32
deps: [PAR-FARM-08]
size: L
aliases: ["split from PAR-FARM-08: its files_owned cannot reach the hitbox files or the datagen output of the CE blocks"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/plant/cherry_leaves.json"
  - "src/main/resources/assets/tfc/blockstates/plant/cherry_branch.json"
  - "src/main/resources/assets/tfc/blockstates/plant/cherry_growing_branch.json"
  - "src/main/resources/assets/tfc/blockstates/plant/cherry_sapling.json"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeBranchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java"
  - "src/main/resources/data/tfc/loot_table/blocks/plant"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/fruittree/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/fruittree/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/fruittree/**"
  - "paper/src/main/java/dev/tfp/paper/fruittree/Splic*"
  - "paper/src/test/java/dev/tfp/paper/fruittree/Splic*"
ui: true
in_game_steps:
  - "tfp debug place tfp:plant/cherry_sapling[saplings=2]; tfp time add years 1; screenshot"
plan:
  - "Split from PAR-FARM-08 (2026-11-19). The Paper glue exists: device kinds registered per block id tfp:plant/<tree>_sapling[saplings=1..4], tfp:plant/<tree>_growing_branch[stage=0..3, saplings=0..4, natural, north..down], tfp:plant/<tree>_leaves[lifecycle, persistent] (tfp:device behaviour on each; the static tfp:plant/<tree>_branch[stage, north..down] has none), the dead bush tfp:plant/dead_bush, product items tfp:food/<tree>. Needed: CE entries for the 40 blocks (datagen reads families/fruit_tree.yml), TFC models and the branch pipe multipart, hitbox demands and outline rows, GeneratedCarrierTest pins, loot, block items, lang, worldgen placement of natural trees (natural=true growing branches), knife splicing (maySplice: offhand knife plus a sapling item on a sapling or on a branch), hoe overlay lines. Register the Datagen.java run line."
done_criteria:
  - "A planted cherry sapling grows branches over a year (time add) and fruits in season"
---

# PAR-FARM-16: Fruit tree blocks

> Claimed: session_011osUp5o449zjy3Qcb6jpEk, 2026-11-20

Follow-up of PAR-FARM-08. Follow `plans/parity/PROTOCOL.md`.
