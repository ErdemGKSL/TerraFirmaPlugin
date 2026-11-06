---
id: PAR-PROC-28
title: "Advanced crafting in-game verification and self-test: knife/sandwich crafts, vanilla recipe leakage over all recipes"
area: PROC
status: open
wave: 8
deps: [PAR-PROC-24, PAR-DECO-01]
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
  - "Combine two stacks of the same raw food with different creation dates in the 2x2 grid (TFC FoodCombiningCraftingRecipe): the result count is the number of non-empty slots (2), one item is taken from each slot, and the result has the older date (tooltip)"
  - "Put one of our items on a vanilla base (e.g. a tfp: item made of paper or stick) into a vanilla recipe's shape (book, ladder): no result"
  - "Two TFC planks (tfp:wood/planks/*, in #minecraft:planks) stacked vertically: 4 minecraft:stick, as in TFC"
  - "A crafter block (powered) holding the book/ladder leakage grid of step 3 does not craft; holding the two-planks grid of step 4 it crafts sticks"
plan:
  - "Deferred from PAR-PROC-03 (PROTOCOL 6.5): the knife needs PAR-PROC-24 (tool items with tfp:mineable rules; `ce item give` of a metal knife fails with \"Missing tag: 'tfp:mineable/knife' in 'minecraft:block'\"), and the itest case lives outside its files. Once PAR-PROC-24 lands, run every in_game_step at GUI scale 2 and 3. The runtime itself is done and tested on MockBukkit and on the shipped data (paper/src/test/java/dev/tfp/paper/craft)."
  - "Self-test CraftLeakageCase (add to SelfTestSuite): for every vanilla crafting recipe of the running server (Bukkit.recipeIterator), fill a CraftingInventory grid with our CE items whose base material satisfies the recipe's choices and fire the prepare path; assert no minecraft: result survives (CraftListener blocks it) unless a TFC recipe of that grid exists or the grid's items are members of the vanilla tag a kept recipe names (KeptVanillaRecipes: TFC planks make sticks). Also assert the 195 recipes TFC disables give no result on vanilla items."
  - "Pending (PAR-PROC-03 review): the planks/sticks and crafter steps also need the tfp:wood/planks/* CE items (`ce item give` answers \"Unknown item 'tfp:wood/planks/acacia'\" on 00a1c0d), which PAR-DECO-01 adds; PAR-PROC-03 itself is not a dep (PROTOCOL 6.5: it is done and the verification item must not list it)."
  - "Pending (PAR-PROC-03 review): CraftLeakageCase also fires a real CrafterCraftEvent on a crafter block (Container state and getContents through CraftListener.onCrafter, not only blocksCrafter), and lists the vanilla crafting recipes newer than TFC's 1.21.1 (spears, copper tools and armor, shelves, ...) that are neither removed nor overridden (ledger divergence row)."
  - "Pending (PAR-PROC-03 review): a drift guard for KeptVanillaRecipes. Recompute from the running server's recipes the kept minecraft:crafting_* recipes (not TFC-overridden, not disabled) whose ingredients, with nested tags expanded, hit a vanilla tag TFC's data extends; fail when the set differs from KeptVanillaRecipes.ALL (minus minecraft:wooden_spear, left out because TFC removes all wooden tools). Run it on 1.21.11 and on 26.x."
tests:
  - "itest: CraftLeakageCase (leakage over all recipes)"
done_criteria:
  - "A knife recipe crafts in-game and the knife loses one durability (screenshot, docs/verification/PAR-PROC-03/verdict.md)"
  - "Self-test CraftLeakageCase passes on Paper 1.21.11 with CraftEngine"
  - "Food combining in-game: two slots give a stack of 2 with the older date (screenshot)"
  - "In-game leakage: our item in a book/ladder shape gives no result, in the crafting table and in a crafter (screenshot)"
  - "TFC planks make 4 sticks in the crafting grid (screenshot)"
---

# PAR-PROC-28: Advanced crafting in-game verification and self-test: knife/sandwich crafts, vanilla recipe leakage over all recipes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
