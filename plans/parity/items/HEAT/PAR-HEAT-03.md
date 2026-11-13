---
id: PAR-HEAT-03
title: "Hot items: burn damage while held, item entities cool in water and snow"
area: HEAT
status: done
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
  - "Note: TFC at c3b4d9536 has no burn rule for held hot items (no tongs; nothing in ForgeEventHandler, the player tick or HeatCapability), so none is added (parity)."
  - "Done: HotItemCooling (pure replay of onItemExpire: water 50, snow 70, snow block 75, ice/sea ice 100, packed ice 125 per 10-tick check, with melt/evaporation chances), HotItemListener (pickup, hopper, despawn; entity PDC progress; region-applied melts; keeps hot items from despawning), HotItemModule; tests HotItemCoolingTest 10, HotItemListenerTest 6; in-game docs/verification/PAR-HEAT-03/ (water 358.6 C vs air 860.8 C)"
tests:
  - "core: cooling modifier golden"
done_criteria:
  - "Dropping a 900 C ingot in water and picking it up after 5 s reads colder than in air"
---

# PAR-HEAT-03: Hot items: burn damage while held, item entities cool in water and snow

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
