---
id: PAR-WORLD-04
title: "TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel"
area: WORLD
status: open
wave: 17
deps: [PAR-INFRA-08]
size: M
aliases: ["M2-GEN-08 (block set)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/DirtBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/TFCRootedDirtBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/PathBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/soil/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/soil/**"
  - "content/generated/content/craftengine/tfp/configuration/soil/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/RockPalette.java"
ui: true
in_game_steps:
  - "Fresh world; tp to a plains biome"
  - "tfp debug block on grass, coarse dirt, gravel"
plan:
  - "Current: full-cube dirt/clay/sand per soil are generated; grass variants, coarse dirt, mud, peat, kaolin, rooted dirt, rock gravel are written as vanilla placeholders."
  - "Generate the missing soil blocks per soil variant with the visual allocator (grass uses repurposed vanilla grass_block states so the biome tint still applies; record which states are taken)."
  - "Map TfcTerrain's surface kinds to the CE ids in RockPalette (cached BlockData, no per-block lookups)."
tests:
  - "tools/datagen: counts per soil vs TFC registry"
  - "paper: palette refuse rule test"
done_criteria:
  - "A fresh world shows TFC grass/dirt/gravel textures (screenshot) with no vanilla placeholder in a 5x5 chunk scan (`tfp debug worldgen`)"
---

# PAR-WORLD-04: TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
