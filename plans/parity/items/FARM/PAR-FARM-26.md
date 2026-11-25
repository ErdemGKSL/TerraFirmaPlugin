---
id: PAR-FARM-26
title: "CraftEngine farmland blocks per soil with hydration visual states (tfp:farmland/<soil>)"
area: FARM
status: open
wave: 28
deps: [PAR-FARM-01]
size: M
aliases: ["STATUS.md row 'PAR-FARM-19 CraftEngine farmland blocks per soil' had no item file and clashed with the worldgen item PAR-FARM-19; the farmland blocks are this item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/farm/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/farm/**"
ui: true
in_game_steps:
  - "hoe a dirt block; screenshot tfp:farmland/<soil> with the hydration look; place water nearby and check the moist look"
plan:
  - "Datagen tfp:farmland/<soil> for the TFC soils (dry/wet visual states by hydration on the farmland carrier of the hitbox allocator), the SoilListener already tills to those ids; hitbox rows in the shared hitbox files."
tests:
  - "datagen: ids, states, carrier pins"
done_criteria:
  - "Hoeing dirt gives farmland that crops can be planted on"
---

# PAR-FARM-26: Farmland CraftEngine blocks per soil

Follow-up of PAR-FARM-01 (PAR-FARM-20 needs it). Follow `plans/parity/PROTOCOL.md`.
