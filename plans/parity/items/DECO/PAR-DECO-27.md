---
id: PAR-DECO-27
title: "Chest extras, second part: trapped chest signal, waterlogging, better carrier, automation, custom names, pests, vanilla chest loot (follow-up of PAR-DECO-26)"
area: DECO
status: open
wave: 48
deps: [PAR-DECO-26]
size: M
aliases: ["split from PAR-DECO-26: the lid, the sounds and the cat check shipped there; what needs files outside its globs is here"]
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
  - "ce item give Bot tfp:wood/trapped_chest/oak; place a redstone lamp next to a comparator; open; the signal follows the viewers"
  - "place a hopper under a double chest; items move in and out as one 36 slot container"
plan:
  - "Trapped chest signal: a redstone signal equal to the viewers (0..15, vanilla TrappedChestBlock.getSignal). Needs a CraftEngine signal behaviour (outside the chest globs: integration/craftengine) or a hidden power property; our screen has one viewer, so the signal is 0 or 1 unless the viewer rule changes."
  - "Waterlogging: a waterlogged property with fluid_state water on every variant (DeviceBlockSpec variants carry no fluid; the spec is in datagen/device, outside the globs), placement in water as vanilla."
  - "Carrier: a free vanilla state with a 14 x 14 x 14 box (single) and 15 wide halves instead of the barrier's full cube, picked by the carrier selector (shape rows for the chest in TfcShapes and HitboxDemands, outside the globs)."
  - "Automation: hoppers, droppers and pipes see a chest and a double chest as one 36 slot container (TFCChestBlockEntity.getInventoryHandler) through the device automation framework."
  - "Custom name: an anvil rename or name tag keeps the chest's name and the dropped item copies it."
  - "Pests: opening one of our chests infests it like TFC's PestContainer (PAR-FAUNA-32 entry points)."
  - "Vanilla chest loot (PAR-INFRA-30): TFC's override drops tfp:wood/chest/oak for a vanilla chest block; a vanilla-block break hook over the loot service, with the custom name copied."
  - "Open model check: look at the lid models of PAR-DECO-26 in game (the lid and lock turn and their texture rotations); fix ChestModels if a face is wrong."
tests:
  - "paper: signal, automation moves, name round trip; datagen: waterlogged variants"
done_criteria:
  - "A trapped chest powers a lamp with its viewers, hoppers fill and empty a double chest, a renamed chest keeps its name, the open lid looks like vanilla's"
---

# PAR-DECO-27: Chest extras, second part (follow-up of PAR-DECO-26)

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-26 shipped the `open` state, the lid models (`ChestModels.model(.., open)`), `ChestLid` (state write and sounds) and the sitting-cat check.
