---
id: PAR-HEAT-08
title: "Grill on firepit: placement, five slots, cooking, GUI"
area: HEAT
status: open
wave: 15
deps: [PAR-HEAT-07]
size: M
aliases: ["M3-HEAT-03 (grill)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/GrillScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/grill/**"
  - "core/src/test/java/dev/tfp/core/grill/**"
  - "core/src/test/resources/dev/tfp/core/grill/**"
  - "paper/src/main/java/dev/tfp/paper/grill/**"
  - "paper/src/test/java/dev/tfp/paper/grill/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Grill.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/GrillTest.java"
  - "content/generated/content/craftengine/tfp/devices/grill.yml"
  - "content/generated/content/craftengine/tfp/configuration/devices/grill.yml"
  - "core/src/main/java/dev/tfp/core/cooking/Grill*"
  - "core/src/test/java/dev/tfp/core/cooking/Grill*"
ui: true
in_game_steps:
  - "tfp debug place tfp:firepit[lit=true]"
  - "ce item give Bot tfp:wrought_iron_grill; use on firepit"
  - "add meat; screenshot"
plan:
  - "Wrought iron grill used on a firepit converts it to a grill (keep fuel/temperature); GrillRules lazy cooking per slot; charcoal_grilled/wood_grilled traits; items visible on top via entity_renderer (5 item displays) per CE gui-furniture skill; GUI per catalogue."
tests:
  - "core: GrillGoldenTest exists"
  - "paper: conversion keeps fuel state"
done_criteria:
  - "Grill cooks with the grilled trait (tooltip) and items show on top"
---

# PAR-HEAT-08: Grill on firepit: placement, five slots, cooking, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
