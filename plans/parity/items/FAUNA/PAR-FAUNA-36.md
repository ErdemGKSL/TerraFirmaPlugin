---
id: PAR-FAUNA-36
title: "Lobster trap block: the CE block with trap, facing and fluid states (follow-up of PAR-FAUNA-19)"
area: FAUNA
status: done
wave: 46
deps: [PAR-FAUNA-19, PAR-INFRA-13]
size: S
aliases: ["follow-up of PAR-FAUNA-19 (2026-11-25): HitboxDemands is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java"
  - "src/main/resources/assets/tfc/blockstates/lobster_trap.json"
  - "src/main/resources/assets/tfc/models/block/lobster_trap"
  - "src/main/resources/data/tfc/loot_table/blocks/lobster_trap.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/coverage/BlockCoverage.java"
  - "content/generated/content/craftengine/tfp/configuration/lobstertrap/**"
  - "docs/verification/PAR-FAUNA-36/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:lobster_trap; place it underwater facing a direction; screenshot the block open, baited (right click with a fish) and closed (tfp debug animal spawn lobster next to it; wait)"
  - "right click with an empty hand: the catch goes into the inventory; break the trap with a catch inside: it drops"
plan:
  - "PAR-FAUNA-19 ships the runtime (device kind tfp:lobster_trap in paper/lobstertrap: bait, catch, take, the animals' goal, property trap=open|baited|closed read and written by the device) but the CraftEngine block does not exist: its hitbox carrier must come from the selector, and HitboxDemands.java is outside PAR-FAUNA-19's files_owned. tfc-shapes.json already has tfc:lobster_trap (Shapes.block, noOcclusion). Generate tfp:lobster_trap with properties facing (4), trap (open, baited, closed) and fluid (empty, water: TFC FluidProperty WATER) on the tfp:device behaviour, TFC's models, textures and name, strength 3 metal sounds, the loot table (the trap itself), BlockCoverage owner moved to this item. Waterlogging: the fluid property must keep the block in water the way the other fluid-loggable devices do."
  - "Done: datagen lobstertrap/LobsterTrapBlock (tfp:lobster_trap: facing, trap, fluid = 24 states; 12 hitbox-only appearances on the selector's shared carrier, TFC's three models as item displays, TFC settings, loot, item and name), HitboxDemands rows (weight -103) and the GeneratedCarrierTest pin, BlockCoverage owner rule removed, LobsterTrapBlockTest 3 green. A wet trap's carrier is dry (follow-up PAR-FAUNA-37). Screenshot check pending (container cannot run client)."
tests:
  - "datagen: the block exists with the three trap states and TFC's models; its carrier comes from the selector; coverage names the generator"
done_criteria:
  - "The trap places, baits, catches, takes and drops; screenshot reviewed"
---

# PAR-FAUNA-36: Lobster trap block

Follow `plans/parity/PROTOCOL.md`.
