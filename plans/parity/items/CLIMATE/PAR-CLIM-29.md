---
id: PAR-CLIM-29
title: "Climate instrument sources: blast furnace, bloomery and the barrel recipe timer"
area: CLIMATE
status: done
wave: 37
deps: [PAR-CLIM-28, PAR-METAL-14, PAR-METAL-15]
size: S
aliases: ["split from PAR-CLIM-28"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/instrument/**"
  - "paper/src/test/java/dev/tfp/paper/instrument/**"
ui: false
in_game_steps: []
plan:
  - "PAR-CLIM-28 wired the firebox (heat, kiln timer) and crucible (heat) through instrument/DeviceSources. Add the blast furnace heat (IHeatable) and the bloomery timer (IRecipeTimer) once their device kinds exist, and the barrel's sealed-recipe timer (duration of the matching recipe, remaining = duration - (now - recipeTick)) through the barrel recipe lookup."
  - "Done: DeviceSources reads BlastFurnaceData (heat), BloomeryData (timer) and BarrelData (timer via BarrelDurations: first matching sealed recipe duration over an own BarrelRecipeBook, no barrel file touched); InstrumentReadoutTest.deviceSourcesReadTheBlastFurnaceTheBloomeryAndTheBarrel; ledger feature row and one divergence row. Pending in-game check (container cannot run client)."
tests:
  - "paper: DeviceSources cases for each new state type, as InstrumentReadoutTest.deviceSourcesReadTheFireboxAndTheCrucible"
done_criteria:
  - "A thermometer on a blast furnace reads its heat; a clock behind a bloomery or barrel offers timer mode"
---

# PAR-CLIM-29: Climate instrument sources: blast furnace, bloomery and the barrel recipe timer

Follow-up of PAR-CLIM-28. Follow `plans/parity/PROTOCOL.md`.
