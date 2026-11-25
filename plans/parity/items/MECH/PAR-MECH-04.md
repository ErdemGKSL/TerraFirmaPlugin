---
id: PAR-MECH-04
title: "Windmill and blades"
area: MECH
status: done
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
ui: false
plan:
  - "Blade inventory (1-5 blades, dyed/lattice/rustic), wind from the climate model, obstruction disk scan async, BetterModel blades."
  - "Done: core `windmill/WindmillSample` (lazy 40-tick step) and `windmill/WindmillBlades` (useItemOn clicks, updateState count, WindmillBladeItem.useOn); paper `windmill/` (`WindmillService` one 40-tick region task: disk and wind inputs snapshotted on the region thread, `WindmillWind` (TFC overworld wind length, as the anemometer) and the sample on the CPU pool, a changed speed to the `WindmillNetwork` service, record commit re-validated; `WindmillDevices` kind `tfp:wood/windmill` with 5 one-blade slots filtered by `tfp:windmill_blades`, DROP on break, blade insert/extract and back to the axle, the blade-on-axle listener with the not-enough-space refusal; `WindmillChunkIndex`, chunk load/unload and startup re-add; lang `windmill.not_enough_space`). Tests: core WindmillSampleTest 6 (+ WindmillGoldenTest), paper WindmillMockBukkitTest 7. Divergences in the ledger. The network source node is PAR-MECH-17; CE blocks, blade item visuals, models and the in-game check moved to PAR-MECH-18 (Pending in-game check)."
tests:
  - "core: WindmillGoldenTest exists"
  - "core: WindmillSampleTest (lazy vs stepped, blade rules)"
  - "paper: WindmillMockBukkitTest"
done_criteria:
  - "A windmill with five blades in a 0.5 wind reaches TFC's 2 pi / 80 rad/t and reports it to the rotation network service (MockBukkit WindmillMockBukkitTest.fiveBladesInStrongWindReachTopSpeed); the network node is PAR-MECH-17; turning on a hill in game: deferred check tracked in PAR-MECH-18"
---

# PAR-MECH-04: Windmill and blades

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
