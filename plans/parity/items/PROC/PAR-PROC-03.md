---
id: PAR-PROC-03
title: "Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "PrepareItemCraftEvent + CraftItemEvent: match the 655 advanced recipes via PAR-INFRA-06 (primary input, output modifiers, tool damage remainders via AdvancedCraftRules), food combining (merge food dates), casting crafting."
  - "Block vanilla recipes that would consume CE items (leakage) and vanilla recipes TFC removes."
  - "In-game screenshot check and the itest leakage case deferred to PAR-PROC-28 (PROTOCOL 6.5): this worker session could not start the cheap-model agent for the headless client, the knife needs PAR-PROC-24, and the self-test case file is outside files_owned."
  - "Done: PAR-PROC-03 commit. core/craft AdvancedCraftGrid (vanilla trim/shrink, shaped plain+mirrored with TFC's mirrored-first input slot, shapeless perfect matching) and AdvancedCraftRules (food combining match/date, casting mold slot); paper/craft CraftModule + CraftingTable + CraftListener (PrepareItemCraftEvent result, cancelled result clicks performed by us, remainders like ResultSlot.onTake, vanilla guard: TFC removals, TFC overrides, grids holding CE items), fluid-container crafting remainder extension. Tests: AdvancedCraftGridTest 5, AdvancedCraftRulesTest +3, CraftingTableMockBukkitTest 7, CraftingContentDataMockBukkitTest 1 (2912 + 66 recipes compile and match their own ingredients). Ledger feature row + 5 divergences; follow-up PAR-PROC-28."
tests:
  - "paper: MockBukkit crafting a TFC recipe with a knife damages it"
  - "itest: leakage test over all recipes (moved to PAR-PROC-28)"
done_criteria:
  - "All 2912 crafting recipes resolvable (count test)"
  - "Deferred check tracked in PAR-PROC-28"
---

# PAR-PROC-03: Advanced crafting: TFC advanced recipes, food combining, casting crafting, vanilla recipe leakage

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
