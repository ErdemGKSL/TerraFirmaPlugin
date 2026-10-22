---
id: PAR-MECH-08
title: "Power loom"
area: MECH
status: open
wave: 51
deps: [PAR-MECH-01, PAR-PROC-06]
size: S
aliases: ["M6-MECH-03 (power loom)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/powerloom/**"
  - "core/src/test/java/dev/tfp/core/powerloom/**"
  - "core/src/test/resources/dev/tfp/core/powerloom/**"
  - "paper/src/main/java/dev/tfp/paper/powerloom/**"
  - "paper/src/test/java/dev/tfp/paper/powerloom/**"
  - "core/src/main/java/dev/tfp/core/machine/PowerLoom*"
  - "core/src/test/java/dev/tfp/core/machine/PowerLoom*"
ui: true
in_game_steps:
  - "axle into power loom with yarn; wait; screenshot"
plan:
  - "Lazy pushes from rotation (PowerLoomRules)."
tests:
  - "core: PowerLoomGoldenTest exists"
done_criteria:
  - "A power loom weaves cloth"
---

# PAR-MECH-08: Power loom

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
