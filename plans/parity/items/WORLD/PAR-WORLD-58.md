---
id: PAR-WORLD-58
title: "Waterlogged groundcover: TFC's FLUID property on the CE loose rocks, small ores and groundcover, wet hitbox rows, and the worldgen writing them under water"
area: WORLD
status: open
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
  - "TFC's groundcover, loose rocks and small ores carry FLUID (empty, water, salt water). PAR-WORLD-31 generated them without it; TfcTerrain.coverData keeps the waterloggable vanilla placeholder for a waterlogged decision (clams, mussels, seaweed, loose rocks under water)."
  - "Add waterlogged=true rows for loose_rock_1..3, small_ore and groundcover_<shape> to TfcShapes (the selector then only offers waterloggable carriers), a CE `waterlogged` property (settings fluid_state water for the true variants) in GroundcoverCeBlocks with demands per wet row, then write the CE block for waterlogged decisions in TfcTerrain.coverData. Salt water: CE has one water fluid state; record the divergence."
tests:
  - "tools/datagen: every groundcover block has waterlogged=false/true variants on carriers of the matching wetness; hitbox report has no overflow"
done_criteria:
  - "Worldgen writes CE groundcover for waterlogged decisions; no vanilla placeholder remains for groundcover"
---

# PAR-WORLD-58: Waterlogged groundcover

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
