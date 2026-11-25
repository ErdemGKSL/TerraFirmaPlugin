---
id: PAR-HEAT-38
title: "Pot screen bubbles in game: the boiling bubble frames and the water-blue fluid band (the checks PAR-HEAT-37 could not capture)"
area: HEAT
status: open
wave: 50
deps: [PAR-HEAT-37]
size: S
aliases: ["follow-up of PAR-HEAT-37 (2026-11-25, PROTOCOL 6.5): the server was OOM-killed twice during the 60 s boil window"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/PotScreen.java"
files_owned:
  - "docs/verification/PAR-HEAT-38/**"
  - "paper/src/main/java/dev/tfp/paper/pot/gui/**"
  - "paper/src/test/java/dev/tfp/paper/pot/gui/**"
ui: true
in_game_steps:
  - "place a pot (`tfp debug place tfp:pot[lit=false]` on a stone block), put two logs, light it (sneak + hold the firestarter), fill it with a water bucket (right-click; check the tank in the screen), put potato, cabbage and carrot in the inputs"
  - "open the screen and take three screenshots 1 s apart at 30, 40 and 50 s after the fire: the bubble strip over the pot changes rows (TFC: ceil(boilingTicks % 35 / 35 * 20) rows), the flames show while the pot is above 0 C"
  - "screenshot the screen with water in the tank before the soup is done: the fluid band is tinted (TFC tints its 24 x 6 sprite with the fluid colour)"
plan:
  - "Run the steps per PROTOCOL section 6 on a machine with enough memory (the shared container killed the 3 GB server twice). If the bubbles do not move or the band is untinted, fix within paper/pot/gui and datagen potgui through a follow-up item."
tests:
  - "Screenshots + verdict.md in docs/verification/PAR-HEAT-38/"
done_criteria:
  - "The bubble strip changes frames while the pot boils, and the fluid band shows the fluid's colour"
---

# PAR-HEAT-38: Pot screen bubbles in game

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
