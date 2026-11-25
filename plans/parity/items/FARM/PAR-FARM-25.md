---
id: PAR-FARM-25
title: "CraftEngine blocks and runtime for the growing plant shapes: vines, weeping/twisting columns, kelp, kelp trees, branching cacti, creeping plants, epiphytes"
area: FARM
status: open
wave: 35
deps: [PAR-FARM-12]
size: L
aliases: ["found while doing PAR-FARM-12: those shapes are vanilla placeholders in generation, so the survival and long-growth rules (core/plant PlantSurvival, PlantGrowth) have no block to run on"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/GrowingPlantBlocks.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/GrowingPlantBlocksTest.java"
  - "paper/src/main/java/dev/tfp/paper/plant/growth/**"
  - "paper/src/test/java/dev/tfp/paper/plant/growth/**"
ui: true
in_game_steps:
  - "tfp time add months 2 near weeping vines, kelp and a kelp tree; screenshot that they grew within TFC's caps"
plan:
  - "Generate CE blocks for the shapes PlantBlockGen leaves out (TYPES), have PlantResources.WRITTEN_TYPES and PlantBlocks.ceRef write them (PAR-WORLD-46), and wire PlantGrowth.lazyTopGrowth, vineMayGrow, branchingCactusGrows and kelpTreeGrows plus PlantSurvival for kelp, columns, creepers and epiphytes through the lazy sweep."
tests:
  - "paper: MockBukkit column growth within the age 25 cap and the free cells"
done_criteria:
  - "Vines, kelp, kelp trees and branching cacti grow lazily within TFC's caps in game"
---

# PAR-FARM-25: Growing plant shapes as CraftEngine blocks

Follow-up of PAR-FARM-12. Follow `plans/parity/PROTOCOL.md`.
