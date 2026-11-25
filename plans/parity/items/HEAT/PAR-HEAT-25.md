---
id: PAR-HEAT-25
title: "Pot loaded in game: configuration/devices/pot.yml, pot screen art (tfp:gui/pot, thermometer, bubbles) and the in-game soup check"
area: HEAT
status: open
wave: 18
deps: [PAR-HEAT-09, PAR-HEAT-20, PAR-HEAT-24]
size: S
aliases: ["follow-up of PAR-HEAT-09; after PAR-HEAT-24, which also owns DatagenTest.java (its grill line comes first) (loading the pot changes DatagenTest's non-occluding fixture list, and the screen art needs a datagen step; both outside its files_owned; its in-game check moved here)"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/fire_pit_cooking_pot.png"
  - "src/main/java/net/dries007/tfc/client/screen/PotScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/PotContainer.java"
  - "src/main/resources/assets/tfc/blockstates/pot.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/Pot.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PotTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/potgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/potgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
  - "content/generated/content/craftengine/tfp/configuration/devices/pot.yml"
  - "paper/src/main/java/dev/tfp/paper/pot/gui/PotScreenLayout.java"
  - "paper/src/test/java/dev/tfp/paper/pot/gui/**"
  - "docs/verification/PAR-HEAT-09/**"
ui: true
in_game_steps:
  - "op Bot; tfp debug place tfp:pot[lit=true] on a solid block (or: place a firepit, right-click it with `ce item give Bot tfp:ceramic/pot`)"
  - "put fuel in (right-click `tfp:stick_bundle` or logs) and light it; fill water (right-click with a water bucket); right-click to open the screen and put 3 vegetables (`tfp:food/carrot`, `tfp:food/potato`, `tfp:food/cabbage`) into the inputs; screenshot at GUI scale 2 and 3"
  - "wait (tfp time add hours 1, or ~66 s live: 100 pre-boil + 1200 boil ticks), the status button reads `2x Vegetable Soup`"
  - "take soup with a bowl (right-click with `minecraft:bowl`); hover it: the food tooltip shows the mixed nutrition"
plan:
  - "Load the pot: in Pot.java also write `LOADED_OUTPUT = configuration/devices/pot.yml` from `Firepit.loaded(spec)` (the tfp:device behaviour), as the firepit does (PAR-HEAT-19); PotTest checks the loaded entry (template tfp:block/device, the literal entry's states and loot, the only configuration file defining tfp:pot); DatagenTest `writesOreOverlayItemsTemplatesAndRuntimeData` then lists `tfp:firepit`, `tfp:pot` as non-occluding. PAR-HEAT-09 did this in its worktree and reverted it only because DatagenTest is outside its files_owned."
  - "Art (datagen `potgui/PotScreenArt`, one `run` line in Datagen.java): TFC fire_pit_cooking_pot.png (176 x 186) re-laid for the 4-row chest of `paper/pot/gui/PotScreenLayout` (moved down DY = 2; the five input frames moved from (65, 25), (83, 25), (56, 43), (74, 43), (92, 43) to chest slots 3, 4, 12, 13, 14; the player inventory onto the chest's rows; a frame drawn under the fluid button (slot 16) and the status button (slot 25), which stand in for TFC's fluid tooltip area and label), as `tfp:gui/pot`; the 52-frame thermometer strip as `tfp:gui/pot_thermometer` (u 176, v 0, at x 30, y 27); the 21-frame bubble strip `tfp:gui/pot_boiling` (TFC's two 11-wide columns at u 193, v 16..36, drawn 0..20 rows tall at x 131 and 144, y 12). Numbers read from PotScreenLayout; the TFC slot points checked against PotContainer like `GuiArt.checkLargeVessel`. Optionally the pot's fluid colour (TFC tints the 24 x 6 sprite at (131, 33)) as a frame grid per fluid."
  - "Screenshot check per PROTOCOL section 6 (Haiku agent runs, the main session judges); also check the title resolves (no literal <image> text), as PAR-HEAT-20 does for the firepit."
tests:
  - "datagen: the loaded pot entry (PotTest), the pot screen art matches PotScreenLayout (image ids, sizes, ascents, frames)"
done_criteria:
  - "Cooking a soup in-game yields soup items with mixed nutrition (moved from PAR-HEAT-09)"
  - "Pot screen art on TFC's frames at GUI scale 2 and 3, thermometer and bubbles move"
---

# PAR-HEAT-25: Pot loaded in game, pot screen art and the in-game soup check

> Claimed: worker session_011osUpAUVyyokAxf5Kiw72D, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
