---
id: PAR-PROC-22
title: "Basket: TFC's bundle item (TFCBundleItem) with its size limits"
area: PROC
status: open
wave: 9
deps: [PAR-PROC-19, PAR-SURV-06]
size: S
aliases: ["follow-up of PAR-PROC-19 (TFCItems.BASKET is not a plain item)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCBundleItem.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/generated/resources/data/tfc/tfc/item_size/basket.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/basket/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/basket/**"
  - "content/generated/content/craftengine/tfp/configuration/basket.yml"
  - "paper/src/main/java/dev/tfp/paper/basket/**"
  - "paper/src/test/java/dev/tfp/paper/basket/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:basket"
  - "Put small items in the basket by clicking them onto it; screenshot the bundle tooltip; try a large item (refused)"
plan:
  - "TFCItems.BASKET is new TFCBundleItem(...BUNDLE_CONTENTS EMPTY): a vanilla bundle that only takes items TFC's size rules allow. Generate tfp:basket on a bundle material with bundle_contents and TFC's model; enforce TFC's size limit in a Paper listener (ItemSizes from PAR-SURV-06). Once the CE item exists the coverage gate (dev.tfp.datagen.material.ItemCoverage) reads it from basket.yml; its PAR-PROC-22 owner rule stays harmless."
tests:
  - "tools/datagen: tfp:basket exists with bundle_contents; the coverage report names basket.yml"
  - "paper: a too-large item is refused (MockBukkit)"
done_criteria:
  - "Basket holds what TFC's basket holds and refuses the rest"
---

# PAR-PROC-22: Basket: TFC's bundle item (TFCBundleItem) with its size limits

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
