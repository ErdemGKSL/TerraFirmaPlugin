---
id: PAR-FAUNA-27
title: "Nest box block and ground-laying hand-off (follow-up of PAR-FAUNA-07)"
area: FAUNA
status: open
wave: 44
deps: [PAR-FAUNA-07]
size: M
aliases: ["follow-up of PAR-FAUNA-07 (2026-11-25): HitboxDemands and ProductsModule are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java"
  - "src/main/resources/assets/tfc/blockstates/nest_box.json"
  - "src/main/resources/assets/tfc/models/block/nest_box.json"
  - "src/main/resources/assets/tfc/models/item/nest_box.json"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/nestbox/NestBoxBlock*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/nestbox/NestBoxBlock*.java"
  - "content/generated/content/craftengine/tfp/configuration/nestbox/**"
  - "paper/src/main/java/dev/tfp/paper/products/ProductsModule.java"
  - "paper/src/test/java/dev/tfp/paper/products/**"
  - "paper/src/main/java/dev/tfp/paper/nestbox/**"
  - "paper/src/test/java/dev/tfp/paper/nestbox/**"
  - "docs/verification/PAR-FAUNA-27/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:nest_box; place it on a floor; screenshot the block (TFC thatch nest, no floor showing through); open it with a right click (GUI scale 2 and 3)"
  - "spawn a hen and a rooster next to each other, feed both until familiar; tfp time add days 10; the hen sits in the nest and lays an egg into a slot; tfp time add days 10 more: the egg hatches a chick at the nest and the slot empties"
plan:
  - "PAR-FAUNA-07 ships the runtime (device kind tfp:nest_box, screen, seeker, hatching, screen art tfp:gui/nest_box) but the CraftEngine block does not exist: its hitbox carrier must come from the selector (HardcodedCarrierTest) and HitboxDemands.java is outside PAR-FAUNA-07's files_owned. tfc-shapes.json already has tfc:nest_box (box 2,0,2..14,5,14)."
  - "Hitbox: add the demand for tfp:nest_box (one appearance, TFC's model block/nest_box) and pin it in GeneratedCarrierTest. Block entry: datagen nestbox/NestBoxBlock writes configuration/nestbox/nest_box.yml through DeviceBlockSpec + DeviceYaml.templated (tfp:device behaviour, TFC's strength/sounds/tool from TFCBlocks NEST_BOX, loot self, block item, TFC's model and its thatch texture, block.tfp.nest_box in every TFC locale; bottom-supported survival) and its Datagen run line (copy powderkeg/PowderkegBlock)."
  - "Hand-off: PAR-FAUNA-06's LayingBehaviour (ProductsModule) still lays a ready hen's egg on the ground on its own check, so a hen can lay before the nest box sees her. Make it skip a hen that PaperNestHens.holds (seated) or that has a known nest box with a free slot within NestSeeker.RADIUS (TFC: a hen with a NEST_BOX_MEMORY walks to the nest instead), and write the species into ground-laid fertile eggs (NestEggs.fertile) so they hatch as the right species."
tests:
  - "datagen: NestBoxBlockTest (the entry, TFC's model and texture, carrier from the allocation, loot), GeneratedCarrierTest pin"
  - "paper: the ground laying skips a seated hen and a hen with a free nest in reach"
done_criteria:
  - "A placed nest box looks like TFC's; a ready hen sits in it and lays there instead of on the ground; screenshot reviewed"
---

# PAR-FAUNA-27: Nest box block and ground-laying hand-off

> Claimed: worker session_011osUp8LArwstSDt7X82o4E, 2026-11-25

Follow `plans/parity/PROTOCOL.md`.
