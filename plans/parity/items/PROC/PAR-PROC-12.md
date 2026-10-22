---
id: PAR-PROC-12
title: "Panning"
area: PROC
status: open
wave: 21
deps: [PAR-WORLD-09, PAR-INFRA-09]
size: M
aliases: ["M3-PAN-01 (pan)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/EmptyPanItem.java"
  - "src/main/java/net/dries007/tfc/common/items/PanItem.java"
  - "src/generated/resources/data/tfc/tfc/deposit"
files_owned:
  - "core/src/main/java/dev/tfp/core/pan/**"
  - "core/src/test/java/dev/tfp/core/pan/**"
  - "core/src/test/resources/dev/tfp/core/pan/**"
  - "paper/src/main/java/dev/tfp/paper/pan/**"
  - "paper/src/test/java/dev/tfp/paper/pan/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/pan/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/pan/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/pan"
  - "use on an ore deposit block; stand in water; hold right-click 6 s"
plan:
  - "Empty pan on a deposit block fills it (block removed); using the filled pan in water for 120 ticks rolls the deposit loot table (datagen emits deposit loot tables); pan stage models via custom_model_data."
tests:
  - "paper: use-duration completion and loot roll"
done_criteria:
  - "Panning a native copper deposit yields small ores"
---

# PAR-PROC-12: Panning

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
