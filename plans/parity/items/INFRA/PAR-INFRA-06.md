---
id: PAR-INFRA-06
title: "Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers"
area: INFRA
status: done
wave: 3
deps: [PAR-INFRA-07]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/RecipeHelpers.java"
  - "src/main/java/net/dries007/tfc/common/recipes/ingredients"
  - "src/main/java/net/dries007/tfc/common/recipes/outputs"
  - "src/main/java/net/dries007/tfc/common/recipes/TFCRecipeTypes.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/recipe/**"
  - "paper/src/test/java/dev/tfp/paper/recipe/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/recipe decodes ingredients and output modifiers with hook interfaces; RegistryService loads the data."
  - "Implement the hooks against ItemStack (CE id or vanilla id, tags from PAR-INFRA-07, `tfc:not_rotten`, `tfc:heatable`, `tfc:has_trait`, fluid ingredients through PAR-INFRA-04)."
  - "Index each recipe type by input item id (immutable snapshot rebuilt on `/tfp reload data`) so device lookups are O(1)."
  - "Apply output modifiers (copy_food, copy_heat, add_trait, damage_inputs...) to real ItemStacks."
  - "Done: paper/recipe: `Recipes` service (RecipeModule, `services().require(Recipes.class)`) holding an immutable `RecipeIndex` (every type in item, block, fluid and string-key spaces per `RecipeTypes.SPECS`; tags expanded exactly via `TagMembers` over the tag snapshot and the server's vanilla ids; precise ingredients are wildcards; candidates in id order, first match wins), rebuilt off-thread on the CPU pool when the registry snapshot or tag rules change (the previous index served meanwhile, ~1.5 s for 6177 recipes); `ItemIngredientContext` (CE/vanilla id, Tags, ItemFoodHooks, ItemHeatHooks, ItemFluids); `ItemModifierHooks` (every TFC output modifier on ItemStacks; forging bonus, bowl, flower cutting, bait, glass, powder, meal and custom-item remainders through `ModifierExtensions` for their owning items). Tests: 24 in paper/src/test/java/dev/tfp/paper/recipe (synthetic index, ItemStack ingredients and modifiers on MockBukkit, the shipped data: every type indexed, per-type counts vs TFC, indexed == linear scan for every known item over 13 types). Ledger row + 3 divergences. Follow-up PAR-INFRA-20: datagen does not export TFC's 66 overrides of vanilla crafting recipes (data/minecraft/recipe), so crafting counts are 1686/569 vs TFC's 1734/587; all other types equal TFC."
tests:
  - "paper: MockBukkit tests for each ingredient type and output modifier"
  - "paper: lookup returns TFC's first-by-id recipe for overlapping inputs"
done_criteria:
  - "Every recipe type in content/generated data resolves through the index (count test vs TFC)"
---

# PAR-INFRA-06: Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers

> see_not_parity: NP-DATA-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
