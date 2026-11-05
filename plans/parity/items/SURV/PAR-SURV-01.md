---
id: PAR-SURV-01
title: "All TFC food items generated as CE items with food_stamp and food data"
area: SURV
status: done
wave: 5
deps: []
size: M
aliases: ["M3-SURV-01 (1)", "gaps: M3-SURV-02 food items"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/Food.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/generated/resources/data/tfc/tfc/food"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/food/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/food/**"
  - "content/generated/content/craftengine/tfp/configuration/food.yml"
ui: true
in_game_steps:
  - "ce item give Bot tfp:food/wheat_bread"
  - "ce item give Bot tfp:food/grain_soup"
  - "ce item give Bot tfp:jar/blackberry, tfp:blackberry_jam, tfp:food/mutton, tfp:food/red_apple, tfp:food/dairy_salad, tfp:food/cooked_beef, tfp:food/wheat_flour, tfp:jar/peanut_unsealed (10 sampled ids with the two above)"
  - "Hover: name shown"
plan:
  - "Current: food.yml holds some food items (mutton verified edible); not every Food enum entry, soups, salads, jams, fruit preserves, sealed/unsealed jars."
  - "Generate every TFC food item with `data: { tfp:food_stamp: {} }`, vanilla `food`/`consumable` components with TFC's eat time, the item names from en_us, and stack size from item_size."
  - "Move the existing food emission into datagen/food (keep ids stable)."
  - "Note: the move needed two small edits outside files_owned, both named by this plan: `AssetPipeline` lost its food emission (models, food.yml) and its pack-lang filter gained `item.tfc.jar.*` and `item.tfc.*_jam`; `Datagen.generate` got the `FoodItems.run` line (shared hub)."
  - "Done: `dev.tfp.datagen.food.FoodItems` + `TfcItemData` generate all 258 TFC food items (182 foods, 22 jars, 22 unsealed jars, 22 jams, 5 soups, 5 salads) into `configuration/food.yml` with `tfp:food_stamp`, name key, model/textures, `food` + `consumable` 1.6 s when edible, and the `item_size` stack size (first match in TFC's load order). Tests: `FoodItemsTest` (7). Divergences: jar lore, unsealed-jar remainder, vanilla nutrition, baked stack size (ledger). In-game check: the workflow's screenshot step, evidence in `docs/verification/PAR-SURV-01/`."
tests:
  - "tools/datagen: count = TFC food item count; every item has a food definition and a name"
done_criteria:
  - "`ce item give Bot tfp:food/<each>` works for a sampled 10; all have names"
---

# PAR-SURV-01: All TFC food items generated as CE items with food_stamp and food data

> see_not_parity: NP-CLIENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
