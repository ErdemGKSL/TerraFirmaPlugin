---
id: PAR-INFRA-06
title: "Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers"
area: INFRA
status: open
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
tests:
  - "paper: MockBukkit tests for each ingredient type and output modifier"
  - "paper: lookup returns TFC's first-by-id recipe for overlapping inputs"
done_criteria:
  - "Every recipe type in content/generated data resolves through the index (count test vs TFC)"
---

# PAR-INFRA-06: Recipe runtime on Paper: ItemStack ingredient matching, indexed lookups, output modifiers

> see_not_parity: NP-DATA-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
