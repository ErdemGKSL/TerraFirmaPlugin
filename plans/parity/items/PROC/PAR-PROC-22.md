---
id: PAR-PROC-22
title: "Basket: TFC's bundle item (TFCBundleItem) with its size limits"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "TFCItems.BASKET is new TFCBundleItem(...BUNDLE_CONTENTS EMPTY): a vanilla bundle that only takes items TFC's size rules allow. Generate tfp:basket on a bundle material with bundle_contents and TFC's model; enforce TFC's size limit in a Paper listener (ItemSizes from PAR-SURV-06). Once the CE item exists the coverage gate (dev.tfp.datagen.material.ItemCoverage) reads it from basket.yml; its PAR-PROC-22 owner rule stays harmless."
  - "Done: datagen dev.tfp.datagen.basket.BasketItem writes tfp:basket (configuration/basket.yml: material bundle, components minecraft:bundle_contents [], TFC model/texture, stack 1 from item_size/basket, names in assets/tfp_basket/lang); the coverage report maps tfc:basket to ce:basket.yml. The size limit is PAR-SURV-06's ContainerSizeListener bundle rule (matches any bundle-material item, so no new listener). Tests: BasketItemTest (3), BasketMockBukkitTest (5: refusals, acceptances, all 17 vanilla bundle materials on both the bundle-action and the right-click path, a non-bundle control, basket on the cursor). Ledger: feature row + 3 divergences (creative inventory unchecked, 1.21.4+ bundle UI, any bundle material / vanilla fill rule). In-game screenshot check: deferred to PAR-PROC-33 (PROTOCOL 6.5)."
  - "Unblock round: the reviewers accepted the rule; the only open stage was the screenshot run, blocked twice by another item's headless server holding the single server slot. The steps and the screenshot criterion moved to PAR-PROC-33 (ui: false here). The vanilla-bundle test now names every bundle material and exercises the right-click path, where the slot material decides, plus a non-bundle control (the bundle actions accept any slot, so they alone did not test the material). Cosmetic: isBundle matches by the material name ending in bundle (noted in the Divergences row)."
tests:
  - "tools/datagen: tfp:basket exists with bundle_contents; the coverage report names basket.yml"
  - "paper: a too-large item is refused (MockBukkit)"
done_criteria:
  - "Basket holds what TFC's basket holds and refuses the rest"
  - "Deferred check tracked in PAR-PROC-33"
---

# PAR-PROC-22: Basket: TFC's bundle item (TFCBundleItem) with its size limits

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
