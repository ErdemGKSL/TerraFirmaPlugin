---
id: PAR-PROC-11
title: "Composter"
area: PROC
status: open
wave: 11
deps: [PAR-INFRA-02, PAR-INFRA-11]
size: S
aliases: ["M3-PROC-05"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/compost/**"
  - "core/src/test/java/dev/tfp/core/compost/**"
  - "core/src/test/resources/dev/tfp/core/compost/**"
  - "paper/src/main/java/dev/tfp/paper/compost/**"
  - "paper/src/test/java/dev/tfp/paper/compost/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/compost/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/compost/**"
  - "content/generated/content/craftengine/tfp/configuration/compost/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:composter"
  - "add 4 green 4 brown"
  - "tfp time add days 12"
  - "sneak-right-click; screenshot"
plan:
  - "Per-wood composter? (TFC: one composter); add greens/browns, lazy ready time, sneak extraction, rotten state and pest attraction (PAR-FAUNA-13), hopper automation, ready particles only on interaction."
tests:
  - "core: CompostGoldenTest exists"
  - "paper: extraction"
done_criteria:
  - "Compost ready after composterTicks"
---

# PAR-PROC-11: Composter

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
