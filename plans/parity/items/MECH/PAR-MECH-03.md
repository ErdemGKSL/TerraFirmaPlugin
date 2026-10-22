---
id: PAR-MECH-03
title: "Water wheel"
area: MECH
status: open
wave: 51
deps: [PAR-MECH-02, PAR-FAUNA-01]
size: M
aliases: ["M6-MECH-02 (water wheel)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/WaterWheelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/waterwheel/**"
  - "core/src/test/java/dev/tfp/core/waterwheel/**"
  - "core/src/test/resources/dev/tfp/core/waterwheel/**"
  - "paper/src/main/java/dev/tfp/paper/waterwheel/**"
  - "paper/src/test/java/dev/tfp/paper/waterwheel/**"
  - "core/src/main/java/dev/tfp/core/power/WaterWheel*"
  - "core/src/test/java/dev/tfp/core/power/WaterWheel*"
ui: true
in_game_steps:
  - "place a water wheel in a river; screenshot"
plan:
  - "40-tick flow sample of the water window (snapshot, decide async), speed ramp, BetterModel waterwheel model (TFC waterwheel.bbmodel)."
tests:
  - "core: WaterWheelGoldenTest exists"
done_criteria:
  - "A water wheel in a river turns an axle"
---

# PAR-MECH-03: Water wheel

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
