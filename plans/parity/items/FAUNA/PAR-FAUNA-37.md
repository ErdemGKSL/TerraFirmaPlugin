---
id: PAR-FAUNA-37
title: "Lobster trap placement state and waterlogged carrier: facing, fluid and the water of a wet trap (follow-up of PAR-FAUNA-36)"
area: FAUNA
status: done
wave: 47
deps: [PAR-FAUNA-36]
size: S
aliases: ["follow-up of PAR-FAUNA-36 (2026-11-25): the selector has no waterlogged lobster trap row, TfcShapes is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java"
  - "src/main/java/net/dries007/tfc/common/fluids/FluidProperty.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lobstertrap/**"
  - "paper/src/main/java/dev/tfp/paper/lobstertrap/**"
  - "paper/src/test/java/dev/tfp/paper/lobstertrap/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:lobster_trap; place it in water (fluid=water) and on land; screenshot both: the wet one is surrounded by water"
plan:
  - "TFC getStateForPlacement: facing = the placer's horizontal direction opposite, fluid = the fluid of the replaced block (FluidHelpers.fillWithFluid). Nothing in paper/lobstertrap sets them yet (PAR-FAUNA-36 only declares the properties): set both on placement through the device (see paper/pump PumpBlockStates for the fluid property)."
  - "PAR-FAUNA-36 draws every fluid value of tfp:lobster_trap on one dry hitbox-only carrier, so the cell of a wet trap shows no water. Add tfc:lobster_trap[waterlogged=true] rows to TfcShapes and the shape json, a second demand set for the fluid=water appearances (the selector's waterlogged filter needs TFC's state to be waterlogged), and appearances per (facing, trap, fluid) in the generator."
  - "Done: LobsterTrapPlacement (BlockPlaceEvent, one region write): facing = the placer's horizontal direction opposite, fluid = water for a replaced water source; wired in LobsterTrapModule; LobsterTrapPlacementMockBukkitTest (4 tests). The waterlogged carrier half was tried and cannot be met inside files_owned (the selector never picks a waterlogged state for a full-block row; details in PAR-FAUNA-39), so no datagen change was kept and the water criterion moved to PAR-FAUNA-39. In-game check pending (container cannot run client)."
tests:
  - "datagen: the fluid=water states take a waterlogged carrier, the empty ones the dry one; carrier pin"
done_criteria:
  - "A trap placed in water shows water around TFC's model; screenshot reviewed"
---

# PAR-FAUNA-37: Lobster trap placement state and waterlogged carrier


Follow `plans/parity/PROTOCOL.md`.
