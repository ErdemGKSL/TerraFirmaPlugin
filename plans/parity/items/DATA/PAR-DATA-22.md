---
id: PAR-DATA-22
title: "Piglin currency: TFC gold ingots barter with piglins (follow-up of PAR-DATA-14)"
area: DATA
status: done
wave: 52
deps: [PAR-DATA-14]
size: S
aliases: ["follow-up of PAR-DATA-14 (BarteringItem)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/BarteringItem.java"
  - "src/main/java/net/dries007/tfc/util/Metal.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/piglin/**"
  - "core/src/test/java/dev/tfp/core/piglin/**"
  - "paper/src/main/java/dev/tfp/paper/piglin/**"
  - "paper/src/test/java/dev/tfp/paper/piglin/**"
ui: false
in_game_steps: []
plan:
  - "TFC: the gold ingot (Metal.isPiglinCurrency, GOLD only) is a BarteringItem, so vanilla piglins admire, pick up and barter it like the vanilla gold ingot. Ours: tfp:metal/ingot/gold is a CraftEngine item on paper, which vanilla piglins ignore, and Paper's barter API is per Material only (Piglin#addBarterMaterial), so adding PAPER would make every paper-based CE item wanted."
  - "Build: on a piglin interaction or on a dropped TFC gold ingot near a piglin, hand the piglin a vanilla gold ingot it barters normally (a PDC-marked item entity players cannot pick up and that despawns if untaken), consuming the TFC ingot; or (better) a PiglinBarterEvent-driven path if a Paper API to start admiring exists by then. Keep vanilla's barter loot (PiglinBarterEvent outcome) and the piglin_loved rules; record the divergence."
  - "Done: core PiglinCurrency (gold only, the admire rule); paper PiglinCurrencyListener (PlayerInteractEntityEvent on Piglin: takes one TFC ingot unless creative, drops a PDC-marked vanilla gold ingot at the piglin that only piglins can pick up (EntityPickupItemEvent, InventoryPickupItemEvent, ItemMergeEvent) and gives the TFC ingot back after 100 ticks if untaken) and PiglinModule (service-file line). Vanilla's AI and barter loot (PiglinBarterEvent) are untouched. Tests: core PiglinCurrencyTest 2, paper PiglinCurrencyMockBukkitTest 6. Dropped TFC ingots stay unwanted (divergence recorded). Pending in-game check (container cannot run client).",
tests:
  - "paper: MockBukkit: a player right-clicking a piglin with a TFC gold ingot loses one ingot and the piglin gets the barter item; other paper-based items are refused; the marker item cannot be picked up by players"
done_criteria:
  - "A TFC gold ingot barters with a piglin like the vanilla gold ingot in TFC; other TFC ingots do not"
---

# PAR-DATA-22: Piglin currency

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
