---
id: PAR-PROC-19
title: "Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs"
area: PROC
status: open
wave: 5
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/common/items/Powder.java"
  - "src/main/java/net/dries007/tfc/common/items/GemSawItem.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/material/**"
  - "content/generated/content/craftengine/tfp/configuration/material/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ore/rich_hematite"
  - "ce item give Bot tfp:gem/ruby"
  - "ce item give Bot tfp:powder/salt"
plan:
  - "Generate every remaining plain TFC item (graded ore items, small ores, gems cut/uncut, powders, ore powders, fibres, cloths, papyrus/paper chain, glue, soot, mortar, sandpaper, straw, sticks bunch/bundle, blubber, wool/yarn, daub, handstone, spindle, brass mechanisms, jars and lids, spawn eggs) with names, models, item_size and item_heat data; gem saw tool."
tests:
  - "tools/datagen: every TFCItems registration has a CE item or a documented owner item"
done_criteria:
  - "`ce item give` works for a sampled 20; coverage report shows 0 unowned TFC items"
---

# PAR-PROC-19: Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
