---
id: PAR-WORLD-10
title: "Wood blocks: logs, wood, stripped, leaves, saplings as CE blocks"
area: WORLD
status: open
wave: 21
deps: [PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["M2-GEN-12 (blocks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/LogBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/wood/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/wood/**"
  - "content/generated/content/craftengine/tfp/configuration/wood/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TreeBlocks.java"
ui: true
in_game_steps:
  - "Fresh world; screenshot a forest; tfp debug block on a log and leaves"
plan:
  - "Generate the 20 woods x (log, stripped log, wood, stripped wood, leaves, sapling, potted sapling, fallen leaves, twig) with the leaves visual pool (leaves keep vanilla tint via repurposed leaves states)."
  - "TreeBlocks writes the CE ids; leaves `persistent=false` state as TFC."
tests:
  - "tools/datagen: counts vs Wood.java"
done_criteria:
  - "Trees in a fresh world use TFC textures"
---

# PAR-WORLD-10: Wood blocks: logs, wood, stripped, leaves, saplings as CE blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
