---
id: PAR-FARM-30
title: "Growing plant shapes, part 3: per-shape outlines and the saguaro fruit block"
area: FARM
status: done
wave: 37
deps: [PAR-FARM-29]
size: M
aliases: ["split from PAR-FARM-29: its files_owned reach only the paper growth runtime and the worldgen plant writers"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/EpiphytePlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/GrowingPlantBlocks.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/PlantBlockGen.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/**"
ui: true
in_game_steps:
  - "place tfp:plant/hanging_vines_plant, tfp:plant/winged_kelp_plant, tfp:plant/moss, tfp:plant/guzmania and tfp:plant/giant_kelp_flower; screenshot the outline of each (F3+B or the selection box)"
plan:
  - "PAR-FARM-25 serves every growing plant on one 14 x 16 x 14 outline row. TFC's own: BodyPlantBlock WEEPING_SHAPE (4,9,4)-(12,16,12), TWISTING_SHAPE (4,0,4)-(12,15,12), THIN_BODY_SHAPE (5,0,5)-(11,16,11), TWISTING_THIN_SHAPE (5,0,5)-(11,12,11), BODY_SHAPE; CreepingPlantBlock six face boxes (2 px slabs, combined per state); EpiphytePlantBlock four side boxes (12 px deep); the kelp tree flower (1..15 cube); the pipe plants' PipeBlock(0.3125) outline per connection pattern. Add one tfc-shapes row per shape and have GrowingPlantBlocks pick the row per block type (HitboxDemands carries one demand per pattern, as the fruit branches do)."
  - "CACTUS_FLOWER (saguaro_fruit) and CACTUS (barrel_cactus) are written by neither generator; PlantBlockGen.TYPES leaves them out. Write tfp:plant/saguaro_fruit so GrowingBranchingCactusBlock.tryFruit (CactusGrowth.FRUIT) places it."
  - "Done: tfc-shapes rows per shape (BodyPlantBlock WEEPING/TWISTING/THIN/TWISTING_THIN, KelpTreeFlowerBlock, PlantBlock.PLANT_SHAPE, EpiphytePlantBlock walls, CreepingPlantBlock slabs, vine faces, 0.3125 pipes; the solid columns and cacti collide), HitboxDemands one demand per shape tag (GROWING_TYPES), GrowingPlantBlocks names appearances <tag>_<n> and writes tfp:plant/saguaro_fruit (CACTUS_FLOWER, one state). The carrier budget fits only a handful of free outline states, so vines, creepers, kelp trees and cacti are served per face / trunk, not per face combination (ledger divergence). Tests: GrowingPlantBlocksTest (5), GeneratedCarrierTest count 41, TfcShapesTest. Pending in-game check (container cannot run client)"
tests:
  - "datagen: GeneratedCarrierTest pins the new rows; GrowingPlantBlocksTest checks the per-shape row of each block type"
done_criteria:
  - "Every growing plant's selection box matches TFC's shape; saguaros fruit"
---

# PAR-FARM-30: Growing plant shapes, part 3

Follow-up of PAR-FARM-29. Follow `plans/parity/PROTOCOL.md`.
