---
id: PAR-DECO-26
title: "Chest extras: lid animation, trapped chest signal, waterlogging, better carrier, automation, custom names (follow-up of PAR-DECO-04)"
area: DECO
status: open
wave: 47
deps: [PAR-DECO-04]
size: M
aliases: ["split from PAR-DECO-04: the chest blocks, models, items, the 18 and 36 slot screens and the pairing exist; what needs the device framework, the integration adapter or other items' files is here"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrappedChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java"
  - "src/main/resources/data/minecraft/loot_table/blocks/chest.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/chest/**"
  - "core/src/test/java/dev/tfp/core/chest/**"
  - "paper/src/main/java/dev/tfp/paper/chest/**"
  - "paper/src/test/java/dev/tfp/paper/chest/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/chest/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/chest/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/chest/oak 2; place side by side; open; watch the lid; screenshot"
  - "ce item give Bot tfp:wood/trapped_chest/oak; place a redstone lamp next to a comparator; open; the signal follows the viewers"
plan:
  - "Lid animation: while a chest has a viewer the display shows an open model (lid rotated 90 degrees about its back edge, as vanilla's openness 1; one step is enough), swapped back on close; the open and close sounds (block.chest.open / close) at the chest centre."
  - "Trapped chest: a redstone signal equal to the viewers (0..15, vanilla TrappedChestBlock.getSignal), through a CraftEngine signal behaviour for a device state or a hidden power property updated when a viewer opens or closes."
  - "Waterlogging: a waterlogged property with fluid_state water on every variant (DeviceBlockSpec variants carry no fluid; extend the spec or write the entry literally), placement in water as vanilla."
  - "Carrier: a free vanilla state with a 14 x 14 x 14 box (single) and 15 wide halves instead of the barrier's full cube, picked by the carrier selector (shape rows for the chest in TfcShapes and HitboxDemands); crack stages and break particles like the renderer cubes."
  - "Automation: hoppers, droppers and pipes see a chest and a double chest as one 36 slot container (TFCChestBlockEntity.getInventoryHandler, the size filter on insert) through the device automation framework."
  - "Custom name: an anvil rename or name tag keeps the chest's name (the title of the screen) and the dropped item copies it (TFC's chest loot table)."
  - "Cat check: a sitting cat above a chest blocks opening (ChestBlock.isChestBlockedAt)."
  - "Pests: opening one of our chests infests it like TFC's PestContainer (PAR-FAUNA-32 entry points)."
  - "Vanilla chest loot (PAR-INFRA-30): TFC's override drops tfp:wood/chest/oak for a vanilla chest block; a vanilla-block break hook over the loot service, with the custom name copied."
tests:
  - "paper: viewer count and lid, signal, automation moves, name round trip; datagen: open models, waterlogged variants"
done_criteria:
  - "An oak chest's lid opens for the viewer, a trapped chest powers a lamp with its viewers, hoppers fill and empty a double chest, a renamed chest keeps its name"
---

# PAR-DECO-26: Chest extras (follow-up of PAR-DECO-04)

> Claimed: claude session_011osUp8LgspZkGFjFNWFDqQ (DECO helper), 2026-11-25

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-04 shipped the blocks (`dev.tfp.datagen.chest.ChestBlocks`, `configuration/chest/<wood>.yml`), the models of `ChestModels`, the device kinds, the screens and the pairing (`paper/chest`, `core/chest`).
