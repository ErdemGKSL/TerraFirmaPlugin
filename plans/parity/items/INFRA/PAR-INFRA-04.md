---
id: PAR-INFRA-04
title: "Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds)"
area: INFRA
status: open
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
tests:
  - "paper: MockBukkit round trip for both codecs incl. unknown newer version"
  - "core: fill/drain golden rows already exist; add the stack-merge rule for fluid items (no merge when contents differ)"
done_criteria:
  - "Fluid and item-list components round-trip on items and are used by at least one caller test"
  - "Ledger row added"
---

# PAR-INFRA-04: Item data components on PDC: fluid contents and item lists (vessels, jugs, buckets, molds)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
