---
id: PAR-MECH-07
title: "Trip hammer"
area: MECH
status: open
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
  - "Hammer strikes on the anvil in front (TripHammerRules) calling the anvil's remote work; hammer item damage."
tests:
  - "core: TripHammerGoldenTest exists"
done_criteria:
  - "A trip hammer works an anvil"
---

# PAR-MECH-07: Trip hammer

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
