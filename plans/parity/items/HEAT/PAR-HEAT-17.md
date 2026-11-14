---
id: PAR-HEAT-17
title: "Self-test heat.cook_meat: TFC HeatingItemTest on a real Paper and Folia server"
area: HEAT
status: done
wave: 13
deps: [PAR-HEAT-04]
size: S
aliases: ["follow-up of PAR-HEAT-04: its itest case file is outside that item's files_owned"]
tfc_refs:
  - "src/test/java/net/dries007/tfc/test/item/HeatingItemTest.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/HeatCookMeatCase.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-HEAT-04 ships `HeatingService` (HeatingModule) and `HeatingSelfCheck` (paper/heat), TFC's HeatingItemTest.testCookMeat over live services; it passes on MockBukkit with the datagen output (HeatingServiceMockBukkitTest#testCookMeatOnTheContentData)."
  - "Add HeatCookMeatCase (`heat.cook_meat`, add it to SelfTestSuite and bump the suite case count its test checks): skip when the module services are off; rebuild the recipe index (`Recipes.rebuild()`, as BarrelRecipesCase does); on the global thread run `HeatingSelfCheck.run(new HeatingSelfCheck.Services(services.require(HeatingService.class), services.require(ItemHeatHooks.class), services.require(ItemHeatDefinitionsCache.class).get(), tfp.integrations().customItems(), services.require(Tags.class).snapshot(), RecipeModule.vanillaIds(), () -> tfp.clock().get().playerTicks()))` and fail with `result.problems()`; report the number of meats checked. With CraftEngine absent the items cannot be created: skip with that reason."
  - "Run :itest:runSelfTest on Paper 1.21.11 and Folia 1.21.11 (Haiku subagent) and record the result in the PAR-HEAT-04 ledger row."
  - "Done: HeatCookMeatCase (`heat.cook_meat`) added to SelfTestSuite (32 cases, SelfTestSuiteTest updated); :itest:runSelfTest green on Paper 1.21.11 and Folia 1.21.11 (44 raw meats checked); recorded in the PAR-HEAT-04 ledger row; no new divergence."
tests:
  - "itest `heat.cook_meat` passes on Paper and Folia"
done_criteria:
  - "Port TFC HeatingItemTest fully as an in-server self-test (itest `heat.cook_meat`): case green on Paper and Folia"
---

# PAR-HEAT-17: Self-test heat.cook_meat

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
