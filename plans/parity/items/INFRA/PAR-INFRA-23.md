---
id: PAR-INFRA-23
title: "Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class"
area: INFRA
status: open
wave: 14
deps: [PAR-INFRA-19, PAR-HEAT-06, PAR-CLIM-10, PAR-INFRA-24]
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
  - "Break: Bot breaks each block in survival; the crack overlay covers the outline; the CE drop appears; no vanilla carrier block or vanilla drop appears. The forge's missing drop is the known `tfp:loot_table/self` divergence (ledger, fixed by PAR-INFRA-19: 7 minecraft:charcoal). Investigate the full stone-textured cube that showed at the forge position after the break in the b00239a run (screenshot forge-after-break.png, with forge-outline-full.png before it, in the PAR-INFRA-13 reviewer's scratch folder ui/PAR-INFRA-13/): rerun the break on a single-layer floor (not the two-layer room) and with F3 block info on the position and the block below; decide whether it is the room setup, a client-side leftover of the carrier (relog clears it) or a server block, and record the answer and crops in verdict.md"
  - "Client prediction: Bot holds a vanilla snow layer item and a block item and right-clicks each snow-carried block: the forge (snow[layers=7]) and the grill (snow[layers=5], VANILLA mode with no hide mapping: the ledger row expects a possible ghost sixth layer); no ghost layer or ghost block (10 frames 100 ms apart). Also right-click the bellows (dirt_path) with a hoe: note whether the till sound or a farmland flash appears (ledger residual)"
  - "Large vessel heavy_core check (when the vessel block exists): outline 8x8x8, stand height floor + 0.5, no vanilla heavy_core look or sound, no water; on failure add the unusable.json row (class, carrier, family, failed check, date, verification path), rerun datagen (the wall post is chosen) and repeat with the wall post"
plan:
  - "2026-10-23: PAR-CLIM-10 and PAR-INFRA-24 are done (fixed with PAR-INFRA-13's unblock; the plugin enables on Paper 26.3 and a content-less jar now fails the build), and PAR-INFRA-13 is done with its in-game check delegated here. Remaining deps: PAR-INFRA-19 and PAR-HEAT-06 (firepit family). The forge and the bellows can be checked on both families now."
  - "Deps: PAR-INFRA-19 and PAR-HEAT-06 make CraftEngine load the firepit family (moved into configuration/devices/), PAR-CLIM-10 lets the plugin enable on 26.x, PAR-INFRA-24 makes the packaged jar ship the content; family items (rocks, ores, piles, containers) add their classes as they land. PAR-INFRA-13 is not a dep: its selector is merged and it is blocked until this item passes (a dep would be a cycle)."
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
