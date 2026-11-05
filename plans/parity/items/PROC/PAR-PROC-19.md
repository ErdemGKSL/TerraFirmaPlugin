---
id: PAR-PROC-19
title: "Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "Generate every remaining plain TFC item (graded ore items, small ores, gems cut/uncut, powders, ore powders, fibres, cloths, papyrus/paper chain, glue, soot, mortar, sandpaper, straw, sticks bunch/bundle, blubber, wool/yarn, daub, handstone, spindle, brass mechanisms, jars and lids, spawn eggs) with names, models, item_size and item_heat data; gem saw tool."
  - "Scope notes: the graded and normal ore pieces already exist in drops.yml (asset pipeline); their stack size is PAR-PROC-21. Small ores are TFCBlocks groundcover block items (PAR-WORLD-09), not TFCItems. Lumber needs the wood paletted atlas (PAR-DECO-01). Basket is PAR-PROC-22. Item heat comes from TFC's item_heat data at runtime, not from the CE item. The in-game steps and the screenshot criterion moved to PAR-PROC-23 (PROTOCOL §6.5)."
  - "Done: datagen material/ (TfcItemRegistry reads all 1618 TFCItems ids; MaterialItems writes 175 CE items in configuration/material/*.yml with models, textures, names in assets/tfp_material/lang, item_size stack sizes, durabilities, gem saw at bronze tier; ItemCoverage writes reports/tfc-item-coverage.json: 887 generated, 731 owned by named parity items, 0 unowned, and fails datagen on an unowned id). Tests: tools/datagen material/MaterialItemsTest (10). Verification: deferred to PAR-PROC-23 (docs/verification/PAR-PROC-19/)."
tests:
  - "tools/datagen: every TFCItems registration has a CE item or a documented owner item"
done_criteria:
  - "Deferred check tracked in PAR-PROC-23 (`ce item give` works for a sampled 20); coverage report shows 0 unowned TFC items"
---

# PAR-PROC-19: Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
