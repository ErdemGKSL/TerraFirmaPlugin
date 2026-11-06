---
id: PAR-DECO-16
title: "Fire brick extras and smooth mud bricks: reinforced fire bricks, fire brick shelf, fireproof door, fire clay block"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-02, PAR-DECO-05, PAR-DECO-07, PAR-METAL-15]
size: S
aliases: ["parity audit 2026-11-06: TFCBlocks entries named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ShelfBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ShelfBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCDoorBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java"
  - "src/main/resources/assets/tfc/textures/block"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/firebrick/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/firebrick/**"
  - "content/generated/content/craftengine/tfp/configuration/firebrick/**"
  - "paper/src/main/java/dev/tfp/paper/firebrick/**"
  - "paper/src/test/java/dev/tfp/paper/firebrick/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:reinforced_fire_bricks 4; ce item give Bot tfp:fire_brick_shelf; ce item give Bot tfp:fireproof_door; ce item give Bot tfp:fire_clay_block; ce item give Bot tfp:smooth_mud_bricks"
  - "Place each next to plain fire bricks at noon; put an ingot on the fire brick shelf; open and close the fireproof door; screenshot at GUI scale 2"
plan:
  - "Current (audit 2026-11-06): TFCBlocks registers reinforced_fire_bricks, fire_brick_shelf (a ShelfBlock with the shared shelf block entity), fireproof_door (TFCDoorBlock, copper block set and sound), fire_clay_block and smooth_mud_bricks; PAR-DECO-07 lists fire bricks and mud bricks only, PAR-DECO-05 the wood shelves only, PAR-DECO-02 the wood doors only, so none of the five has an owner."
  - "Generate the five CE blocks with TFC names, textures, models, strength, tool requirement, sounds and loot (self); the shelf and the door reuse PAR-DECO-05's shelf behaviour and PAR-DECO-02's door behaviour through their shared templates (no copies), with hitbox carriers from PAR-INFRA-13."
  - "Check that reinforced_fire_bricks counts as blast furnace insulation through the TFC tag (PAR-METAL-15's multiblock scan) and that the crafting and heating recipes for the five blocks resolve."
tests:
  - "tools/datagen: five blocks with TFC settings (hardness, resistance, sound, tool) and loot"
  - "paper: MockBukkit shelf put/take on the fire brick shelf, door open/close"
done_criteria:
  - "All five blocks place, drop and behave as in TFC; reinforced fire bricks are accepted by the blast furnace scan; screenshots pass"
---

# PAR-DECO-16: Fire brick extras and smooth mud bricks: reinforced fire bricks, fire brick shelf, fireproof door, fire clay block

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
