---
id: PAR-INFRA-04
title: "Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds)"
area: INFRA
status: done
wave: 1
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java"
  - "src/main/java/net/dries007/tfc/common/component/fluid/FluidContainer.java"
  - "src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java"
  - "src/main/java/net/dries007/tfc/common/component/item/ItemContainer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/itemdata/**"
  - "core/src/test/java/dev/tfp/core/itemdata/**"
  - "core/src/test/resources/dev/tfp/core/itemdata/**"
  - "paper/src/main/java/dev/tfp/paper/itemdata/**"
  - "paper/src/test/java/dev/tfp/paper/itemdata/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/fluid ports the container table and fill/drain rules; there is no item codec."
  - "Add versioned PDC codecs `tfp:fluid` (fluid id + amount) and `tfp:contents` (list of serialized ItemStacks, bounded) with the same rules as FoodPdc/HeatPdc (newer version kept untouched)."
  - "Add `ItemFluids` (read/write/fill/drain on ItemStack via core FluidContainerRules) and `ItemContents` (vessel slots)."
  - "Make heat and food codecs read nested item lists (a vessel's contents keep their food dates)."
  - "Note: FoodPdc/HeatPdc needed no change: each `tfp:contents` slot is the whole serialized stack with its own PDC, so `FoodPdc.read`/`HeatPdc.read` work on the slots `ContentsPdc` returns. MockBukkit cannot serialize nested PDC containers, so the nested test uses an in-memory slot codec; the real-server check is the follow-up PAR-INFRA-12."
  - "Done: core/itemdata (ItemSlotRules insert/extract, ItemDataMatching merge rule, ItemListLimits), paper/itemdata (FluidPdc tfp:fluid, ContentsPdc tfp:contents, ItemFluids, ItemContents); tests core ItemDataRulesTest (5), paper ItemDataPdcMockBukkitTest (8); two ledger divergences."
  - "Review fix: filled fluid items never stack (TFC FluidContainerItem/MoldItem.getMaxStackSize = 1 with fluid): FluidPdc sets max stack size 1 on fill and restores the item's own size on empty; ItemContents.canMerge is false when either holds fluid; ItemFluids fill/drain refuse stacks of more than one item (no fluid duplication). Corrected TFC ids (tfc:contents, not tfc:item_list) and list sizes (powderkeg 12, large vessel 9; the small vessel is VesselComponent, its alloy needs its own codec). Tests: core ItemDataRulesTest (6), paper ItemDataPdcMockBukkitTest (10)."
tests:
  - "paper: MockBukkit round trip for both codecs incl. unknown newer version"
  - "core: fill/drain golden rows already exist; add the stack-merge rule for fluid items (no merge when contents differ)"
done_criteria:
  - "Fluid and item-list components round-trip on items and are used by at least one caller test"
  - "Ledger row added"
---

# PAR-INFRA-04: Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds)

> see_not_parity: NP-DATA-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
