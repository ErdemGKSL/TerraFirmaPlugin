---
id: PAR-MECH-01
title: "Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor"
area: MECH
status: open
wave: 49
deps: [PAR-INFRA-10, PAR-INFRA-08]
size: M
aliases: ["M6-MECH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/rotation"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AxleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/GearBoxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/ClutchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rotation/**"
  - "core/src/test/java/dev/tfp/core/rotation/**"
  - "core/src/test/resources/dev/tfp/core/rotation/**"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/**"
  - "content/generated/content/craftengine/tfp/configuration/rotation/**"
ui: true
in_game_steps:
  - "place creative motor + 5 axles + gearbox; tfp debug rotation (new)"
plan:
  - "Blocks persist their node; topology changes enqueue RotationNetworks updates (decide async, apply sync), split per region on Folia; port the 4 remaining RotationNetworkTest cycle cases."
tests:
  - "core: RotationNetworkManagerPortTest + 4 cycle cases"
  - "itest: Folia stress"
done_criteria:
  - "A creative motor drives an axle line and gearbox (debug readout of speeds)"
---

# PAR-MECH-01: Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor

> see_not_parity: NP-VIS-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
