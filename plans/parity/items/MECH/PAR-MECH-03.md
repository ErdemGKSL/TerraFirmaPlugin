---
id: PAR-MECH-03
title: "Water wheel"
area: MECH
status: done
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
ui: false
plan:
  - "40-tick flow sample of the water window (snapshot, decide async), speed ramp, BetterModel waterwheel model (TFC waterwheel.bbmodel)."
  - "Done: core `waterwheel/WaterWheelSample` (lazy 40-tick step: the ramp between samples in closed form, TFC's sample order); paper `waterwheel/` (`WaterWheelService` one 40-tick region task per wheel: region snapshot of the 24-cell window, `WaterWheel.netFlow` and the sample on the CPU pool, a changed speed to the `WaterWheelNetwork` service, record commit on the region thread re-validated; `WaterWheelCells` river/still/salt/open/solid from `WorldWaterService`; `WaterWheelDevices` kind `tfp:wood/water_wheel` (axis from the player's facing); `WaterWheelChunkIndex`, chunk load/unload and startup re-add; `WaterWheelModule`). Tests: core WaterWheelSampleTest 4 (+ WaterWheelGoldenTest), paper WaterWheelMockBukkitTest 8. Divergences in the ledger. The network source node is PAR-MECH-17 (core/paper rotation are not this item's files); CE blocks, items, the wheel model and the in-game check moved to PAR-MECH-18 (Pending in-game check)."
tests:
  - "core: WaterWheelGoldenTest exists"
  - "core: WaterWheelSampleTest (lazy vs stepped)"
  - "paper: WaterWheelMockBukkitTest"
done_criteria:
  - "A water wheel in a river reaches TFC's speed and reports it to the rotation network service on its axis (MockBukkit WaterWheelMockBukkitTest.aWheelInARiverReachesTopSpeedAndReportsIt); the network node is PAR-MECH-17; turning an axle in game: deferred check tracked in PAR-MECH-18"
---

# PAR-MECH-03: Water wheel

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
