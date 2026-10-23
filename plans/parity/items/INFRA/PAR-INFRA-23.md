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
  - "itest/src/main/java/dev/tfp/itest/cases/HitboxBreakResyncCase.java"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then repeat on 26.x (pink_petals/wildflowers/leaf_litter shapes are client-side and version-dependent)"
  - "Every carrier class whose CE block exists at the time of the run (today: the charcoal forge on snow[layers=7] and the bellows on dirt_path; the firepit family once PAR-INFRA-19/PAR-HEAT-06 load it; the rest as their family items land): tfp time set noon; a flat stone row; place one block per class 2 blocks apart (tfp debug place ...)"
  - "Selection outline (no F3+B), in daylight (tfp time set noon, clear weather, brightness max; the night run of 2026-10-23 was too dark to judge, and the forge's dark still texture devices/charcoal_forge/0 hides a black outline): tp Bot 2 blocks in front of each block, pitch 35 deg; screenshot; 8x crop (place a light block such as smooth quartz next to the forge so the outline has contrast); the outline must be the carrier's shape in reports/hitbox-carriers-<family>.md (forge: 14 px full width). If no outline shows or it is taller than 14 px, read the block state the client holds (F3 targeted block, or a packet log of ClientboundBlockUpdatePacket / the chunk section): snow[layers=7] is right; snow[layers=8] means the hide mapping snow[7] -> snow[8] was applied to the forge itself (a double mapping in the packet path)"
  - "Collision: tp Bot onto each block with collision, wait 20 ticks, data get entity Bot Pos[1]; stand height minus floor = the carrier's collision height; walk Bot through each no-collision class"
  - "Break: Bot breaks each block in survival; the crack overlay covers the outline; no vanilla carrier block or vanilla drop appears. Drop check depends on PAR-INFRA-19: until it is done the forge drops nothing (the known `tfp:loot_table/self` divergence, ledger row: TFC drops 7 minecraft:charcoal, registerNoItem), so no drop is expected and is not a carrier fault; after PAR-INFRA-19 the CE drop must appear. Ghost block regression (2026-10-23 run, s05-forge-pre-break / post-break): after the survival break the server reported air (TFC leaves air too) but the client showed a WHITE full-height cube with a full-cube outline, the vanilla look of snow[layers=8], i.e. the hide target of the forge's snow[layers=7] carrier (the b00239a notes called it a stone cube; that was wrong). Trace the break path: (1) log every ClientboundBlockUpdatePacket / section update for the position from the break until 2 s later (state before and after CraftEngine's rewrite); (2) decide whether CraftEngine resends the forge's mapped state (snow[7]) and the snow[7] -> snow[8] hide mapping is then applied to that already-mapped state, or whether the client keeps a stale predicted state that the server never corrects; (3) relog and check whether the cube disappears (client leftover) or stays (server block); (4) repeat on a single-layer stone floor with F3 block info on the position and the block below. Fix owner: the CraftEngine adapter if it is a resend fault, the selector (a different carrier, unusable.json row) if snow[7] cannot be broken cleanly. Record the answer and crops in verdict.md"
  - "Client prediction: Bot holds a vanilla snow layer item and a block item and right-clicks each snow-carried block: the forge (snow[layers=7]) and the grill (snow[layers=5], VANILLA mode with no hide mapping: the ledger row expects a possible ghost sixth layer); no ghost layer or ghost block (10 frames 100 ms apart). Also right-click the bellows (dirt_path) with a hoe: note whether the till sound or a farmland flash appears (ledger residual)"
  - "Large vessel heavy_core check (when the vessel block exists): outline 8x8x8, stand height floor + 0.5, no vanilla heavy_core look or sound, no water; on failure add the unusable.json row (class, carrier, family, failed check, date, verification path), rerun datagen (the wall post is chosen) and repeat with the wall post"
plan:
  - "2026-10-23 (review of PAR-INFRA-13, second pass): PAR-CLIM-10 and PAR-INFRA-24 are done (ac11bfe). PAR-INFRA-13 is back to blocked on this item; ac11bfe had set it done, which the review withdrew (its done criterion 2 is unmet and no owner decision was recorded). The dep cycle (PAR-INFRA-19 and PAR-HEAT-06 depend on PAR-INFRA-13, which waits for this item, which depends on them) needs the owner: the proposed fix is to move PAR-INFRA-13's done criterion 2 here and set PAR-INFRA-13 done. Remaining deps: PAR-INFRA-19 and PAR-HEAT-06. The forge and the bellows can be checked on both families now."
  - "Front-matter edits by another item (said explicitly, PROTOCOL 3): this item was created by PAR-INFRA-13 (b00239a) as the follow-up for its in-game check; PAR-INFRA-13's review fixes (1bbeef8) moved it from wave 5 to wave 14 and rewrote its deps, and its second review pass (2026-10-23) rewrote the outline and break steps, added the ghost-cube regression test and its itest file to files_owned. The owner may revise any of it."
  - "Re-test notes, not carrier faults (do not re-report): the forge looks like a flat dark pit because level 0 uses TFC's heat_0 block model and the still devices/charcoal_forge/0 texture (compared in PAR-METAL-10); the bellows is a dark-brown dirt_path slab because it is a placeholder in its vanilla look until PAR-HEAT-12 (ledger row on the firepit/grill/pot/bellows shapes and looks); the forge's 12 px collision vs TFC's 14 px is a logged residual (ledger row on the charcoal forge carrier); the forge's missing drop is the self-loot divergence (PAR-INFRA-19). Coverage: the firepit family, loose rocks, small ores, groundcover, containers and placed items need loaded CraftEngine blocks first (PAR-INFRA-19, PAR-HEAT-06, the family items); re-run the outline, collision, break and prediction steps on 1.21.11 and 26.x once they land, and list the rest in verdict.md."
  - "Deps: PAR-INFRA-19 and PAR-HEAT-06 make CraftEngine load the firepit family (moved into configuration/devices/), PAR-CLIM-10 lets the plugin enable on 26.x, PAR-INFRA-24 makes the packaged jar ship the content; family items (rocks, ores, piles, containers) add their classes as they land. PAR-INFRA-13 is not a dep: its selector is merged and it is blocked until this item passes (a dep would be a cycle)."
  - "Current: PAR-INFRA-13 implemented the selector in datagen (reports/hitbox-carriers-1.21.md and -26.md list every carrier). Its in-game steps could not run in that item: most pinned classes have no CE block yet, the firepit family is not loaded by CraftEngine (outside configuration/), and test execution is delegated to a cheap-model agent the worker did not have."
  - "Run the steps above for the classes that exist, store the crops and verdict.md in docs/verification/PAR-INFRA-13/, and record failed carriers in hitbox/unusable.json (then the selector falls back and the ledger needs the fallback row)."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-INFRA-13/"
  - "Regression for the ghost cube: itest case HitboxBreakResyncCase (added to SelfTestSuite): place tfp:charcoal_forge[heat_level=0], break it as a fake survival player, capture the block-change packets sent for the position after CraftEngine's rewrite and assert the last one is minecraft:air and none carries minecraft:snow[layers=8]; the same for the bellows (dirt_path)"
done_criteria:
  - "The outline of each verified class matches the report on 1.21.11 and 26.x, stand heights match the carrier collision, no ghost prediction with a held snow layer"
  - "Classes not verifiable yet are listed in verdict.md with the item that will add their block"
---

# PAR-INFRA-23: Hitbox carrier in-game verification: outlines, stand heights and client prediction of every carrier class

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
