---
id: PAR-DECO-08
title: "Sand, sandstone families, hardened clay, halite, coal, calcite, misc natural blocks"
area: DECO
status: done
wave: 47
deps: [PAR-DECO-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/SandstoneBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/SandBlockType.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/sandstone/**"
  - "content/generated/content/craftengine/tfp/configuration/sandstone/**"
ui: true
in_game_steps:
  - "tfp debug showcase sandstone"
plan:
  - "Generate the 7 sand colours x (raw/smooth/cut) sandstone with stairs/slabs/walls and the remaining natural blocks."
  - "Done: 21 sandstone cubes (7 colours x raw/smooth/cut), hardened clay, halite, lignite, bituminous coal as CE blocks via tfp:settings/natural_stone (AssetPipeline.writeNaturalBlocks, configuration/natural/*.yml), TFC strengths, loot, names; NaturalBlocksTest. Sandstone stairs/slabs/walls and calcite moved to PAR-DECO-18 (vanilla twin states are too scarce for 63 more shape blocks next to DECO-06). Pending in-game check: showcase screenshot (no client run in this session)."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Showcase screenshot"
---

# PAR-DECO-08: Sand, sandstone families, hardened clay, halite, coal, calcite, misc natural blocks


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
