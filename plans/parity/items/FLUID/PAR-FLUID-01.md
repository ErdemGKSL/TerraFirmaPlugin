---
id: PAR-FLUID-01
title: "Fluids as container contents: registry of TFC fluids, colours, debug fill"
area: FLUID
status: open
wave: 8
deps: [PAR-INFRA-04]
size: S
aliases: ["M6-FLUID-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java"
  - "src/main/java/net/dries007/tfc/common/fluids/SimpleFluid.java"
  - "src/main/java/net/dries007/tfc/common/fluids/MoltenFluid.java"
  - "src/generated/resources/data/tfc/tfc/fluid_heat"
files_owned:
  - "core/src/main/java/dev/tfp/core/fluid/**"
  - "core/src/test/java/dev/tfp/core/fluid/**"
  - "core/src/test/resources/dev/tfp/core/fluid/**"
  - "paper/src/main/java/dev/tfp/paper/fluid/**"
  - "paper/src/test/java/dev/tfp/paper/fluid/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/jug"
  - "tfp debug fluid Bot jug tfp:milk 100"
  - "hover"
plan:
  - "Fluid registry (ids, tint colours, temperature, drinkable link) used by containers; `tfp debug fluid <name> <container> <fluid> <mB>` fills the held container."
tests:
  - "core: FluidContainerKindTest exists; fluid count vs TFCFluids"
done_criteria:
  - "Debug fill shows the fluid in the tooltip"
---

# PAR-FLUID-01: Fluids as container contents: registry of TFC fluids, colours, debug fill

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
