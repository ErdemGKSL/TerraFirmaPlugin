---
id: PAR-FARM-14
title: "Stationary berry bush blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement"
area: FARM
status: open
wave: 30
deps: [PAR-FARM-06]
size: M
aliases: ["split from PAR-FARM-06: its files_owned cannot reach the hitbox files"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/plant/snowberry_bush.json"
  - "src/main/resources/assets/tfc/models/block/plant/snowberry_bush_0.json"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBerryBushBlock.java"
  - "src/main/resources/data/tfc/loot_table/blocks/plant"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/berry/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/berry/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/berry/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:berry_bush/snowberry[lifecycle=fruiting,stage=2]; right-click; screenshot"
plan:
  - "Blocked-by-ownership split from PAR-FARM-06 (2026-11-19). The Paper glue exists: device kind tfp:berry_bush, blocks tfp:berry_bush/<snowberry|bunchberry|gooseberry|cloudberry|strawberry|wintergreen_berry>[lifecycle=dormant|healthy|flowering|fruiting, stage=0..2] and tfp:berry_bush/dead[stage=0..2], tfp:device behaviour, product items tfp:food/<species>. Needed: CE entries (datagen/berry/BerryBlocks), TFC models (12 per species + dead), hitbox demands and an outline row (TFC HALF_PLANT box(2,0,2,14,8,14), no collision) for every appearance, GeneratedCarrierTest pins, loot, block items, lang, and worldgen placement of bushes (PAR-WORLD-13 gap ids). Register the Datagen.java run line."
done_criteria:
  - "A snowberry bush fruits in season and can be picked in game"
---

# PAR-FARM-14: Stationary berry bush blocks

Follow-up of PAR-FARM-06. Follow `plans/parity/PROTOCOL.md`.
