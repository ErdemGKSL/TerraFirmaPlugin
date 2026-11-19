---
id: PAR-POT-11
title: "item_heat data for fired pottery: vessels, molds, jugs"
area: POTTERY
status: open
wave: 25
deps: [PAR-POT-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/VesselItem.java"
  - "src/main/java/net/dries007/tfc/common/items/MoldItem.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/potteryheat/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/potteryheat/**"
ui: false
in_game_steps: []
plan:
  - "TFC gives the fired vessel and the molds the pottery heat capacity (1.2, `HeatCapability.POTTERY_HEAT_CAPACITY`) as a default item component, not as `item_heat` data. Our `ItemHeatDefinitions` therefore has no entry for `tfp:ceramic/vessel`, the 16 glazed vessels or the molds, so the item heater (`tfp debug warm`, forge, firepit) and the heat tooltip treat them as unheatable (found in PAR-POT-04's in-game check). Emit `tfp/item_heat` definitions with heat_capacity 1.2 for those ids (add a datagen generator + one `run` line in Datagen.java), keep the vessel's content-dependent capacity a ledger divergence."
tests:
  - "datagen: the generated item_heat covers every fired vessel and mold id with 1.2"
done_criteria:
  - "`tfp debug warm Bot 1100 2000` heats a held fired vessel and the heat tooltip line shows"
---

# PAR-POT-11: item_heat data for fired pottery

Follow-up of PAR-POT-04. Follow `plans/parity/PROTOCOL.md`.
