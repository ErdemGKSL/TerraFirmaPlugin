---
id: PAR-FARM-15
title: "Spreading bush blocks: CraftEngine states, models, hitbox carriers, loot, worldgen placement"
area: FARM
status: open
wave: 31
deps: [PAR-FARM-07]
size: M
aliases: ["split from PAR-FARM-07: its files_owned cannot reach the hitbox files or the datagen output"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/plant/raspberry_bush.json"
  - "src/main/resources/assets/tfc/blockstates/plant/raspberry_cane.json"
  - "src/main/resources/assets/tfc/blockstates/plant/cranberry_bush.json"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java"
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
  - "tfp debug place tfp:spreading_bush/raspberry[lifecycle=healthy,stage=0]; tfp time add months 4; screenshot"
plan:
  - "Split from PAR-FARM-07 (2026-11-19). The Paper glue exists: device kind tfp:spreading_bush (tfp:device behaviour) with blocks tfp:spreading_bush/<blackberry|raspberry|blueberry|elderberry>[lifecycle=dormant|healthy|flowering|fruiting, stage=0..2], tfp:spreading_bush/<species>_cane[lifecycle, stage, facing=north|east|south|west], tfp:spreading_bush/cranberry[lifecycle, stage] (waterlogged), tfp:spreading_bush/dead[stage=0..2], tfp:spreading_bush/dead_cane[stage, facing]; product items tfp:food/<species> and tfp:food/cranberry. Needed: CE entries (datagen/berry), TFC models, hitbox demands and outline rows (bush: full block at stage 2 else HALF_PLANT box; canes: TFC CANE_* boxes; cranberry waterlogged), GeneratedCarrierTest pins, loot, block items, lang, worldgen placement. Register the Datagen.java run line."
done_criteria:
  - "A raspberry bush spreads canes over four months of time add and the canes grow into bush blocks"
---

# PAR-FARM-15: Spreading bush blocks

> Claimed: session_011osUp5o449zjy3Qcb6jpEk, 2026-11-20

Follow-up of PAR-FARM-07. Follow `plans/parity/PROTOCOL.md`.
