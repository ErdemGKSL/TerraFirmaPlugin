---
id: PAR-HEAT-12
title: "Firebox, stove and stove pot"
area: HEAT
status: done
wave: 16
deps: [PAR-HEAT-07, PAR-INFRA-13]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StovePotBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/FireboxScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/FireboxContainer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/firebox/**"
  - "core/src/test/java/dev/tfp/core/firebox/**"
  - "core/src/test/resources/dev/tfp/core/firebox/**"
  - "paper/src/main/java/dev/tfp/paper/firebox/**"
  - "paper/src/test/java/dev/tfp/paper/firebox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/firebox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/firebox/**"
  - "content/generated/content/craftengine/tfp/configuration/firebox/**"
ui: false
in_game_steps: []
plan:
  - "Firebox heats the block above (stove, crucible, pot) through the IHeatConsumer interface; reuse FirepitRules for its burn; GUI per FireboxScreen; stove/stove pot blocks as TFC."
  - "PROTOCOL 3 + 6.5: the firebox CE block (full cube on the solid pool) must be declared in HitboxDemands.FULL_CUBE_POOLS (GeneratedCarrierTest), and the stove and stove pot need hitbox demands and the firepit / pot device kinds, all outside files_owned: the Datagen run line of firebox/FireboxBlock, the copper sounds, the stove, the stove pot and the in-game screen check (old steps: place tfp:firebox[lit=true], right-click, screenshot the GUI) moved to PAR-HEAT-31; ui set false. The crucible self-test, the kiln heating of placed items, hot floor and comparator need PAR-METAL-12 and PAR-POT-01: moved to PAR-HEAT-32. The BlockCoverage owner line firebox|stove|stove_pot moved to PAR-HEAT-31 (BlockCoverageTest refuses a done owner)."
  - "Done: core/firebox (FireboxData, FireboxSim lazy serverTick with heat runs, FireboxKiln flood fill, FireboxScreenLayout); paper/firebox (FireboxDevice kind tfp:firebox, HeatConsumers service, FireboxModule: FireLightable, chunk-load callback, screen; FireboxScreens + FireboxLink; PaperFireboxEffects); datagen firebox/FireboxScreenArt (configuration/firebox/firebox_gui.yml, run) and firebox/FireboxBlock (firebox.yml, tested, run by PAR-HEAT-31). Tests: core FireboxSimTest 11 (TFC golden 289 rows), FireboxKilnTest 7; paper FireboxMockBukkitTest 8; datagen FireboxBlockTest 3. Ledger row and divergences."
tests:
  - "core: firebox heat transfer golden"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-32 (firebox under a crucible heats it, self-test: the crucible does not exist yet). Covered here: the heat the firebox gives the block above (core FireboxSimTest golden heatSum/heatTicks columns and the CrucibleTemperature coupling; paper FireboxMockBukkitTest: a registered HeatConsumers.Consumer above gets every tick of heat)"
  - "Deferred check tracked in PAR-HEAT-31 (the firebox block loaded and its screen lined up at GUI scale 2 and 3)"
---

# PAR-HEAT-12: Firebox, stove and stove pot

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
