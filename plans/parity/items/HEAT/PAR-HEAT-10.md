---
id: PAR-HEAT-10
title: "Torches: burnout to dead torch, relight, wall torches"
area: HEAT
status: open
wave: 14
deps: [PAR-INFRA-11, PAR-INFRA-08]
size: M
aliases: ["M3-HEAT-04 (torch)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCWallTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/DeadTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/items/TorchItem.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/light/**"
  - "core/src/test/java/dev/tfp/core/light/**"
  - "core/src/test/resources/dev/tfp/core/light/**"
  - "paper/src/main/java/dev/tfp/paper/light/**"
  - "paper/src/test/java/dev/tfp/paper/light/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/light/**"
  - "content/generated/content/craftengine/tfp/configuration/light/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:torch"
  - "place it"
  - "tfp time add days 3"
  - "relog / tp away and back"
  - "screenshot"
plan:
  - "Current: core/light burnout rules golden-tested."
  - "CE torch + dead torch (standing/wall) replacing vanilla torch placement for TFC torches; placement timestamp in block data; catch-up kind swaps to the dead torch after torchTicks; relight with firestarter/torch; torch item ages too (TFC TorchItem)."
tests:
  - "paper: MockBukkit catch-up swaps to dead torch"
  - "core: lazy-vs-stepped exists"
done_criteria:
  - "A torch placed and `tfp time add days 3` later is dead after chunk reload"
---

# PAR-HEAT-10: Torches: burnout to dead torch, relight, wall torches

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
