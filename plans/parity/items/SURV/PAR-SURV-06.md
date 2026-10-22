---
id: PAR-SURV-06
title: "Item size and weight on Paper: container limits, overburdened and exhausted, tooltip"
area: SURV
status: open
wave: 8
deps: [PAR-INFRA-05]
size: M
aliases: ["M3-SURV-05"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java"
  - "src/main/java/net/dries007/tfc/common/component/size/Size.java"
  - "src/main/java/net/dries007/tfc/common/component/size/Weight.java"
  - "src/main/java/net/dries007/tfc/common/effect/ExhaustedEffect.java"
  - "src/generated/resources/data/tfc/tfc/item_size"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/itemsize/**"
  - "paper/src/test/java/dev/tfp/paper/itemsize/**"
  - "core/src/main/java/dev/tfp/core/item/**"
  - "core/src/test/java/dev/tfp/core/item/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/anvil/copper 2"
  - "screenshot effect icon"
  - "try to put one in a chest"
plan:
  - "Resolve size/weight per ItemStack (explicit item_size definitions first, then the class default)."
  - "Enforce on InventoryClickEvent/InventoryMoveItemEvent for chests (chestMaximumItemSize), bundles and our device inventories via a shared predicate."
  - "Every 20 ticks per player (entity scheduler): count huge/very heavy items -> exhausted / overburdened effects (enableOverburdening)."
  - "Tooltip contributor: '⚖ Weight: Heavy / ⇲ Size: Large'."
  - "Stack sizes follow Weight (vanilla max_stack_size component set in datagen? record decision)."
tests:
  - "paper: MockBukkit chest refuses a huge item"
  - "paper: overburdened applied with two anvils in inventory"
done_criteria:
  - "In-game: anvil in a chest refused; carrying 2 anvils shows overburdened"
---

# PAR-SURV-06: Item size and weight on Paper: container limits, overburdened and exhausted, tooltip

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
