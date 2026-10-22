---
id: PAR-PHYS-02
title: "Collapse runner: mining triggers collapses decided async, applied as falling blocks"
area: PHYS
status: open
wave: 17
deps: [PAR-PHYS-01, PAR-INFRA-10, PAR-INFRA-16]
size: M
aliases: ["M3-PHYS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/Collapse.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
  - "src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java"
  - "src/main/java/net/dries007/tfc/util/data/Support.java"
  - "src/generated/resources/data/tfc/recipe/collapse"
files_owned:
  - "core/src/main/java/dev/tfp/core/collapse/**"
  - "core/src/test/java/dev/tfp/core/collapse/**"
  - "core/src/test/resources/dev/tfp/core/collapse/**"
  - "paper/src/main/java/dev/tfp/paper/collapse/**"
  - "paper/src/test/java/dev/tfp/paper/collapse/**"
ui: true
in_game_steps:
  - "dig a 5x5 room in raw rock without beams; mine a ceiling block"
  - "screenshot falling cobble"
plan:
  - "On BlockBreakEvent of #can_trigger_collapse with a pickaxe: snapshot the support area, CollapseRules decides (real/fake chance, radius) off-thread, the commit queue turns raw rock into falling cobble per collapse recipe in budgeted batches; WorldTracker propagation ticks become delayed decisions."
  - "Folia: the collapse area is clipped to the owning region; neighbours in other regions get a scheduled follow-up."
  - "Ore overlay (2026-10-22): collapses turn rich -> normal -> poor ore into falling blocks (TFC collapse recipes); overlays follow through the block-change packets and the falling-block attachment of PAR-INFRA-16; check no overlay is left in the air after a collapse."
tests:
  - "core: CollapseGoldenTest exists"
  - "paper: fake-clock collapse commits"
  - "itest: Folia stress `collapse.stress`"
done_criteria:
  - "Mining under unsupported raw rock causes a collapse in-game; support beams prevent it"
---

# PAR-PHYS-02: Collapse runner: mining triggers collapses decided async, applied as falling blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
