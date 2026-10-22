---
id: PAR-MECH-10
title: "Bladed axle"
area: MECH
status: open
wave: 52
deps: [PAR-MECH-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/BladedAxleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/BladedAxleBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bladedaxle/**"
  - "core/src/test/java/dev/tfp/core/bladedaxle/**"
  - "core/src/test/resources/dev/tfp/core/bladedaxle/**"
  - "paper/src/main/java/dev/tfp/paper/bladedaxle/**"
  - "paper/src/test/java/dev/tfp/paper/bladedaxle/**"
ui: true
in_game_steps:
  - "place bladed axle with saw; spawn a pig next to it"
plan:
  - "Bladed axle holds a saw/blade item, damages entities and cuts as TFC."
tests:
  - "paper: damage tick"
done_criteria:
  - "Bladed axle hurts a mob"
---

# PAR-MECH-10: Bladed axle

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
