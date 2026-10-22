---
id: PAR-FARM-03
title: "Crop blocks: all 29 crops in every shape, seeds, dead and wild crops"
area: FARM
status: open
wave: 28
deps: [PAR-INFRA-08]
size: M
aliases: ["M3-FARM-02 (blocks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/crop"
  - "src/main/java/net/dries007/tfc/common/items/SeedItem.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crop/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crop/**"
  - "content/generated/content/craftengine/tfp/configuration/crop/**"
ui: true
in_game_steps:
  - "tfp debug showcase crops"
  - "screenshot"
plan:
  - "Generate the crop blocks with age states on crop-age carriers (wheat/carrots/beetroot/potatoes ages + tripwire/auto_state pools), double crops, climbing crops with stick, flooded (rice) waterlogged look, dead and wild variants; seeds as block_item items."
tests:
  - "tools/datagen: counts vs Crop.java"
  - "VisualBudgetTest"
done_criteria:
  - "Crop showcase screenshot for every crop and stage"
---

# PAR-FARM-03: Crop blocks: all 29 crops in every shape, seeds, dead and wild crops

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
