---
id: PAR-FARM-03
title: "Crop blocks: all 29 crops in every shape, seeds, dead and wild crops"
area: FARM
status: done
wave: 28
deps: [PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["M3-FARM-02 (blocks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/crop"
  - "src/main/java/net/dries007/tfc/common/items/SeedItem.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crop/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crop/**"
  - "content/generated/content/craftengine/tfp/configuration/crop/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
ui: true
in_game_steps:
  - "tfp debug showcase crops"
  - "screenshot"
plan:
  - "UNBLOCKED (worker, 2026-11-19): files_owned widened to the four hitbox files (shared: edit only the tfp:crop / dead_crop / wild_crop rows, demands and pins, keep others on rebase). PAR-CLIM-04 is done."
  - "Blocked (2026-11-19): files_owned is too narrow for PAR-INFRA-13's carrier rule, and the files needed overlap PAR-CLIM-04 (claimed, not finished), so they cannot be widened yet. Every explicit appearance state in generated CE YAML must come from the hitbox selector (HardcodedCarrierTest bans vanilla state literals outside datagen/hitbox and datagen/visuals; GeneratedCarrierTest pins the exact block ids and checks each state against HitboxDemands). The crops need: demands in hitbox/HitboxDemands.java for every age/part/stick/mature appearance (about 400 appearances over 29 crops: crop, dead_crop, wild_crop), outline rows for TFC CropBlock QUARTER/HALF/FULL shapes in hitbox/TfcShapes.java + src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json (tfc:crop/<name>[age,part], dead and wild shapes; no collision), the new block ids pinned in hitbox/GeneratedCarrierTest.java. BlockCoverage already has the owner rule. Note: wild_crop is listed under PAR-FARM-05 in BlockCoverage.OWNERS but FARM-05 owns only paper code, so FARM-03 should generate wild crops too (generated wins in the gate). Unblock: add the four hitbox paths (HitboxDemands.java, TfcShapes.java, tfc-shapes.json, GeneratedCarrierTest.java) to files_owned once PAR-CLIM-04 is done (or the owner approves a shared edit of only the tfp:crop rows), then generate tfp:crop/*, tfp:dead_crop/*, tfp:wild_crop/* and seeds/* block items in datagen/crop/CropBlocks.java."
  - "Generate the crop blocks with age states on crop-age carriers (wheat/carrots/beetroot/potatoes ages + tripwire/auto_state pools), double crops, climbing crops with stick, flooded (rice) waterlogged look, dead and wild variants; seeds as block_item items."
  - "Done: datagen crop/CropBlocks (29 crops x crop/dead_crop/wild_crop = 87 blocks, TFC property sets, TFC models as item displays on three shared hitbox-only carriers, loot tables for the loot runtime, seeds tfp:seeds/<crop> and names), HitboxDemands/TfcShapes/tfc-shapes.json rows (quarter, half; full served on the half row, ledger divergence), GeneratedCarrierTest pins, BlockCoverage owner line for wild crops; tests CropBlocksTest (10) + theCropDemandsArePinned. Planting limits (farmland below), growth and death are PAR-FARM-04/05. Pending in-game check (container cannot run client)"
tests:
  - "tools/datagen: counts vs Crop.java"
  - "VisualBudgetTest"
done_criteria:
  - "Crop showcase screenshot for every crop and stage"
---

# PAR-FARM-03: Crop blocks: all 29 crops in every shape, seeds, dead and wild crops


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
