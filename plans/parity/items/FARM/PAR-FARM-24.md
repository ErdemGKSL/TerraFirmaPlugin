---
id: PAR-FARM-24
title: "Wire SpreadingFruit into the crop device: fruit placement at full growth, crowding limit, fruit sides"
area: FARM
status: done
wave: 31
deps: [PAR-FARM-05, PAR-FARM-04]
size: S
aliases: ["split from PAR-FARM-05: CropDevice/CropWorld belong to PAR-FARM-04's files"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/crop/SpreadingCropBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/crop/*.java"
  - "paper/src/test/java/dev/tfp/paper/crop/*.java"
ui: false
plan:
  - "CropDevice.settle/apply: for a SPREADING crop, take the service dev.tfp.paper.crop.wild.SpreadingFruit (ModuleServices.get); pass growthLimit(block, type, base) into the growth spec as the limit, after the step call afterGrowth(block, type, growth, playerTicks) and, when it returns a growth, store it in the CropPlant (TFC: crop.setGrowth) ; call updateSides(block, type, state) on every visit so the four side properties show the fruit."
  - "Module order: crop-wild before crop (WildModule provides the service; CropModule resolves it lazily)."
  - "Test: a crop at growth 1 with a free side on spreading_fruit_grows_on ground places the fruit and falls to 0.6..0.72."
  - "Done: CropSettle.settle takes a growth-limit override; CropDevice uses the SpreadingFruit service for pumpkin and melon (growth limit, afterGrowth stored in the CropPlant, sides on every apply); CropModule resolves it lazily; CropDeviceTest (2 new)"
tests:
  - "paper: spreading crop places fruit"
done_criteria:
  - "A grown pumpkin vine sets a fruit block beside it"
---

# PAR-FARM-24: Wire SpreadingFruit into the crop device

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
