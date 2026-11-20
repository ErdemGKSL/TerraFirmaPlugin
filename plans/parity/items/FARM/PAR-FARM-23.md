---
id: PAR-FARM-23
title: "Fruit and wild spreading crop blocks: tfp:fruit pumpkin/melon (and rotten), jack o'lantern link, hitbox rows, loot, wild crop worldgen fruit"
area: FARM
status: open
wave: 31
deps: [PAR-FARM-05]
size: M
aliases: ["split from PAR-FARM-05: its files_owned cannot reach tools/datagen (the shared hitbox files) or the worldgen"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/SpreadingCropFeature.java"
  - "src/main/resources/data/tfc/loot_table/blocks/rotten_pumpkin.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crop/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crop/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:food/pumpkin; place it; tfp time add months 1; the block is rotten; screenshot"
  - "plant pumpkin seeds on farmland; tfp time add months 3; a fruit block stands beside the vine; screenshot"
plan:
  - "Generate the CraftEngine blocks the PAR-FARM-05 device kinds expect: tfp:fruit/pumpkin, tfp:fruit/melon, tfp:fruit/rotten_pumpkin, tfp:fruit/rotten_melon (full cubes, TFC models, strength 1.0, wood sound, no push reaction; the block items; loot: the fruit food item, the rotten table with tfc:rotten). The wild blocks tfp:wild_crop/<crop> already exist (PAR-FARM-03); check they carry mature, part and the four sides as WildCropDevice reads them."
  - "Food items tfp:food/pumpkin and tfp:food/melon with TFC's decay modifier, placeable as the fruit blocks (the block item carries the food state; FruitDevice.placed reads it)."
  - "Hitbox rows for the new blocks (full cube) in the hitbox files; coverage owner of melon|pumpkin|rotten_melon|rotten_pumpkin moves here from PAR-FARM-05."
  - "Worldgen: SpreadingCropFeature places the wild pumpkin and melon fruit with the wild food trait (FruitState wild=true, creation date at generation)."
tests:
  - "datagen: fruit block ids and states on the real checkout"
done_criteria:
  - "A placed pumpkin rots in game; a wild pumpkin patch generates with fruit blocks"
---

# PAR-FARM-23: Fruit and wild spreading crop blocks

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
