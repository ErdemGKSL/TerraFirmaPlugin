---
id: PAR-WORLD-07
title: "Aquifer barrier noise and the grass rule under carved surfaces"
area: WORLD
status: open
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
tests:
  - "core: golden aquifer samples"
done_criteria:
  - "No floating water in a 100-chunk scan"
---

# PAR-WORLD-07: Aquifer barrier noise and the grass rule under carved surfaces

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
