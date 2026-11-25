---
id: PAR-DECO-02
title: "Wood logic: doors, trapdoors, fence gates, buttons, pressure plates"
area: DECO
status: done
wave: 45
deps: [PAR-DECO-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCDoorBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrapDoorBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCFenceGateBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCWoodButtonBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCPressurePlateBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/woodlogic/**"
  - "core/src/test/java/dev/tfp/core/woodlogic/**"
  - "core/src/test/resources/dev/tfp/core/woodlogic/**"
  - "paper/src/main/java/dev/tfp/paper/woodlogic/**"
  - "paper/src/test/java/dev/tfp/paper/woodlogic/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/woodlogic/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/woodlogic/**"
ui: true
in_game_steps:
  - "tfp debug showcase wood"
  - "open a door, press a button; screenshot"
plan:
  - "Use CE built-in behaviours (door, trapdoor, fence_gate, button, pressure_plate) per craftengine-blocks skill; open/close sounds per wood."
  - "Done: the five kinds of the 20 woods are CE blocks in configuration/woodfamily/<wood>.yml (WoodLogicBlocks, called from WoodFamilies.writeBlocks) on the built-in door_block, trapdoor_block, fence_gate_block, button_block (+ face_attached_horizontal_directional_block) and pressure_plate_block behaviours, hitbox-only on shared vanilla twins with TFC's models as item displays; vanilla shape rows, carrier policy, TfcShapes rows, tests (WoodFamiliesTest woodLogicBlocksUseTheBuiltInBehavioursOnSharedHitboxCarriers) and ledger rows. Pending in-game check (door, button, plate in the showcase; display turns of x-rotated models)."
tests:
  - "tools/datagen: behaviour per kind"
done_criteria:
  - "Each kind works in the showcase"
---

# PAR-DECO-02: Wood logic: doors, trapdoors, fence gates, buttons, pressure plates


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
