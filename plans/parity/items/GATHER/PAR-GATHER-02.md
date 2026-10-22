---
id: PAR-GATHER-02
title: "Harvest plants, leaves, twigs and fallen leaves by TFC loot tables"
area: GATHER
status: open
wave: 25
deps: [PAR-GATHER-01, PAR-WORLD-10, PAR-WORLD-12]
size: M
aliases: ["M3-GATH-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java"
  - "src/main/resources/data/tfc/loot_table/blocks"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/harvest/**"
  - "paper/src/test/java/dev/tfp/paper/harvest/**"
  - "core/src/main/java/dev/tfp/core/gather/Harvest*"
  - "core/src/test/java/dev/tfp/core/gather/Harvest*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:stone/knife/igneous_extrusive (any knife)"
  - "break tall grass"
  - "screenshot"
plan:
  - "Break and right-click hooks for the 277 harvest tables (core HarvestTables) with the held tool (knife/scythe/sharp tag), creative handling."
tests:
  - "paper: MockBukkit break with knife vs hand"
done_criteria:
  - "Cutting grass with a knife yields straw in-game"
---

# PAR-GATHER-02: Harvest plants, leaves, twigs and fallen leaves by TFC loot tables

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
