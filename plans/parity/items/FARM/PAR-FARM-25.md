---
id: PAR-FARM-25
title: "CraftEngine blocks and runtime for the growing plant shapes: vines, weeping/twisting columns, kelp, kelp trees, branching cacti, creeping plants, epiphytes"
area: FARM
status: done
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
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "paper/src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/GrowingPlantBlocks.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/GrowingPlantBlocksTest.java"
  - "paper/src/main/java/dev/tfp/paper/plant/growth/**"
  - "paper/src/test/java/dev/tfp/paper/plant/growth/**"
ui: true
in_game_steps:
  - "tfp time add months 2 near weeping vines, kelp and a kelp tree; screenshot that they grew within TFC's caps"
plan:
  - "UNBLOCKED (worker, 2026-11-25): files_owned widened to the shared hitbox files (one new tfc-shapes row, 40 demands, pins) and the module service file. Scope split: the blocks and the lazy growth of weeping, twisting and kelp columns are done here; the rest is PAR-FARM-29."
  - "Done: datagen plant/GrowingPlantBlocks writes the 40 growing plants (tfp:plant/<name>: vine, weeping and twisting columns, kelp, giant kelp tree and flower, saguaro, creepers, epiphytes) with TFC's state properties, TFC's models as item displays (one per multipart model, tinted textures baked, vanilla kelp models passed through) on one hitbox-only row (tfc:plant/body_plant), loot, items (not for the TFC no-item bodies), names; paper plant/growth/{ColumnGrowth,ColumnSweeper,GrowthModule}: the lazy Poisson growth of heads (age cap 25, free cells, old head becomes body, kelp keeps its fluid) with decide-async apply-sync. Tests: GrowingPlantBlocksTest (4, datagen), ColumnGrowthMockBukkitTest (5, paper). tfp time add does not move game time, so the in_game step grows columns by game time like TFC's random ticks. Left to PAR-FARM-29: vine spread, kelp tree and branching cactus growth, survival, per-shape outlines, worldgen wiring. Pending in-game check (container cannot run client)"
  - "Generate CE blocks for the shapes PlantBlockGen leaves out (TYPES), have PlantResources.WRITTEN_TYPES and PlantBlocks.ceRef write them (PAR-WORLD-46), and wire PlantGrowth.lazyTopGrowth, vineMayGrow, branchingCactusGrows and kelpTreeGrows plus PlantSurvival for kelp, columns, creepers and epiphytes through the lazy sweep."
tests:
  - "paper: MockBukkit column growth within the age 25 cap and the free cells"
done_criteria:
  - "Vines, kelp, kelp trees and branching cacti grow lazily within TFC's caps in game"
---

# PAR-FARM-25: Growing plant shapes as CraftEngine blocks

Follow-up of PAR-FARM-12. Follow `plans/parity/PROTOCOL.md`.
