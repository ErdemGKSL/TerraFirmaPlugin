---
id: PAR-GATHER-10
title: "Plant and thatch movement slowdown: TFC's per-plant speed factors registered with the slow-block service"
area: GATHER
status: open
wave: 25
deps: [PAR-GATHER-04, PAR-WORLD-12]
size: S
aliases: ["found while doing PAR-GATHER-04: the slowdown service (SlowBlocks) and the player listener exist and slow leaves and fallen leaves; plants need TFC's Plant.speedFactor table and the plant blocks (PAR-WORLD-12), thatch its multiplier"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BodyPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/plantslow/**"
  - "core/src/test/java/dev/tfp/core/plantslow/**"
  - "paper/src/main/java/dev/tfp/paper/plantslow/**"
  - "paper/src/test/java/dev/tfp/paper/plantslow/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plantslow/**"
ui: false
in_game_steps: []
plan:
  - "Datagen: export Plant.speedFactor per plant (and the berry bush age/stage rule, thatch 0.6) to a data file read by core; never hand-copy the table."
  - "Core: factor = lerp(plantsMovementModifier 0.2, speedFactor, 1) (PlantBlock/TopPlantBlock/BodyPlantBlock/KelpTreeBlock), SeasonalPlantBlock's lerp by age/stage with the leaves modifier, thatch 0.6 unless waterlogged."
  - "Paper: a SlowBlocks.Provider registered through services().require(SlowBlocks.class); the listener of PAR-GATHER-04 does the rest."
tests:
  - "core: factors against rows exported from TFC for every plant"
  - "paper: provider answers for tfp:plant/* ids and not for others"
done_criteria:
  - "Walking through TFC plants and thatch slows the player by TFC's factors; tests green"
---

# PAR-GATHER-10: Plant and thatch movement slowdown

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
