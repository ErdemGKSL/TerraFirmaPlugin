---
id: PAR-FARM-04
title: "Crop growth glue: lazy calendar growth, climate checks, death, harvest yields"
area: FARM
status: open
wave: 29
deps: [PAR-FARM-03, PAR-FARM-01]
size: M
aliases: ["M3-FARM-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/CropBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java"
  - "src/main/java/net/dries007/tfc/common/blocks/crop/IPickableCrop.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/crop/**"
  - "core/src/test/java/dev/tfp/core/crop/**"
  - "core/src/test/resources/dev/tfp/core/crop/**"
  - "paper/src/main/java/dev/tfp/paper/crop/**"
  - "paper/src/test/java/dev/tfp/paper/crop/**"
ui: true
in_game_steps:
  - "plant wheat seeds on farmland"
  - "tfp time add months 2; tp away and back"
  - "screenshot; break for yield"
plan:
  - "Crop state (growth, yield, expiry, lastGrowthTick) in block data; catch-up kind runs CropRules on chunk load/interaction/sweep (decide async, apply sync) incl. climate per day and nutrient consumption from the farmland below; death to dead crop; harvest yields by yield value; pickable crops (berries on crops) picked by right-click."
tests:
  - "core: crop goldens exist"
  - "paper: catch-up applies stage"
  - "itest: `crop.grow` over 60 days"
done_criteria:
  - "Wheat planted in spring is harvestable in summer in-game"
---

# PAR-FARM-04: Crop growth glue: lazy calendar growth, climate checks, death, harvest yields

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
