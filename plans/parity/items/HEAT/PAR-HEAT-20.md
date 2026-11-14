---
id: PAR-HEAT-20
title: "Firepit screen art and block wiring: datagen art for tfp:gui/firepit and its thermometer, FirepitDevice opens the screen, in-game check"
area: HEAT
status: open
wave: 15
deps: [PAR-HEAT-07, PAR-HEAT-19]
size: S
aliases: ["follow-up of PAR-HEAT-07 (art in datagen and the device's screen step are outside its files_owned)"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/fire_pit.png"
  - "src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/FirepitContainer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/firepitgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/firepitgui/**"
  - "paper/src/main/java/dev/tfp/paper/firepit/FirepitDevice.java"
  - "paper/src/main/java/dev/tfp/paper/firepit/FirepitModule.java"
  - "paper/src/main/java/dev/tfp/paper/firepit/FirepitScreenLink.java"
  - "paper/src/test/java/dev/tfp/paper/firepit/FirepitMockBukkitTest.java"
  - "paper/src/test/java/dev/tfp/paper/firepit/FirepitScreenLinkMockBukkitTest.java"
  - "paper/src/test/java/dev/tfp/paper/firepit/gui/FirepitScreenLayoutContentTest.java"
  - "docs/verification/PAR-HEAT-07/**"
ui: true
in_game_steps:
  - "op Bot; tfp debug firepitgui 0, 400 and 1200: screenshots at GUI scale 2 and 3 (slots on TFC's frames, thermometer indicator moving up)"
  - "tfp debug place tfp:firepit[lit=true]"
  - "right-click it"
  - "screenshot at GUI scale 2 and 3; put an oak log in the top fuel slot (it drops to the bottom slot), raw beef into the input; hover the thermometer column for the temperature"
plan:
  - "Art (datagen `firepitgui/FirepitScreenArt`, one `run` line in Datagen.java): TFC fire_pit.png (176 x 186) re-laid out for the 4-row chest of `paper/firepit/gui/FirepitScreenLayout` (moved down DY = 2; the input frame moved from (80, 31) to chest slot 4 (80, 18); the two output frames from (71, 59) and (89, 59) to chest slots 21 (62, 54) and 23 (98, 54), the arrows redrawn to point at them; the player inventory onto the chest's rows) as `tfp:gui/firepit`, and the 52-frame thermometer strip from the sprite at u 176, v 0 at y 27 as `tfp:gui/firepit_thermometer` (`configuration/firepit_gui.yml`, the numbers read from `FirepitScreenLayout`, the TFC slot points checked against FirepitContainer like `GuiArt.checkLargeVessel`). A content test compares the generated images with the layout constants."
  - "Block: replace the stand-in screen step in `FirepitDevice`'s use handler (input/outputs by hand) with opening `FirepitScreens` over a `FirepitScreenLink` (implements `paper/firepit/gui/FirepitLink` over the device record: snapshot after catch-up, commit = TFC setAndUpdateSlots then cascadeFuelSlots, isItemValid = the device's own), keyed by block position; device changes while a screen is open go through `FirepitScreens.edit`/`reload`."
tests:
  - "datagen: the firepit screen art matches FirepitScreenLayout (image ids, sizes, ascents, thermometer frames)"
  - "paper: right-clicking a firepit opens the screen; a log put in the top slot is in device slot 0 after the commit"
done_criteria:
  - "Aligned screenshot of the firepit GUI at GUI scale 2 and 3, opened from the block (verification in docs/verification/PAR-HEAT-07/)"
---

# PAR-HEAT-20: Firepit screen art and block wiring

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
