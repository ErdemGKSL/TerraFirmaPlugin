---
id: PAR-FARM-21
title: "Flower cutting item and plant block items: tfp:flower_cutting content, plant item ids for cuttings"
area: FARM
status: open
wave: 34
deps: [PAR-FARM-11]
size: S
aliases: ["found while doing PAR-FARM-11: the cutting item tfp:flower_cutting is not generated (coverage owner PAR-FARM-11, datagen is outside its files)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java"
  - "src/main/resources/assets/tfc/models/item/flower_cutting.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/FlowerCutting.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/FlowerCuttingTest.java"
ui: true
in_game_steps:
  - "give a shears; right-click a flower; screenshot the dropped cutting"
  - "craft the cutting with compost; two flowers come out"
plan:
  - "Datagen the CE item tfp:flower_cutting (TFC texture and lang) and check that the plant item id the cutting stores (tfp:plant/<name>, written by PAR-FARM-11 CuttingListener) is a CE item the crafting modifier can create; if PlantBlockGen defines no plant block items, add them."
tests:
  - "datagen: item exists, model and lang"
done_criteria:
  - "Shearing a flower drops a cutting that crafts into two flowers"
---

# PAR-FARM-21: Flower cutting item and plant block items

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
