---
id: PAR-FARM-17
title: "Banana blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement"
area: FARM
status: open
wave: 33
deps: [PAR-FARM-09]
size: M
aliases: ["split from PAR-FARM-09: its files_owned cannot reach the hitbox files or the datagen output of the CE blocks"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/plant/banana_plant.json"
  - "src/main/resources/assets/tfc/blockstates/plant/banana_sapling.json"
  - "src/main/resources/assets/tfc/blockstates/plant/dead_banana_plant.json"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/BananaFeature.java"
  - "src/main/resources/data/tfc/loot_table/blocks/plant"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/banana/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/banana/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/banana/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:plant/banana_sapling; tfp time add months 6; screenshot"
plan:
  - "Split from PAR-FARM-09 (2026-11-19). The Paper glue exists: device kinds registered per block id tfp:plant/banana_sapling (no properties), tfp:plant/banana_plant[stage=0..2, lifecycle=dormant|healthy|flowering|fruiting], tfp:plant/dead_banana_plant[stage=0..2] (tfp:device behaviour on each), product item tfp:food/banana (3 to 6 per pick), the dead bush tfp:plant/dead_bush. Needed: CE entries for the 3 blocks (stage x lifecycle = 12 plant states, 3 dead states), TFC models, shapes (PLANT box 2,0,2 to 14,6,14 for stage 2 without collision; TRUNK_0 4..12 and TRUNK_1 5..11 full height with collision for stages 0 and 1), hitbox demands and carriers, loot, the sapling block item, lang, worldgen placement (BananaFeature), hoe overlay lines (hydration and temperature)."
done_criteria:
  - "A planted banana sapling grows a stalk over months (time add) and fruits in season"
---

# PAR-FARM-17: Banana blocks

Follow-up of PAR-FARM-09. Follow `plans/parity/PROTOCOL.md`.
