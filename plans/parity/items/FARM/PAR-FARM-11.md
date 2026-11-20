---
id: PAR-FARM-11
title: "Plant regrowth, plant growth/spread, flower cuttings"
area: FARM
status: done
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
ui: false
in_game_steps:
  - "cut grass; tfp time add days 10; screenshot"
plan:
  - "Lazy plant growth (age states) and regrowth of harvested plants; flower cuttings plant a flower."
  - "Done: core regrowth (SpreadKind, PlantKinds vs Plant.java golden, PlantRegrowth, PlantSweepRules) + 11 tests; paper regrowth (PlantSweeper lazy chunk sweep for short and tall grasses, CuttingListener shears on a flower, CuttingPdc, the tfc:flower_cutting crafting modifier, RegrowthModule) + 4 MockBukkit tests; ledger rows and divergences. Pending in-game check (container cannot run client). Follow-ups: PAR-FARM-21 (cutting item content), PAR-FARM-22 (creeping and floating plants, rising rocks, in-game check once PAR-WORLD-46 places plants)."
tests:
  - "core: regrowth golden"
done_criteria:
  - "Harvested grass regrows: deferred check tracked in PAR-FARM-22"
---

# PAR-FARM-11: Plant regrowth, plant growth/spread, flower cuttings

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
