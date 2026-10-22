---
id: PAR-MECH-09
title: "Pump and pipes"
area: MECH
status: open
wave: 51
deps: [PAR-MECH-01, PAR-FLUID-02]
size: M
aliases: ["M6-MECH-03 (pump)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPumpBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/FluidPipeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/pump/**"
  - "core/src/test/java/dev/tfp/core/pump/**"
  - "core/src/test/resources/dev/tfp/core/pump/**"
  - "paper/src/main/java/dev/tfp/paper/pump/**"
  - "paper/src/test/java/dev/tfp/paper/pump/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/pump/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/pump/**"
  - "content/generated/content/craftengine/tfp/configuration/pump/**"
  - "core/src/main/java/dev/tfp/core/machine/Pump*"
  - "core/src/test/java/dev/tfp/core/machine/Pump*"
  - "core/src/main/java/dev/tfp/core/machine/FluidPipe*"
  - "core/src/test/java/dev/tfp/core/machine/FluidPipe*"
ui: true
in_game_steps:
  - "pump in water + pipes + crankshaft; screenshot outlet"
plan:
  - "40-tick cadence pump moving water through pipes (PumpRules), flood placement of vanilla water at the outlet; pipe connections via the neighbour runner."
tests:
  - "core: PumpGoldenTest exists"
done_criteria:
  - "A pump moves water 10 pipes up"
---

# PAR-MECH-09: Pump and pipes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
