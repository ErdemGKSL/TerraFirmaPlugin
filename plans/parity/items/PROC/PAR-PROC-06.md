---
id: PAR-PROC-06
title: "Loom: weaving cloth, burlap, wool, silk"
area: PROC
status: open
wave: 41
deps: [PAR-INFRA-02, PAR-DECO-01]
size: S
aliases: ["M3-PROC-02 (loom)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/loom"
files_owned:
  - "core/src/main/java/dev/tfp/core/loom/**"
  - "core/src/test/java/dev/tfp/core/loom/**"
  - "core/src/test/resources/dev/tfp/core/loom/**"
  - "paper/src/main/java/dev/tfp/paper/loom/**"
  - "paper/src/test/java/dev/tfp/paper/loom/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/loom/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/loom/**"
  - "core/src/main/java/dev/tfp/core/craft/Loom*"
ui: true
in_game_steps:
  - "tfp debug place tfp:wood/loom/oak"
  - "add 16 jute fiber; push repeatedly; screenshot"
plan:
  - "Per-wood loom block; add inputs, right-click pushes (LoomRules cooldown); in-progress texture; output taken by hand."
tests:
  - "paper: push cooldown"
done_criteria:
  - "Weaving 12 wool yarn gives wool cloth"
---

# PAR-PROC-06: Loom: weaving cloth, burlap, wool, silk

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
