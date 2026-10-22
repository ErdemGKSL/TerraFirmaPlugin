---
id: PAR-HEAT-03
title: "Hot items: burn damage while held, item entities cool in water and snow"
area: HEAT
status: open
wave: 11
deps: []
size: S
aliases: ["M3-HEAT-01 (burn)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/heat/HotItem*"
  - "paper/src/test/java/dev/tfp/paper/heat/HotItem*"
ui: true
in_game_steps:
  - "tfp debug heat Bot 900"
  - "drop the ingot in water; wait 5 s; pick up; tfp debug heat Bot"
plan:
  - "Holding an item above the burn threshold without tongs sets the player on fire briefly as TFC (check the exact rule in ForgeEventHandler/PlayerTick)."
  - "Item entities above 0 C in water or on snow cool faster (TFC onItemExpire / item entity tick: coolHotItemEntities) via a lazy cooling modifier written when the item is picked up; steam particle only at that moment."
tests:
  - "core: cooling modifier golden"
done_criteria:
  - "Dropping a 900 C ingot in water and picking it up after 5 s reads colder than in air"
---

# PAR-HEAT-03: Hot items: burn damage while held, item entities cool in water and snow

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
