---
id: PAR-POT-10
title: "Small vessel screen art: the textured four-slot GUI"
area: POTTERY
status: done
wave: 25
deps: [PAR-POT-04]
size: S
aliases: ["M3-POT-02 (vessel screen art)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/SmallVesselInventoryScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/SmallVesselInventoryContainer.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/vesselgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/vesselgui/**"
  - "core/src/main/java/dev/tfp/core/gui/VesselScreenLayout.java"
  - "core/src/test/java/dev/tfp/core/gui/VesselScreenLayoutTest.java"
  - "paper/src/main/java/dev/tfp/paper/vessel/VesselScreens.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/vessel"
  - "tfp debug vessel open"
  - "screenshot at GUI scale 2 and 3"
plan:
  - "PAR-POT-04 opens the vessel's four slots in a plain three-row chest (`VesselScreens`, no background layer). Add TFC's `small_vessel_inventory.png` re-laid out by datagen (catalogue row `m3-pot-02-small-vessel`: slots at (71,23), (89,23), (71,41), (89,41)), the `tfp:gui/small_vessel` image in `gui.yml`, a core layout (`SlotLayout.of`) for the four chest slots, and switch `VesselScreens` to the background layer and those slots (replace `SLOTS`)."
  - "Done: VesselScreenLayout (core), datagen vesselgui/VesselScreenArt (re-lays small_inventory.png as tfp:gui/small_vessel, configuration/pottery/vessel_gui.yml), VesselScreens on the background layer with slots 3, 4, 12, 13; tests VesselScreenLayoutTest (2), VesselScreenArtTest (3, pixel-equal slot frames), VesselMockBukkitTest (14). Pending in-game check (container cannot run client): steps stay in in_game_steps."
tests:
  - "datagen: GuiArtTest style check of the new image and slot frames"
  - "core: layout slots equal TFC's coordinates"
done_criteria:
  - "The vessel screen shows TFC's small vessel art with the four slots on the art's slot frames at GUI scales 2 and 3"
---

# PAR-POT-10: Small vessel screen art: the textured four-slot GUI

Follow-up of PAR-POT-04, which ships the working four-slot screen without art. Follow `plans/parity/PROTOCOL.md`.
