---
id: PAR-INFRA-20
title: "Datagen exports TFC's overrides of vanilla recipes (data/minecraft/recipe, 66 files)"
area: INFRA
status: open
wave: 4
deps: [PAR-INFRA-06]
size: S
aliases: ["follow-up of PAR-INFRA-06 (recipe count test vs TFC)"]
tfc_refs:
  - "src/generated/resources/data/minecraft/recipe"
  - "src/main/java/net/dries007/tfc/common/recipes/RecipeHelpers.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/DataExport.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DataExportTest.java"
  - "paper/src/test/java/dev/tfp/paper/recipe/RecipeIndexContentDataMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: DataExport copies only data/tfc/{tfc,recipe,tags} plus foreign tags. TFC also ships 66 recipes under data/minecraft/recipe (48 minecraft:crafting_shaped, 18 minecraft:crafting_shapeless: beds, concrete powder, rails, compass, clock, bow, shield, piston, hopper, ...) that replace vanilla's recipes of the same id with TFC ingredients, and the 195 vanilla recipes TFC disables with a false neoforge:conditions."
  - "Export data/minecraft/recipe/** with the usual id rename (tfc: -> tfp: inside the JSON), so the recipe registry and the PAR-INFRA-06 index hold them under their minecraft: ids; keep the disabled ones recorded as disabled (RegistrySnapshot.disabledRecipes) so PAR-PROC-03 can block the vanilla originals."
  - "Then drop VANILLA_OVERRIDES_NOT_EXPORTED in RecipeIndexContentDataMockBukkitTest: every per-type count must equal TFC_COUNTS."
tests:
  - "datagen: the export has the 66 minecraft: recipes and the disabled set"
  - "paper: RecipeIndexContentDataMockBukkitTest counts equal TFC's for every type"
done_criteria:
  - "Index counts equal TFC's for every recipe type, crafting included"
---

# PAR-INFRA-20: Datagen exports TFC's overrides of vanilla recipes (data/minecraft/recipe, 66 files)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
