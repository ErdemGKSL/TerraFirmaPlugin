---
id: PAR-MECH-14
title: "Pump in game: a crankshaft powers a pump that moves water 10 pipes up (PAR-MECH-09 in-game check and crankshaft wiring)"
area: MECH
status: open
wave: 52
deps: [PAR-MECH-09, PAR-MECH-05]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/pump/**"
  - "paper/src/test/java/dev/tfp/paper/pump/**"
  - "itest/src/main/java/dev/tfp/itest/pump/**"
ui: true
in_game_steps:
  - "creative motor (axis x) + axles + crankshaft whose shaft side faces a steel pump; the pump facing north with a pipe run of 10 steel pipes under it ending in a lake: after 40 ticks a water source appears in front of the pump; screenshot the outlet"
  - "break the crankshaft: after 40 ticks the placed water is gone; place the pump over a pipe run of 17: nothing is pumped"
  - "feel checklist (docs/research/ore-entity-renderer-design.md 6) on a pipe and the pump: cracks, metal sounds and particles, mining time, light, outline, pick-block, /fill leaves no display"
plan:
  - "PAR-MECH-09 left the pump complete except its power: PumpService reads the crankshaft rotation through the paper/pump ShaftSource service (TFC CrankshaftBlockEntity.getCrankShaftAt: a SHAFT part next to the pump and the BASE part two blocks away, both facing the pump). The crankshaft module (PAR-MECH-05) provides ShaftSource through ModuleContext.services(); if it did not, add the provider here. Without it no pump runs."
  - "Code done 0d7140e: ShaftSource provider (already CrankshaftModule.shaftSource, PAR-MECH-05; nothing to add), test; pending in-game check"
  - "Run the in-game steps on Paper 1.21.11 and keep the evidence in docs/verification/PAR-MECH-14/ (the PAR-MECH-09 divergence 'full-cube outline' is expected)."
tests:
  - "MockBukkit: a ShaftSource provided by the crankshaft module powers the pump"
done_criteria:
  - "A crankshaft powers a pump: water appears at the outlet within 40 ticks and goes when the crankshaft breaks"
  - "In-game steps pass with screenshots and verdict in docs/verification/PAR-MECH-14/"
---

# PAR-MECH-14: Pump in game: a crankshaft powers a pump that moves water 10 pipes up

> Claimed: claude MECH session 011osUpA, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
