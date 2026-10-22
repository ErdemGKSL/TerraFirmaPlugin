---
id: PAR-HEAT-04
title: "Heating recipes on devices: the 884 heating recipes as a shared service"
area: HEAT
status: open
wave: 12
deps: [PAR-INFRA-06]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java"
  - "src/generated/resources/data/tfc/recipe/heating"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/heat/Heating*"
  - "paper/src/test/java/dev/tfp/paper/heat/Heating*"
  - "core/src/main/java/dev/tfp/core/heat/Heating*"
  - "core/src/test/java/dev/tfp/core/heat/Heating*"
ui: false
in_game_steps: []
plan:
  - "`HeatingService.convertIfReady(stack, temperature)` looks up the heating recipe (indexed), returns the result item or fluid (with copy_heat / copy_food) when the item reaches the recipe temperature, as TFC HeatingRecipe.assemble; used by firepit, grill, forge, crucible, kiln, bloomery."
tests:
  - "core: golden for 20 sampled recipes (temperature, result)"
  - "paper: MockBukkit raw beef -> cooked beef with copied food date"
done_criteria:
  - "Port TFC HeatingItemTest fully as an in-server self-test (itest `heat.cook_meat`)"
---

# PAR-HEAT-04: Heating recipes on devices: the 884 heating recipes as a shared service

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
