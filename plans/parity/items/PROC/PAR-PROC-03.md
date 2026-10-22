---
id: PAR-PROC-03
title: "Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage"
area: PROC
status: open
wave: 7
deps: [PAR-PROC-02, PAR-INFRA-06]
size: M
aliases: ["M3-GUI-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/AdvancedShapedRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/AdvancedShapelessRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/FoodCombiningCraftingRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/CastingCraftingRecipe.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/craft/**"
  - "paper/src/test/java/dev/tfp/paper/craft/**"
  - "core/src/main/java/dev/tfp/core/craft/AdvancedCraft*"
  - "core/src/test/java/dev/tfp/core/craft/AdvancedCraft*"
ui: true
in_game_steps:
  - "craft a sandwich or a recipe needing a knife; screenshot result and knife durability"
plan:
  - "PrepareItemCraftEvent + CraftItemEvent: match the 655 advanced recipes via PAR-INFRA-06 (primary input, output modifiers, tool damage remainders via AdvancedCraftRules), food combining (merge food dates), casting crafting."
  - "Block vanilla recipes that would consume CE items (leakage) and vanilla recipes TFC removes."
tests:
  - "paper: MockBukkit crafting a TFC recipe with a knife damages it"
  - "itest: leakage test over all recipes"
done_criteria:
  - "All 2912 crafting recipes resolvable (count test)"
---

# PAR-PROC-03: Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
