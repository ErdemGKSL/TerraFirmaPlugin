---
id: PAR-INFRA-23
title: "Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class"
area: INFRA
status: open
wave: 5
deps: [PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-INFRA-13 (its in_game_steps and screenshot done-criterion)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
files_owned:
  - "docs/verification/PAR-INFRA-13/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/unusable.json"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then repeat on 26.x (pink_petals/wildflowers/leaf_litter shapes are client-side and version-dependent)"
  - "Every carrier class whose CE block exists at the time of the run (today: the charcoal forge on snow[layers=7] and the bellows on dirt_path; the firepit family once PAR-INFRA-19/PAR-HEAT-06 load it; the rest as their family items land): tfp time set noon; a flat stone row; place one block per class 2 blocks apart (tfp debug place ...)"
  - "Selection outline (no F3+B): tp Bot 2 blocks in front of each block, pitch 35 deg; screenshot; 8x crop; the outline must be the carrier's shape in reports/hitbox-carriers-<family>.md"
  - "Collision: tp Bot onto each block with collision, wait 20 ticks, data get entity Bot Pos[1]; stand height minus floor = the carrier's collision height; walk Bot through each no-collision class"
  - "Break: Bot breaks each block in survival; the crack overlay covers the outline; the CE drop appears; no vanilla carrier block or vanilla drop appears"
  - "Client prediction: Bot holds a vanilla snow layer item and a block item and right-clicks each snow-carried block; no ghost layer or ghost block (10 frames 100 ms apart)"
  - "Large vessel heavy_core check (when the vessel block exists): outline 8x8x8, stand height floor + 0.5, no vanilla heavy_core look or sound, no water; on failure add the unusable.json row (class, carrier, family, failed check, date, verification path), rerun datagen (the wall post is chosen) and repeat with the wall post"
plan:
  - "Current: PAR-INFRA-13 implemented the selector in datagen (reports/hitbox-carriers-1.21.md and -26.md list every carrier). Its in-game steps could not run in that item: most pinned classes have no CE block yet, the firepit family is not loaded by CraftEngine (outside configuration/), and test execution is delegated to a cheap-model agent the worker did not have."
  - "Run the steps above for the classes that exist, store the crops and verdict.md in docs/verification/PAR-INFRA-13/, and record failed carriers in hitbox/unusable.json (then the selector falls back and the ledger needs the fallback row)."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-INFRA-13/"
done_criteria:
  - "The outline of each verified class matches the report on 1.21.11 and 26.x, stand heights match the carrier collision, no ghost prediction with a held snow layer"
  - "Classes not verifiable yet are listed in verdict.md with the item that will add their block"
---

# PAR-INFRA-23: Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
