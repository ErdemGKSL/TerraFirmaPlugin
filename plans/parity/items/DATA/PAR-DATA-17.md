---
id: PAR-DATA-17
title: "Self-test piston.no_water_dupe: a real 1-tick piston pulse moves no water source, on Paper and Folia"
area: DATA
status: done
wave: 13
deps: [PAR-DATA-16]
size: S
aliases: ["follow-up of PAR-DATA-16: its itest case file is outside that item's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/mixin/PistonBlockEntityMixin.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/PistonNoWaterDupeCase.java"
  - "paper/src/main/java/dev/tfp/paper/piston/PistonFluidSelfCheck.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-DATA-16 dries piston-moved fluid-carrying blocks (core piston/PistonFluidRule, paper piston/PistonFluidService) and is covered by core and MockBukkit tests only; no real piston has run it."
  - "Add PistonFluidSelfCheck (paper/piston, body of the case) and PistonNoWaterDupeCase (itest, add it to SelfTestSuite): in a loaded test area place a sticky piston facing east, a waterlogged oak slab in front of it, then a 1-tick pulse (redstone block set and removed on the next tick through the region scheduler, or an observer clock); wait until PistonFluidService.pipeline(world).isIdle() and watching(world) == 0; assert the slab at its destination is waterlogged=false, no minecraft:water source exists in the 3x3x3 around origin and destination, and the pipeline counted 0 thread violations. Repeat with a normal (held) pulse: the slab lands dry with no commit."
  - "Run :itest:runSelfTest on Paper 1.21.11 and Folia 1.21.11 (Haiku subagent), record the result in the ledger feature row of PAR-DATA-16."
  - "Done: paper piston/PistonFluidSelfCheck + itest cases/PistonNoWaterDupeCase (in SelfTestSuite; SelfTestSuiteTest counts 31). The falling edge follows the piston base (removed on the first tick it is seen extended), since a fixed 1-tick delay from the rising edge let the removal reach the piston before its extend block event (seen on Paper with an immediate rising edge and on Folia with a delayed one). :itest:runSelfTest green on Paper 1.21.11-132 and Folia 1.21.11-14 (2026-11-14): 1-tick pulse lands the slab dry with 1 commit, held pulse dry with 0 commits, no water in the 3x3x3s, 0 thread violations."
tests:
  - "itest `piston.no_water_dupe` passes on Paper and Folia"
done_criteria:
  - "No piston pulse moves a water source with a waterlogged block on a real Paper and Folia server; case green on both"
---

# PAR-DATA-17: Self-test piston.no_water_dupe

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
