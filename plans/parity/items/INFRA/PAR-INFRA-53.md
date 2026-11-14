---
id: PAR-INFRA-53
title: "Hitbox carrier in-game verification of the classes added by family items: piles, rocks, ores, groundcover, placed items, containers, seals, heavy_core"
area: INFRA
status: open
wave: 47
deps: [PAR-HEAT-08, PAR-HEAT-09, PAR-PROC-18, PAR-PROC-37, PAR-PROC-38, PAR-WORLD-09, PAR-GATHER-01, PAR-POT-01, PAR-POT-06, PAR-POT-09, PAR-METAL-12, PAR-FAUNA-07, PAR-DECO-05]
size: M
aliases: ["moved from PAR-INFRA-23 (2026-11-14, PROTOCOL 6.5): the in_game_steps of the carrier classes that had no loaded CraftEngine block when PAR-INFRA-23 ran (docs/verification/PAR-INFRA-13/verdict.md, 'Classes not verifiable yet')"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
files_owned:
  - "docs/verification/PAR-INFRA-53/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/unusable.json"
ui: true
in_game_steps:
  - "Method of PAR-INFRA-23 (docs/verification/PAR-INFRA-13/verdict.md, section Method): headless Paper 1.21.11 + client 1.21.11, then 26.x; fresh run dir, TFC world, debug entry looking_at_block always on, BetterHud date panel off for Bot on 26.x (/hud turn off Bot); tp Bot to the test site first and let the chunks load, then build a stone floor on a sky platform with a smooth quartz row directly behind the test blocks; one block per class 3 blocks apart, placed with /tfp debug place <state> from Bot's chat while Bot looks at a glass block in that spot"
  - "Run each part as soon as its CE block loads (the barrel seal toggle and the composter stages can run at any time: their blocks are loaded since PAR-PROC-32/34)"
  - "Classes: tfp:grill and tfp:pot; each loose rock count (tfp:rock/loose/granite[count=1|2|3]); one small ore; one groundcover of each TFC shape (SMALL, MEDIUM, FLAT, PIXEL_HIGH); charcoal pile layers 1..8; composter stages 1..8 (fill the bin with wheat or grass and sticks or straw as PAR-PROC-34 did; /tfp debug place with stage>0 is reset to the contents)"
  - "Outline: Bot 2 blocks south of each block, yaw 180, pitch 35, crosshair on the block; one screenshot as is and one with F1 (hides the outline), the differing pixels are the outline; it must be the carrier's shape in the hitbox-carriers report"
  - "Collision: tp Bot in survival onto each block that has collision (grill, pot, charcoal pile n>1), wait 1.6 s, data get entity Bot Pos[1]: the stand height minus the floor equals the carrier's collision height; walk Bot through each no-collision class (loose rocks, small ores, groundcover, placed items, charcoal pile 1) and check it passes"
  - "Break: Bot breaks each block in survival (empty hand or the right tool); frames 0.3 s and 2 s after; no vanilla carrier block or vanilla drop appears; scan the item entities over the row for the CE drops only"
  - "Containers, each placed on the stone row, looked at (outline screenshot), walked onto or into, then broken: barrel on its side (facing=north) without and with rack, a lone barrel rack, powderkeg unsealed and sealed, large vessel open and sealed, plain and one glazed colour (tfp:ceramic/large_vessel, tfp:ceramic/large_vessel/red), nest box, wooden and ceramic bowl block, crucible, tool rack on a wall, shelf (empty, then with a placed item under it)"
  - "Placed items (owner rule: every item put on the ground, whatever it is: vessels, jugs, molds, bowls, buckets, tools, ingots): hold the item, look at the top face of stone and hold right-click >= 1 s once in each quadrant (aim at the NW, NE, SW, SE corner; the jug while sneaking); a press shorter than 1 s places nothing; after each placement screenshot the outline (always the 14x1x14 pressure-plate outline) and check the item display sits in the quadrant nearest the cursor; walk Bot through the block (no collision); right-click each quadrant with an empty hand and once with sneak + item to take the items back; place a very large item once; break a block holding 4 items (all drop, no vanilla pressure plate drops)"
  - "Gesture negatives: a block item held 2 s on stone places the block, not a placed item; releasing after 0.6 s or moving the cursor to another block cancels; holding on a side face or on a chest does nothing new (the chest opens on the first event as in vanilla)"
  - "Sealed/open checks: toggle the seal of a barrel (barrel screen, PAR-PROC-35), a powderkeg and a large vessel while Bot looks at it; the outline changes only where the carrier table says it does (barrels and powderkegs keep the cactus box, the large vessel keeps the heavy_core box) and no ghost block flashes on the client (record 2 s at 20 fps)"
  - "Large vessel heavy_core check (plain and red glazed, open and sealed, facing north and east): look (outline 8x8x8 centred), stand on it (Bot y = floor + 0.5), walk against it, place stone next to it and break it again (no visual change), right-click to open the GUI, break it (fired-clay break particles, TFC ceramic break sound, vessel drops with contents). heavy_core is UNUSABLE, and the fallback is taken automatically, if any of: the vanilla heavy_core texture/model or its vanilla sounds appear, a client-predicted change shows, water renders, the outline is not the 8x8x8 box, a version family lacks it, or the budget report shows a conflict; then add the unusable.json row, rerun datagen (the wall post is chosen), repeat this step with the wall post (outline 8x16x8, collision 24 px tall; place and break stone on each side: an arm may flash for at most one tick and is corrected by the resend) and add the ledger row"
plan:
  - "Moved from PAR-INFRA-23 (2026-11-14): that item verified every class whose CE block was loaded (charcoal forge, bellows, firepit, quern base and handstone, upright barrel sealed and unsealed, composter stage 0) on 1.21.11 and 26.3 and listed the rest with their items. The steps above are the rest; run them per class as each family item loads its block, and record failed carriers in hitbox/unusable.json (then the selector falls back and the ledger needs the fallback row)."
  - "Not here: the held-snow and tool-use prediction checks (PAR-INFRA-51) and the grill and pot block sounds (PAR-INFRA-52)."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-INFRA-53/"
done_criteria:
  - "The outline of each class above matches the hitbox-carriers report on 1.21.11 and 26.x, stand heights match the carrier collision, no-collision classes can be walked through, breaks leave air with only the CE drop"
  - "Containers, placed items, gesture negatives, seal toggles and the heavy_core step pass as written, or the failed carrier has its unusable.json row, the fallback and its ledger row"
---

# PAR-INFRA-53: Hitbox carrier in-game verification of the classes added by family items: piles, rocks, ores, groundcover, placed items, containers, seals, heavy_core

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
