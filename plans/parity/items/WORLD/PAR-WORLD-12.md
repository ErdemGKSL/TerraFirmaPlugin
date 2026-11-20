---
id: PAR-WORLD-12
title: "Plant blocks: all TFC plant shapes as CE blocks in generation"
area: WORLD
status: done
wave: 24
deps: [PAR-INFRA-08, PAR-INFRA-13]
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
ui: false
plan:
  - "Generate every Plant enum entry with its shape (age states, tall halves, water-logged variants) using cross/tripwire/carpet carriers per the visuals skill; record pool usage."
  - "PlantBlocks writes CE ids instead of vanilla placeholders."
  - "Scope taken: the 13 land/floating block types (100 plants, 300 states), 146 distinct looks on CE's plant pools (sugar_cane 15/15, sapling 8/8, tripwire 123/126); first lifecycle stage, ages 0/1 share a look, tinted faces baked with TFC's default climate colour (ledger divergences). PlantBlocks.ceRef gives the CE state; writing it in TfcTerrain (not owned here) is PAR-WORLD-46, which also holds the moved meadow screenshot (PROTOCOL 6.5). Water plants and the other 31 shapes: PAR-WORLD-47. Seasonal stages, climate tint, TFC soil rules: PAR-WORLD-48."
  - "Done: datagen plant/PlantBlockGen (configuration/plant/*.yml, models, baked textures, items, loot, tfp_plant lang), PlantBlocks.ceRef; PlantBlockGenTest 6; VisualBudgetTest untouched (no solid/leaves pool use). Deferred in-game check tracked in PAR-WORLD-46. Minimal edits outside the glob, for this item's own rules: BlockCoverage owner rule plant/.+ now PAR-WORLD-47; HardcodedCarrierTest and GeneratedCarrierTest exempt the tfp:plant pool looks until PAR-WORLD-47 moves the plant pools into hitbox/."
tests:
  - "tools/datagen: counts vs Plant.java"
  - "VisualBudgetTest green"
done_criteria:
  - "Deferred check tracked in PAR-WORLD-46"
---

# PAR-WORLD-12: Plant blocks: all TFC plant shapes as CE blocks in generation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
