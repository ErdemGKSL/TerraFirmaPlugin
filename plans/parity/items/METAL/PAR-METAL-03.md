---
id: PAR-METAL-03
title: "Anvil blocks: rock anvils and metal anvils with items shown on top"
area: METAL
status: blocked
wave: 28
deps: [PAR-INFRA-02, PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["M4-ANV-02 (blocks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockAnvilBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockConvertableToAnvilBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/anvil/**"
  - "core/src/test/java/dev/tfp/core/anvil/**"
  - "core/src/test/resources/dev/tfp/core/anvil/**"
  - "paper/src/main/java/dev/tfp/paper/anvil/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/anvil/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/anvil/**"
  - "content/generated/content/craftengine/tfp/configuration/anvil/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/anvil/copper; place"
  - "right-click with a copper ingot; screenshot"
plan:
  - "Blocked (2026-11-19): the item cannot place or draw an anvil from files_owned. The metal anvil (TFC outline box(0,0,3,16,11,13) / box(3,0,0,13,11,16) per facing, 9 tiers) and the rock anvil (box(0,0,0,16,14,16), generated today as a plain family block by RockFamilies) are non-full-cube blocks, so each needs hitbox carrier demands and TFC shape rows that this item does not own. Needed, none in files_owned: (1) tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java: demands for tfp:metal/anvil/<metal>[facing x|z] (hitbox-only TRANSPARENT like the barrel, a weight after the grill's -7) and for the rock anvil outline; (2) tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java: specs for tfc:metal/anvil/* (AnvilBlock SHAPE_X/SHAPE_Z) and tfc:rock/anvil/* (RockAnvilBlock.SHAPE), plus the pinned selection tests in tools/datagen/src/test/java/dev/tfp/datagen/hitbox/ (HitboxCarrierTest, HitboxAllocationTest, GeneratedCarrierTest) and tools/datagen/src/test/java/dev/tfp/datagen/coverage/BlockCoverageTest.java; (3) tools/datagen/src/main/java/dev/tfp/datagen/RockFamilies.java (and its yaml/test) so the rock anvil entry carries the tfp:device behaviour and the hammer-conversion target; MetalFamilies.EXCLUDED already leaves the metal anvil to this item; (4) tools/datagen/src/main/java/dev/tfp/datagen/Datagen.java hub line is allowed. Add those paths to files_owned (or a prior infra item) and the rest fits: core AnvilState/slot rules (4 slots: main 1, second 1, hammer 1 filtered by tfc:tools/hammer, flux 64 filtered by tfc:welding_flux), paper AnvilDevice kind (DROP removal; shift right click insert order catalyst, main, second; empty hand extract main then second; hammer weld hook left to PAR-METAL-06; no hand open GUI left to PAR-METAL-04/05), RockConvertableToAnvil listener (hammer on the top face of a raw igneous rock with no chisel in the other hand and air above), entity_renderer item displays for the two inputs on the top face, tests and the in-game check."
  - "Rock anvil: hammer on raw igneous rock converts it (tier 0); metal anvils per tier (9) placeable; inventory (input, second input, hammer, flux) stored in the device; dynamic entity_renderer item displays for the two inputs on the top face."
tests:
  - "paper: conversion with a hammer"
  - "tools/datagen: anvil states"
done_criteria:
  - "Placing a copper anvil and an ingot shows the ingot on top"
---

# PAR-METAL-03: Anvil blocks: rock anvils and metal anvils with items shown on top


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
