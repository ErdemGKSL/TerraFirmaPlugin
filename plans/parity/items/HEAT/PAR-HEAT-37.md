---
id: PAR-HEAT-37
title: "Pot in game: the in-game soup check, the pot screen at GUI scale 2 and 3, TFC's pot models and the fluid colour"
area: HEAT
status: open
wave: 19
deps: [PAR-HEAT-25]
size: S
aliases: ["moved from PAR-HEAT-25 (2026-11-25, PROTOCOL 6.5): the pot entry and the screen art are generated and unit-tested; no server or client run was made"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/PotScreen.java"
  - "src/main/resources/assets/tfc/blockstates/pot.json"
  - "src/main/resources/assets/tfc/models/block/firepit_pot.json"
files_owned:
  - "docs/verification/PAR-HEAT-37/**"
  - "docs/verification/PAR-HEAT-09/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Pot.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PotTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/potgui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/potgui/**"
  - "paper/src/main/java/dev/tfp/paper/pot/**"
  - "paper/src/test/java/dev/tfp/paper/pot/**"
ui: true
in_game_steps:
  - "op Bot; tfp debug place tfp:pot[lit=true] on a solid block (or: place a firepit, right-click it with `ce item give Bot tfp:ceramic/pot`)"
  - "put fuel in (right-click `tfp:stick_bundle` or logs) and light it; fill water (right-click with a water bucket); right-click to open the screen and put 3 vegetables (`tfp:food/carrot`, `tfp:food/potato`, `tfp:food/cabbage`) into the inputs; screenshot at GUI scale 2 and 3; check the title resolves (no literal <image> text), the five input frames sit on the chest slots, the thermometer, the three flames and the bubbles move"
  - "wait (tfp time add hours 1, or ~66 s live: 100 pre-boil + 1200 boil ticks), the status button reads `2x Vegetable Soup`"
  - "take soup with a bowl (right-click with `minecraft:bowl`); hover it: the food tooltip shows the mixed nutrition"
plan:
  - "Run the steps per PROTOCOL section 6 (a Haiku agent runs, the main session judges) with CraftEngine self-host so the pack applies. The pot is a placeholder look today (the carriers' vanilla look, as before PAR-HEAT-24 for the grill): give it TFC's models as PAR-HEAT-24 did for the grill (merged lit and unlit models, entity_renderer look on a hitbox-only carrier, hitbox demands) and draw the tinted fluid band (TFC fills a 24 x 6 sprite at (131, 33) with the fluid colour; a frame grid per fluid, or the jam texture) if the look reads empty without it."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-HEAT-37/ (moves the pending soup check of PAR-HEAT-09)"
done_criteria:
  - "Cooking a soup in game yields soup items with mixed nutrition"
  - "Pot screen art on TFC's frames at GUI scale 2 and 3, the thermometer, flames and bubbles move"
---

# PAR-HEAT-37: Pot in game

> Claimed: worker session_011osUpAUVyyokAxf5Kiw72D, 2026-11-25

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
