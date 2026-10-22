---
id: PAR-MECH-04
title: "Windmill and blades"
area: MECH
status: open
wave: 51
deps: [PAR-MECH-02, PAR-FAUNA-01]
size: M
aliases: ["M6-MECH-02 (windmill)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/items/WindmillBladeItem.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/windmill/**"
  - "core/src/test/java/dev/tfp/core/windmill/**"
  - "core/src/test/resources/dev/tfp/core/windmill/**"
  - "paper/src/main/java/dev/tfp/paper/windmill/**"
  - "paper/src/test/java/dev/tfp/paper/windmill/**"
  - "core/src/main/java/dev/tfp/core/power/Wind*"
  - "core/src/test/java/dev/tfp/core/power/Wind*"
ui: true
in_game_steps:
  - "place windmill with 4 blades at y>100; screenshot"
plan:
  - "Blade inventory (1-5 blades, dyed/lattice/rustic), wind from the climate model, obstruction disk scan async, BetterModel blades."
tests:
  - "core: WindmillGoldenTest exists"
done_criteria:
  - "A windmill on a hill turns"
---

# PAR-MECH-04: Windmill and blades

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
