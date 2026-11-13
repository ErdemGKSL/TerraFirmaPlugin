---
id: PAR-FARM-12
title: "Wild plant block rules: survival per shape, cactus damage, tall halves, plant stacking, flower shearing, long growth of vines, kelp, kelp trees and branching cacti"
area: FARM
status: open
wave: 26
deps: [PAR-WORLD-12, PAR-WORLD-13, PAR-INFRA-10, PAR-INFRA-11]
size: L
aliases: ["parity audit 2026-11-13: the gameplay of common/blocks/plant (canSurvive, entityInside, useItemOn, long growth randomTick) has no owner; PAR-WORLD-12/13 only generate the blocks and PAR-FARM-11 owns spread and regrowth"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCBushBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallShrubBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCPassableCactusBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BranchingCactusBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCKelpBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCKelpTopBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/CreepingWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/EpiphytePlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/WaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TallWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/RotatableWaterPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PipePlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCSeagrassBlock.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/plant/**"
  - "core/src/test/java/dev/tfp/core/plant/**"
  - "core/src/test/resources/dev/tfp/core/plant/**"
  - "paper/src/main/java/dev/tfp/paper/plant/**"
  - "paper/src/test/java/dev/tfp/paper/plant/**"
ui: true
in_game_steps:
  - "In a fresh world near a meadow: break the dirt under a flower and under a tall grass; screenshot: both pop with their TFC drops, the tall grass's upper half too"
  - "Walk Bot into a saguaro/barrel cactus from the itest position; screenshot the health bar after 2 s (1.0 cactus damage per contact tick as TFC)"
  - "Hold a flower item and right-click the same flower block (not sneaking) where TFC allows stacking; tfp debug block shows the age raised; shear a flower; screenshot the dropped flower"
  - "tfp time add months 2 near weeping vines, kelp and a kelp tree from the itest positions with the chunk loaded through the catch-up path; screenshot that they grew within TFC's caps"
plan:
  - "Current (audit 2026-11-13): PAR-WORLD-12 generates every Plant shape as a CE block and PAR-WORLD-13 places the features, PAR-FARM-11 owns PlantRegrowth spread and age growth, PAR-GATHER-02 the drops, PAR-GATHER-04 the movement slowdown, PAR-DATA-14 vanilla bamboo and vines on leaves, PAR-DECO-13 krummholz, PAR-WORLD-25 corals. Nothing owns the rest of the plant block code, so placed TFC plants never break when their support goes, cacti do not hurt, tall halves do not pair, and vines, kelp and cacti never grow."
  - "core/plant (pure, golden vs TFC): canSurvive per shape (bush/grass on the plantable_on tags, dry plants, tall halves, water plants and floating plants by fluid and depth, epiphytes on logs, creeping plants on any solid face with updateStateFromSides, hanging/top/body pipes, kelp and kelp trees attached downward, cactus on sand); the stacking rule (canBeReplaced with the same item and age < max raises age, not when sneaking); flower shearing (shears on #minecraft:flowers drop the flower item); long growth: TopPlantBlock age < 25 grows one block in its direction when the next block is free, GrowingBranchingCactusBlock and KelpTreeFlowerBlock with plantGrowthChance / plantLongGrowthChance and their size caps, TFCVineBlock only when the instant temperature is above 0 C."
  - "paper/plant: survival through the PAR-INFRA-10 neighbour runner (decide async, apply sync: a support change marks the plants above/beside, the decision pops the ones that cannot survive, the region commit queue applies it with their loot); cactus contact damage 1.0 of vanilla's cactus damage type for TFCCactusBlock, TFCPassableCactusBlock, TFCCactusBedBlock and BranchingCactusBlock (entity-in-block check on the entity's region thread, throttled to the vanilla contact cadence); tall halves placed and broken together; stacking and shearing on interaction; long growth as lazy catch-up from the last-seen calendar time through PAR-INFRA-11 (no ticking), equivalent to TFC's random ticks in expectation (lazy-vs-stepped test)."
  - "Config keys plantGrowthChance and plantLongGrowthChance through the PAR-DATA-06 config when it exists; until then the TFC defaults as constants with a ledger note."
tests:
  - "core: survival golden per shape vs TFC (table exported from /opt/tfc), stacking and shearing rules, long-growth caps"
  - "core: lazy-vs-stepped: catch-up growth of a kelp column and a branching cactus over 1..24 months matches the random-tick simulation in mean and bound"
  - "paper: MockBukkit: support removal pops the plant through the neighbour runner; cactus contact hurts once per cadence; creative and spectator players are not hurt"
  - "itest: plant.survival case breaks the block under five plant shapes and checks they popped"
done_criteria:
  - "Plants break without support, cacti hurt on contact, tall halves pair, stacking and shearing work, vines/kelp/kelp trees/branching cacti grow lazily within TFC's caps; tests green; screenshots pass review"
---

# PAR-FARM-12: Wild plant block rules: survival per shape, cactus damage, tall halves, plant stacking, flower shearing, long growth

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
