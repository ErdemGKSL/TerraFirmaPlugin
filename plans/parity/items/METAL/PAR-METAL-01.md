---
id: PAR-METAL-01
title: "Alloy display and fluid alloy glue for vessels and crucibles"
area: METAL
status: open
wave: 27
deps: [PAR-INFRA-04, PAR-INFRA-05]
size: S
aliases: ["M4-MET-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/FluidAlloy.java"
  - "src/main/java/net/dries007/tfc/util/AlloyRange.java"
  - "src/generated/resources/data/tfc/recipe/alloy"
files_owned:
  - "core/src/main/java/dev/tfp/core/alloy/**"
  - "core/src/test/java/dev/tfp/core/alloy/**"
  - "core/src/test/resources/dev/tfp/core/alloy/**"
  - "paper/src/main/java/dev/tfp/paper/alloy/**"
  - "paper/src/test/java/dev/tfp/paper/alloy/**"
ui: true
in_game_steps:
  - "vessel with copper and tin melted (PAR-POT-04)"
  - "hover; screenshot"
plan:
  - "Store FluidAlloy contents on vessel/crucible items and blocks (PAR-INFRA-04), tooltip contributor lists metal percentages and the resulting alloy (or 'Unknown') as TFC."
tests:
  - "core: FluidAlloyTest exists"
  - "paper: tooltip lines"
done_criteria:
  - "A vessel with 90% copper 10% tin shows 'Bronze' in-game"
---

# PAR-METAL-01: Alloy display and fluid alloy glue for vessels and crucibles

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
