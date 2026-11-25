---
id: PAR-MECH-09
title: "Pump and pipes"
area: MECH
status: done
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
ui: false
in_game_steps:
  - "pump in water + pipes + crankshaft; screenshot outlet"
plan:
  - "40-tick cadence pump moving water through pipes (PumpRules), flood placement of vanilla water at the outlet; pipe connections via the neighbour runner."
  - "Done: core PumpRules/PumpCell/FluidPipeRules (already ported, PumpGoldenTest and FluidPipeRulesTest); paper/pump (PumpService one 40-tick region task per pump, PumpWorld reader/writer, PumpDevices kinds + placement listener, ShaftSource service, PumpChunkIndex, /tfp debug pump); datagen pump/PumpBlocks (tfp:steel_pipe 128 states, tfp:steel_pump 4 states, TFC models/textures/lang/loot). Tests: PumpMockBukkitTest 7 (water 10 pipes up and taken back), PumpBlocksTest 4, BootstrapTest debug literal. Pending in-game check and the crankshaft that powers the pump: PAR-MECH-14 (ShaftSource is provided by PAR-MECH-05). Divergences in the ledger."
tests:
  - "core: PumpGoldenTest exists"
done_criteria:
  - "A pump moves water 10 pipes up (MockBukkit PumpMockBukkitTest.aPumpMovesWaterTenPipesUp)"
  - "Deferred check tracked in PAR-MECH-14 (screenshot of the outlet needs the crankshaft of PAR-MECH-05)"
---

# PAR-MECH-09: Pump and pipes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
