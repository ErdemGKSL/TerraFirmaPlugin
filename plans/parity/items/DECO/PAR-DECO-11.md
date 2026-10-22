---
id: PAR-DECO-11
title: "Boats, chest boats and minecarts"
area: DECO
status: open
wave: 48
deps: [PAR-WORLD-10]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/misc/TFCBoat.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/TFCChestBoat.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/HoldingMinecart.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCBoatItem.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/vehicle/**"
  - "core/src/test/java/dev/tfp/core/vehicle/**"
  - "core/src/test/resources/dev/tfp/core/vehicle/**"
  - "paper/src/main/java/dev/tfp/paper/vehicle/**"
  - "paper/src/test/java/dev/tfp/paper/vehicle/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/vehicle/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/vehicle/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/boat/maple; place on water; ride; screenshot"
plan:
  - "Per-wood boats as vanilla boat carriers with the closest vanilla wood type + BetterModel skin (divergence if skin impossible), chest boats, holding minecart (carries a block)."
tests:
  - "paper: spawn from item"
done_criteria:
  - "Rowing a TFC maple boat"
---

# PAR-DECO-11: Boats, chest boats and minecarts

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
