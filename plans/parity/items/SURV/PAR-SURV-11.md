---
id: PAR-SURV-11
title: "Item size in-game check: anvil refused by a chest, two anvils overburden, size tooltip"
area: SURV
status: open
wave: 29
deps: [PAR-METAL-03]
size: S
aliases: ["deferred in-game check of PAR-SURV-06 (PROTOCOL §6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java"
files_owned:
  - "docs/verification/PAR-SURV-06/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/anvil/copper 2"
  - "hover the anvil in the inventory: the first tooltip line reads '⚖ Very Heavy ⇲ Huge' in grey (screenshot, GUI scale 2 and 3)"
  - "with both anvils in the inventory, wait 1 s: the action bar says 'Overburdened' and the bot cannot walk or jump (screenshot of the action bar; F3 position unchanged after holding W for 2 s)"
  - "drop one anvil: within 1 s the action bar says 'Exhausted' and walking works again"
  - "place a chest, open it, try to put the anvil in by click, shift-click and number key: it stays in the player inventory (screenshot of the open chest); a copper ingot goes in"
  - "switch to creative with both anvils: walking works at once; back to survival: overburdened again"
plan:
  - "Deferred from PAR-SURV-06 (PROTOCOL §6.5): the runtime and its MockBukkit tests are done there (paper/itemsize, core/item); the anvil items (tfp:metal/anvil/*) come with PAR-METAL-03, so the in-game steps cannot run before it."
  - "Run every in_game_step on the headless Paper 1.21.11 server and client (spikes/client/, CraftEngine self-host), store crops and verdict.md in docs/verification/PAR-SURV-06/. A failure is fixed through PAR-SURV-06's files (paper/itemsize, core/item) in a follow-up."
tests:
  - "in-game only (screenshots)"
done_criteria:
  - "In-game: anvil in a chest refused; carrying 2 anvils shows overburdened (screenshots, docs/verification/PAR-SURV-06/verdict.md)"
---

# PAR-SURV-11: Item size in-game check: anvil refused by a chest, two anvils overburden, size tooltip

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
