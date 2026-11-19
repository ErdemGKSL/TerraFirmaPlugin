---
id: PAR-WORLD-07
title: "Aquifer barrier noise and the grass rule under carved surfaces"
area: WORLD
status: done
wave: 20
deps: [PAR-WORLD-06]
size: S
aliases: ["M2-GEN-09 (aquifer)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/TFCAquifer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/aquifer/**"
  - "core/src/test/java/dev/tfp/core/worldgen/aquifer/**"
ui: false
in_game_steps: []
plan:
  - "Port the barrier noise (currently zero) and the surface rule that turns exposed dirt under carved openings into grass as TFC; no floating fluids."
  - "Grass rule: nothing to port. TFC's cave and canyon carvers override `carveBlock` and never set `reachedSurface` (only vanilla's own `carveBlock` does), so `CarverHelpers.carveBlock` never places grass at the pinned commit. Ledger row changed to parity."
  - "Floating fluids: with the real barrier, TFC's aquifer leaves a few fluids next to air and schedules fluid ticks (`shouldScheduleFluidUpdate`). The terrain fill cannot schedule ticks, so `FluidSupport.settle` dams those positions with rock (divergence row in the ledger, NP-FLUID-01 family)."
  - "Wiring into `TfcTerrain` (not owned; owned by PAR-WORLD-28) and the in-game 100-chunk scan moved to PAR-WORLD-29. The done criterion is met in core by AquiferBarrierScanTest; the in-game scan is tracked in PAR-WORLD-29."
  - "Done: `core/worldgen/aquifer/AquiferBarrierNoise` (TFC NoiseSampler.barrierNoise = Minecraft 1.21.1 NormalNoise on aquifer_barrier, exact to the bit against the 1.21.11 jar) and `FluidSupport`; tests AquiferBarrierNoiseGoldenTest (3, 24 golden rows, 4 seeds) and AquiferBarrierScanTest (3, 100-chunk fully carved scan: zero unsupported fluids after settling). Oracle: scratch program on the Mojang-mapped Paper 1.21.11 jar, kept outside the repo."
tests:
  - "core: golden aquifer samples"
done_criteria:
  - "No floating water in a 100-chunk scan (core: AquiferBarrierScanTest; in-game scan tracked in PAR-WORLD-29)"
---

# PAR-WORLD-07: Aquifer barrier noise and the grass rule under carved surfaces

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
