---
id: PAR-METAL-27
title: "Blast furnace textured screen: temperature bar and tank bars"
area: METAL
status: done
wave: 37
deps: [PAR-METAL-15]
size: S
aliases: ["follow-up of PAR-METAL-15"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/BlastFurnaceScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/BlastFurnaceContainer.java"
  - "src/main/resources/assets/tfc/textures/gui/blast_furnace.png"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/blastfurnace/BlastFurnaceScreens.java"
  - "paper/src/main/java/dev/tfp/paper/blastfurnace/BlastFurnaceModule.java"
  - "paper/src/test/java/dev/tfp/paper/blastfurnace/BlastFurnaceScreens*.java"
  - "core/src/main/java/dev/tfp/core/blastfurnace/BlastFurnaceScreenLayout.java"
  - "core/src/test/java/dev/tfp/core/blastfurnace/BlastFurnaceScreenLayoutTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/blastfurnace/BlastFurnaceScreenArt.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/blastfurnace/BlastFurnaceScreenArtTest.java"
ui: true
in_game_steps:
  - "blast furnace: open the screen empty, with a tuyere, lit and hot; screenshots at GUI scale 2 and 3 show the TFC background, the tuyere slot at (152,17), the temperature bar and both tanks"
plan:
  - "PAR-METAL-15 ships the furnace with a plain three-row chest menu (the tuyere slot and name-only read-outs). Replace it with the TfpGui screen on TFC's blast_furnace.png (one slot at (152,17), the temperature bar overlay at (8, 76 - min(51, temp)) 15x5, the tank overlays 10x66 as BlastFurnaceScreen draws them), following the firebox and crucible screens (datagen screen art, core layout, GuiService)."
  - "Done: core BlastFurnaceScreenLayout (4-row chest, tuyere slot 8, DY 1, button columns, frame math of renderCapacityLimitedFillMeter, the indicator and the fluid), datagen BlastFurnaceScreenArt (background re-laid out, thermometer, ore/fuel bars and fills, tinted fluid; configuration/blast_furnace_gui.yml), BlastFurnaceScreens rewritten on GuiService/TfpGui (tuyere input slot committed by a region task, 10 tick reload, tooltip buttons); BlastFurnaceModule widened (passes GuiService). Tests: core BlastFurnaceScreenLayoutTest 8, datagen BlastFurnaceScreenArtTest 6 (pixel for pixel vs TFC), paper BlastFurnaceScreensMockBukkitTest 4. Pending in-game check (container cannot run client); in_game_steps kept. Ledger: feature row updated, screen divergence row rewritten."
tests:
  - "datagen: the background and the bars match TFC's texture pixel for pixel; paper: the tuyere slot and the buttons"
done_criteria:
  - "The blast furnace screen looks like TFC's at GUI scale 2 and 3"
---

# PAR-METAL-27: Blast furnace textured screen

Follow-up of PAR-METAL-15. Follow `plans/parity/PROTOCOL.md`.
