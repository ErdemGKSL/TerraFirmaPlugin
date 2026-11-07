---
id: PAR-FLUID-01
title: "Fluids as container contents: registry of TFC fluids, colours, debug fill"
area: FLUID
status: done
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
ui: false
in_game_steps: []
plan:
  - "Fluid registry (ids, tint colours, temperature, drinkable link) used by containers; `tfp debug fluid <name> <container> <fluid> <mB>` fills the held container."
  - "In-game check deferred to PAR-FLUID-05 (PROTOCOL §6.5): no fluid container is a CraftEngine item yet (2026-11-07, Paper 1.21.11: `ce item give Bot tfp:ceramic/jug` is `Unknown item`); PAR-POT-07 adds the jug and bottles, PAR-FLUID-02 the buckets."
  - "Done: core `fluid/TfcFluid`, `TfcFluids` (67 TFC sources in TFCFluids order + water, lava, milk; TFC tints, temperatures, name keys, metal and drinkable links, `resolve` accepts `tfp:milk`); paper `fluid/FluidModule` (`/tfp debug fluid <name> <container> <fluid> [mB]`, player thread, usable_in tag check), `FluidTooltipContributor` (TFC's `fluid_units_and_capacity_of` line from TFC's lang data per locale), `FluidLang`, `FluidContainerItems`. Tests: core TfcFluidsTest 8, paper FluidTooltipContributorTest 5, FluidModuleTest 4. Ledger: feature row + 3 divergences. Verification deferred to PAR-FLUID-05."
tests:
  - "core: FluidContainerKindTest exists; fluid count vs TFCFluids"
done_criteria:
  - "Deferred check tracked in PAR-FLUID-05"
---

# PAR-FLUID-01: Fluids as container contents: registry of TFC fluids, colours, debug fill

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
