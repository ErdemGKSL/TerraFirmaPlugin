---
id: PAR-FARM-11
title: "Plant regrowth, plant growth/spread, flower cuttings"
area: FARM
status: open
wave: 33
deps: [PAR-WORLD-12, PAR-INFRA-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java"
  - "src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/regrowth/**"
  - "core/src/test/java/dev/tfp/core/regrowth/**"
  - "core/src/test/resources/dev/tfp/core/regrowth/**"
  - "paper/src/main/java/dev/tfp/paper/regrowth/**"
  - "paper/src/test/java/dev/tfp/paper/regrowth/**"
ui: true
in_game_steps:
  - "cut grass; tfp time add days 10; screenshot"
plan:
  - "Lazy plant growth (age states) and regrowth of harvested plants; flower cuttings plant a flower."
tests:
  - "core: regrowth golden"
done_criteria:
  - "Harvested grass regrows"
---

# PAR-FARM-11: Plant regrowth, plant growth/spread, flower cuttings

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
