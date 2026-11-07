---
id: PAR-FLUID-05
title: "Fluid container tooltip in-game verification: debug fill of a jug shows the fluid line"
area: FLUID
status: open
wave: 27
deps: [PAR-POT-07]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
files_owned:
  - "docs/verification/PAR-FLUID-05/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/jug"
  - "tfp debug fluid Bot jug tfp:milk 100"
  - "hover the jug in the inventory; screenshot the tooltip at GUI scale 2 and 3"
  - "tfp debug fluid Bot jug tfp:beer 60; hover; screenshot"
  - "client language tr_tr (options.txt lang:tr_tr, relaunch); hover; screenshot"
plan:
  - "Deferred in-game check of PAR-FLUID-01 (PROTOCOL §6.5): PAR-FLUID-01 adds the fluid registry, the tooltip line and `/tfp debug fluid`, but no fluid container exists as a CraftEngine item until PAR-POT-07 (jug, glass bottles) and PAR-FLUID-02 (buckets) add them; checked 2026-11-07 on Paper 1.21.11: `ce item give Bot tfp:ceramic/jug` answers `Unknown item`. Run the steps once PAR-POT-07 is done; a failure is fixed in PAR-FLUID-01's files (`paper/.../fluid/**`) through a follow-up."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Debug fill shows the fluid in the tooltip: `100 / 100 mB of Milk` (en_us) and `60 / 100 mB of Beer` under the jug's name; the Turkish client shows TFC's Turkish pattern and fluid name"
---

# PAR-FLUID-05: Fluid container tooltip in-game verification: debug fill of a jug shows the fluid line

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
