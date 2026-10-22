---
id: PAR-METAL-03
title: "Anvil blocks: rock anvils and metal anvils with items shown on top"
area: METAL
status: open
wave: 28
deps: [PAR-INFRA-02, PAR-INFRA-08]
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
  - "Rock anvil: hammer on raw igneous rock converts it (tier 0); metal anvils per tier (9) placeable; inventory (input, second input, hammer, flux) stored in the device; dynamic entity_renderer item displays for the two inputs on the top face."
tests:
  - "paper: conversion with a hammer"
  - "tools/datagen: anvil states"
done_criteria:
  - "Placing a copper anvil and an ingot shows the ingot on top"
---

# PAR-METAL-03: Anvil blocks: rock anvils and metal anvils with items shown on top

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
