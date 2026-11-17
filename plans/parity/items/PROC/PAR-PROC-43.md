---
id: PAR-PROC-43
title: "Vanilla recipes newer than TFC's 1.21.1 follow TFC's removal policy: copper tools and armour, spears and other post-1.21.1 tool/armour recipes disabled"
area: PROC
status: open
wave: 18
deps: [PAR-PROC-03, PAR-INFRA-20]
size: S
aliases: ["parity audit 2026-11-17: ledger divergence 'TFC targets MC 1.21.1: its data/minecraft/recipe overrides and removals cover the vanilla 1.21.1 recipe set only' (revisit: 'a datagen override adding the new families TFC removes in 1.21.1 ... to disabledRecipes'); PAR-PROC-28 only lists them in CraftLeakageCase"]
tfc_refs:
  - "src/generated/resources/data/minecraft/recipe"
  - "src/main/java/net/dries007/tfc/common/recipes/TFCRecipeTypes.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/recipe/NewerVanillaRecipes.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/recipe/NewerVanillaRecipesTest.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/recipe/newer_vanilla_recipes.txt"
  - "tools/datagen/src/main/java/dev/tfp/datagen/DataExport.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DataExportTest.java"
  - "paper/src/main/java/dev/tfp/paper/craft/KeptVanillaRecipes.java"
  - "docs/verification/PAR-PROC-43/**"
ui: true
in_game_steps:
  - "give Bot minecraft:copper_ingot 8 and minecraft:stick 2; open a crafting table; lay out a copper pickaxe: no result; screenshot at GUI scale 2"
  - "lay out a copper chestplate and a wooden spear (vanilla planks): no result; screenshot"
  - "lay out minecraft:stick from two vanilla planks (a kept recipe): result shows (control)"
plan:
  - "TFC (data/minecraft/recipe, generated) disables every vanilla wooden, stone, iron, golden and diamond tool and armour recipe of 1.21.1 through a false neoforge:conditions, plus the other families it replaces. Vanilla 1.21.9+ added copper tools and armour, 1.21.11 spears (and 26.x may add more); our server keeps them, so they craft from vanilla copper ingots and planks (ledger). Owner policy (auditor decision 2026-11-17, recorded as a divergence): a vanilla tool, weapon or armour recipe newer than 1.21.1 is disabled like its 1.21.1 siblings, other new families (shelves, decorative blocks) stay until an item decides otherwise."
  - "Datagen: a pinned list of the newer recipe ids per supported version (1.21.11, 26.x), derived once from the vanilla server jars' data by a scratch script kept outside the repo and stored as newer_vanilla_recipes.txt with the selection rule written above it; DataExport adds the selected ids to RegistrySnapshot.disabledRecipes. CraftListener already blocks disabled ids (table.disabled), so no Paper change beyond the KeptVanillaRecipes comment about wooden_spear."
  - "Update the ledger row (divergence: the policy is ours, TFC has no data for these versions). PAR-PROC-28's CraftLeakageCase may keep listing these ids; it is not ours to edit."
tests:
  - "datagen: NewerVanillaRecipesTest pins the selected ids (copper tools/armour, every spear) and that none is a TFC-overridden id; DataExportTest sees them in disabledRecipes"
done_criteria:
  - "Copper tools/armour and spears do not craft on 1.21.11 (and 26.x when its list is filled); kept vanilla recipes still craft"
  - "./gradlew check and verifyContent pass; screenshots and verdict in docs/verification/PAR-PROC-43/"
---

# PAR-PROC-43: Vanilla recipes newer than TFC's 1.21.1 follow TFC's removal policy

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
