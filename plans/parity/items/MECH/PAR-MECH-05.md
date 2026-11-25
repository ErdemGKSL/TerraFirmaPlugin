---
id: PAR-MECH-05
title: "Crankshaft and powered bellows"
area: MECH
status: done
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
ui: false
plan:
  - "Crankshaft converts rotation into bellows pushes as TFC."
  - "Done: core CrankshaftRules (shape, partner, rod use, updateShape, sink face, getCrankShaftAt, calculateShaftMovement with the Mth sine table, real angle, extension, tickBoth push cadence, batched run); paper crankshaft/ (CrankshaftModule: device kind tfp:crankshaft, ShaftSource for the pump, BellowsDevice.crank lookup; CrankshaftService: one 20-tick region task per base pushing the bellows in front; CrankshaftDevices: steel rod grows the shaft, shaft goes with its base; CrankshaftChunkIndex restart; CrankRotation service seam). Tests: CrankshaftGoldenTest 4 (TFC oracle in a scratch harness), CrankshaftMockBukkitTest 5 (a water wheel's rotation drives the bellows at TFC's push ticks). The CE block, the network sink that feeds CrankRotation and the in-game screenshot moved to PAR-MECH-21 (tools/datagen, core/rotation and paper/rotation are not this item's files); ui set false."
tests:
  - "core: crank timing golden"
done_criteria:
  - "A water wheel drives bellows (MockBukkit CrankshaftMockBukkitTest.waterWheelDrivesBellows, through the CrankRotation seam)"
  - "Deferred check tracked in PAR-MECH-21 (the CE block and network sink it needs come with it)"
---

# PAR-MECH-05: Crankshaft and powered bellows

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
