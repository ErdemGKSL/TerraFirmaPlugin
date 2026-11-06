---
id: PAR-PROC-28
title: "Advanced crafting in-game verification and self-test: knife/sandwich crafts, vanilla recipe leakage over all recipes"
area: PROC
status: open
wave: 8
deps: [PAR-PROC-03, PAR-PROC-24]
size: S
aliases: ["follow-up of PAR-PROC-03 (its in_game_steps, itest test and in-game done-criterion)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/AdvancedShapedRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/AdvancedShapelessRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/FoodCombiningCraftingRecipe.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CraftLeakageCase.java"
  - "docs/verification/PAR-PROC-03/**"
ui: true
in_game_steps:
  - "Survival Bot with a crafting table: craft a sandwich (tfp:crafting/food/*_sandwich_*: knife + bread + fillings) or another recipe needing a knife (e.g. a tfp:crafting/... recipe with c:tools/knife); screenshot the grid with the result, then the knife's durability bar after the craft (GUI scale 2 and 3)"
  - "Combine two stacks of the same raw food with different creation dates in the 2x2 grid: one stack of the summed count, the older date (tooltip)"
  - "Put one of our items on a vanilla base (e.g. a tfp: item made of paper or stick) into a vanilla recipe's shape (book, ladder): no result"
plan:
  - "Deferred from PAR-PROC-03 (PROTOCOL 6.5): that worker could not start the cheap-model agent for the headless client, the knife needs PAR-PROC-24 (tool items with tfp:mineable rules), and the itest case lives outside its files. The runtime itself is done and tested on MockBukkit and on the shipped data (paper/src/test/java/dev/tfp/paper/craft)."
  - "Self-test CraftLeakageCase (add to SelfTestSuite): for every vanilla crafting recipe of the running server (Bukkit.recipeIterator), fill a CraftingInventory grid with our CE items whose base material satisfies the recipe's choices and fire the prepare path; assert no minecraft: result survives (CraftListener blocks it) unless a TFC recipe of that grid exists. Also assert the 195 recipes TFC disables give no result on vanilla items."
tests:
  - "itest: CraftLeakageCase (leakage over all recipes)"
done_criteria:
  - "A knife recipe crafts in-game and the knife loses one durability (screenshot, docs/verification/PAR-PROC-03/verdict.md)"
  - "Self-test CraftLeakageCase passes on Paper 1.21.11 with CraftEngine"
---

# PAR-PROC-28: Advanced crafting in-game verification and self-test: knife/sandwich crafts, vanilla recipe leakage over all recipes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
