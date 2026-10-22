---
id: PAR-MECH-06
title: "Quern automation"
area: MECH
status: open
wave: 50
deps: [PAR-MECH-01, PAR-PROC-04]
size: S
aliases: ["M6-MECH-03 (quern)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotationSinkBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/quern/powered/**"
  - "paper/src/test/java/dev/tfp/paper/quern/powered/**"
ui: true
in_game_steps:
  - "axle into quern; hopper of grain; wait; screenshot"
plan:
  - "Powered quern grinds lazily from network speed; hopper feed."
tests:
  - "paper: powered grind catch-up"
done_criteria:
  - "An axle-driven quern grinds a hopper of grain"
---

# PAR-MECH-06: Quern automation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
