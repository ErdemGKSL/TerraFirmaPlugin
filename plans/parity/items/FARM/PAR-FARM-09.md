---
id: PAR-FARM-09
title: "Bananas"
area: FARM
status: open
wave: 32
deps: [PAR-FARM-08]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaSaplingBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/banana/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/banana/**"
  - "core/src/main/java/dev/tfp/core/banana/**"
  - "core/src/test/java/dev/tfp/core/banana/**"
ui: true
in_game_steps:
  - "plant banana sapling; tfp time add months 6; screenshot"
plan:
  - "Banana plant growth stages and harvest as TFC."
tests:
  - "core: growth golden"
done_criteria:
  - "A banana plant fruits"
---

# PAR-FARM-09: Bananas

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
