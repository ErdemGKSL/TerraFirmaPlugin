---
id: PAR-WORLD-58
title: "Waterlogged groundcover: TFC's FLUID property on the CE loose rocks, small ores and groundcover, wet hitbox rows, and the worldgen writing them under water"
area: WORLD
status: blocked
wave: 23
deps: [PAR-WORLD-31, PAR-WORLD-32]
size: M
aliases: ["PAR-WORLD-31/32 follow-up: the CE groundcover blocks have no fluid state, so worldgen keeps vanilla placeholders (sea pickle, seagrass, buttons) for waterlogged decisions"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/fluids/IFluidLoggable.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/groundcover/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/groundcover/**"
  - "content/generated/content/craftengine/tfp/configuration/groundcover/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
ui: false
in_game_steps: []
plan:
  - "BLOCKED (2026-11-25, worker): no waterlogged carrier fits. Ranking the wet groundcover outlines (TfcState waterlogged=true on tfc:rock/loose[count=1..3], tfc:ore/small, tfc:groundcover[shape=*], 1.21 family, every state free) leaves only fluid states of minecraft:candle (score 0.13..0.33; every state is tfp:candle's vanilla-look twin, PAR-HEAT-26) and closed bottom iron trapdoors (0.05..0.22, 3 px collision where TFC has none); the coral fans and sea pickles are the corals' twins (PAR-WORLD-50). Every wet demand would fall to a hitbox-only trapdoor that players bump into under water, a silent degrade the selector refuses without the owner. Also needed outside files_owned: hitbox/HitboxCarrier.java (reject dry carriers for a waterlogged non-twin row, as it does for twins). Unblock: an owner decision on the carrier (e.g. hitbox-only on the waterlogged trapdoors with the collision divergence recorded, or a CraftEngine entity-only block in a water source), then add HitboxCarrier.java to files_owned and do the plan below."
  - "TFC's groundcover, loose rocks and small ores carry FLUID (empty, water, salt water). PAR-WORLD-31 generated them without it; TfcTerrain.coverData keeps the waterloggable vanilla placeholder for a waterlogged decision (clams, mussels, seaweed, loose rocks under water)."
  - "Add waterlogged=true rows for loose_rock_1..3, small_ore and groundcover_<shape> to TfcShapes (the selector then only offers waterloggable carriers), a CE `waterlogged` property (settings fluid_state water for the true variants) in GroundcoverCeBlocks with demands per wet row, then write the CE block for waterlogged decisions in TfcTerrain.coverData. Salt water: CE has one water fluid state; record the divergence."
tests:
  - "tools/datagen: every groundcover block has waterlogged=false/true variants on carriers of the matching wetness; hitbox report has no overflow"
done_criteria:
  - "Worldgen writes CE groundcover for waterlogged decisions; no vanilla placeholder remains for groundcover"
---

# PAR-WORLD-58: Waterlogged groundcover

> Claimed: worker session_011osUp5nmwNAYj7g53gEDAm, 2026-11-25

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
