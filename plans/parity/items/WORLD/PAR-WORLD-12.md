---
id: PAR-WORLD-12
title: "Plant blocks: all TFC plant shapes as CE blocks in generation"
area: WORLD
status: open
wave: 24
deps: [PAR-INFRA-08]
size: M
aliases: ["M2-GEN-13 (blocks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/**"
  - "content/generated/content/craftengine/tfp/configuration/plant/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantBlocks.java"
ui: true
in_game_steps:
  - "Fresh world; screenshot a meadow; tfp debug block on a flower"
plan:
  - "Generate every Plant enum entry with its shape (age states, tall halves, water-logged variants) using cross/tripwire/carpet carriers per the visuals skill; record pool usage."
  - "PlantBlocks writes CE ids instead of vanilla placeholders."
tests:
  - "tools/datagen: counts vs Plant.java"
  - "VisualBudgetTest green"
done_criteria:
  - "Fresh world shows TFC flowers/grasses"
---

# PAR-WORLD-12: Plant blocks: all TFC plant shapes as CE blocks in generation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
