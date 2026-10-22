---
id: PAR-POT-07
title: "Jugs and glass bottles: fill, drink, pour, break"
area: POTTERY
status: open
wave: 26
deps: [PAR-FLUID-01]
size: S
aliases: ["M3-POT-03 (jugs)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/JugItem.java"
  - "src/main/java/net/dries007/tfc/common/items/GlassBottleItem.java"
  - "src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/jug/**"
  - "core/src/test/java/dev/tfp/core/jug/**"
  - "core/src/test/resources/dev/tfp/core/jug/**"
  - "paper/src/main/java/dev/tfp/paper/jug/**"
  - "paper/src/test/java/dev/tfp/paper/jug/**"
  - "core/src/main/java/dev/tfp/core/vessel/Jug*"
  - "core/src/test/java/dev/tfp/core/vessel/Jug*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/jug"
  - "right-click water"
  - "tfp debug thirst Bot 20"
  - "drink; tfp debug player Bot"
plan:
  - "Right-click water to fill (100 mB), right-click air/ground to drink (JugRules: drain all, Drinkable effects), break chance 0.02 on drink; glass bottles per glass type with their capacities and break chances."
tests:
  - "paper: MockBukkit fill/drink"
done_criteria:
  - "Drinking from a jug raises thirst in-game; this also closes the open-water drinking divergence path"
---

# PAR-POT-07: Jugs and glass bottles: fill, drink, pour, break

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
