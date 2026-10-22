---
id: PAR-MECH-05
title: "Crankshaft and powered bellows"
area: MECH
status: open
wave: 50
deps: [PAR-MECH-01, PAR-METAL-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/crankshaft/**"
  - "core/src/test/java/dev/tfp/core/crankshaft/**"
  - "core/src/test/resources/dev/tfp/core/crankshaft/**"
  - "paper/src/main/java/dev/tfp/paper/crankshaft/**"
  - "paper/src/test/java/dev/tfp/paper/crankshaft/**"
ui: true
in_game_steps:
  - "axle -> crankshaft -> bellows -> forge; screenshot heat level"
plan:
  - "Crankshaft converts rotation into bellows pushes as TFC."
tests:
  - "core: crank timing golden"
done_criteria:
  - "A water wheel drives bellows"
---

# PAR-MECH-05: Crankshaft and powered bellows

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
