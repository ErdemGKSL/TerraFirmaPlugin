---
id: PAR-PROC-33
title: "Basket in-game verification: small items go in, a log is refused, bundle tooltip at GUI scale 2 and 3"
area: PROC
status: done
wave: 10
deps: [PAR-PROC-22]
size: S
aliases: ["deferred in-game check of PAR-PROC-22 (PROTOCOL 6.5): its screenshot stage"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCBundleItem.java"
  - "src/generated/resources/data/tfc/tfc/item_size/basket.json"
files_owned:
  - "docs/verification/PAR-PROC-22/**"
  - "docs/verification/PAR-PROC-33/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 (docs/testing/headless.md, CraftEngine pack self-hosted on 127.0.0.1); rcon: gamemode survival Bot, weather clear, time set noon, a lit snow_block room"
  - "ce item give Bot tfp:basket; give Bot minecraft:stick 16, minecraft:stone 16, minecraft:oak_log 4"
  - "Open the inventory; click the stick stack onto the basket, then the stone stack: both go in; screenshot the basket's bundle tooltip (GUI scale 2 and 3)"
  - "Click the oak_log stack onto the basket: refused (the log stays on the cursor, the basket contents do not change); screenshot"
plan:
  - "Deferred from PAR-PROC-22 (PROTOCOL 6.5): PAR-PROC-22's rule is done and tested (BasketItemTest, BasketMockBukkitTest: TFC's size rule, all 17 vanilla bundle materials, both click paths). Its screenshot stage could not run because the single headless server slot was held by another item's server; the check moved here so it does not hold the item. PAR-PROC-22 does not depend on this item."
  - "Run the in_game_steps (cheap-model agent where available); the main session judges every screenshot and writes docs/verification/PAR-PROC-22/verdict.md."
  - "Done: in-game run on Paper 1.21.11 + client 1.21.11 at GUI scale 2 and 3: 16 stick and 16 stone went into tfp:basket (bundle tooltip lists both, half-full bar), 4 oak logs were refused (stayed on the cursor, bundle_contents unchanged per RCON); the icon matches TFC's basket texture. No code change. Evidence and verdict (all pass): docs/verification/PAR-PROC-22/."
tests:
  - "None beyond the in-game steps (the rule is unit-tested in PAR-PROC-22)"
done_criteria:
  - "Screenshots in docs/verification/PAR-PROC-22/ at GUI scale 2 and 3: the basket shows TFC's texture and name, stick and stone are in its tooltip, the oak log is refused; verdict.md per criterion"
---

# PAR-PROC-33: Basket in-game verification: small items go in, a log is refused, bundle tooltip at GUI scale 2 and 3

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Deferred in-game check of PAR-PROC-22 (PROTOCOL §6.5).
