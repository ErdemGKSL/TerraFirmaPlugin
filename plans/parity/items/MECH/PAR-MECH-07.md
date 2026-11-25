---
id: PAR-MECH-07
title: "Trip hammer"
area: MECH
status: blocked
wave: 50
deps: [PAR-MECH-01, PAR-METAL-05]
size: S
aliases: ["M6-MECH-03 (trip hammer)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/triphammer/**"
  - "core/src/test/java/dev/tfp/core/triphammer/**"
  - "core/src/test/resources/dev/tfp/core/triphammer/**"
  - "paper/src/main/java/dev/tfp/paper/triphammer/**"
  - "paper/src/test/java/dev/tfp/paper/triphammer/**"
  - "core/src/main/java/dev/tfp/core/machine/TripHammer*"
  - "core/src/test/java/dev/tfp/core/machine/TripHammer*"
ui: true
in_game_steps:
  - "trip hammer facing an anvil with a hot ingot; screenshot"
plan:
  - "Blocked (2026-11-25): the logic is in and tested, but a trip hammer cannot work an anvil yet: the CE block tfp:trip_hammer needs datagen (not in files_owned) and the anvil's remote work is the anvil's code (paper/anvil/forge). Follow-ups: PAR-METAL-28 (AnvilBlockEntity.workRemotely as the AnvilRemote service), PAR-MECH-27 (the block, the block coverage owner, the in-game check; it sets this item done)."
  - "Hammer strikes on the anvil in front (TripHammerRules) calling the anvil's remote work; hammer item damage."
  - "Done so far: core/triphammer TripHammerRun (TFC serverTick per tick over a batch, the hammer's own angle, item damage, break on the wrong hand) over core/machine TripHammerRules; paper/triphammer: device kind tfp:trip_hammer (one slot, one item of tfp:trip_hammers by right click, DROP), the strike task every 20 ticks (rotation of the bladed axle above through RotationService.rotation, the metal anvil in front, the hit sound, AnvilRemote.workRemotely(HIT_LIGHT, 12, true), hammer damage stored in the record, the break), TFC's placement facing, chunk index restart, TripHammerModule. Tests: TripHammerRunTest 5, TripHammerMockBukkitTest 7, TripHammerGoldenTest 10."
tests:
  - "core: TripHammerGoldenTest exists"
done_criteria:
  - "A trip hammer works an anvil"
---

# PAR-MECH-07: Trip hammer

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
