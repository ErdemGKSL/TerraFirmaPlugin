---
id: PAR-SURV-01
title: "All TFC food items generated as CE items with food_stamp and food data"
area: SURV
status: open
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
  - "ce item give Bot tfp:food/bread/wheat"
  - "ce item give Bot tfp:food/soup/grain (or the TFC soup id)"
  - "Hover: name shown"
plan:
  - "Current: food.yml holds some food items (mutton verified edible); not every Food enum entry, soups, salads, jams, fruit preserves, sealed/unsealed jars."
  - "Generate every TFC food item with `data: { tfp:food_stamp: {} }`, vanilla `food`/`consumable` components with TFC's eat time, the item names from en_us, and stack size from item_size."
  - "Move the existing food emission into datagen/food (keep ids stable)."
tests:
  - "tools/datagen: count = TFC food item count; every item has a food definition and a name"
done_criteria:
  - "`ce item give Bot tfp:food/<each>` works for a sampled 10; all have names"
---

# PAR-SURV-01: All TFC food items generated as CE items with food_stamp and food data

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
