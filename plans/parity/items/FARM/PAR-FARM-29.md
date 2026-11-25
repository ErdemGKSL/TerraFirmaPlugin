---
id: PAR-FARM-29
title: "Growing plant shapes, part 2: vine, kelp tree and branching cactus growth, survival of columns, kelp and creepers, per-shape outlines, worldgen wiring"
area: FARM
status: open
wave: 36
deps: [PAR-FARM-25]
size: L
aliases: ["split from PAR-FARM-25: its files_owned reach only the column growth runtime and the block datagen"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/EpiphytePlantBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/plant/growth/**"
  - "paper/src/test/java/dev/tfp/paper/plant/growth/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantResources.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantBlocks.java"
ui: true
in_game_steps:
  - "place tfp:plant/jungle_vines, tfp:plant/giant_kelp_flower and tfp:plant/saguaro; let them grow; screenshot within TFC's caps"
plan:
  - "PAR-FARM-25 wrote the 40 blocks (tfp:plant/<name>) and the lazy column growth (weeping, twisting, kelp heads). Left: PlantGrowth.vineMayGrow (vine spread, needs the instant temperature), kelpTreeGrows (KelpTreeFlowerBlock), branchingCactusGrows (GrowingBranchingCactusBlock) in the lazy sweep; PlantSurvival for columns, kelp, creepers, epiphytes and the perched epiphytes (PlantSupportQueue only covers the PAR-WORLD-12 types); PlantResources.WRITTEN_TYPES and PlantBlocks.ceRef writing these blocks in worldgen (PAR-WORLD-46); TFC's per-shape outlines (BodyPlantBlock WEEPING/TWISTING/THIN shapes, CreepingPlantBlock face boxes, EpiphytePlantBlock side boxes) as tfc-shapes rows instead of the one body-plant row; the cactus and cactus-flower block types (CACTUS, CACTUS_FLOWER) that neither generator writes."
tests:
  - "paper: MockBukkit vine spread, kelp tree and branching cactus growth within the caps"
done_criteria:
  - "Vines, kelp trees and branching cacti grow lazily within TFC's caps in game; columns, kelp and creepers pop without their support"
---

# PAR-FARM-29: Growing plant shapes, part 2

Follow-up of PAR-FARM-25. Follow `plans/parity/PROTOCOL.md`.
