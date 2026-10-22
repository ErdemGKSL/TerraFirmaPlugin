---
id: PAR-INFRA-21
title: "Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime"
area: INFRA
status: open
wave: 4
deps: [PAR-INFRA-09]
size: M
aliases: []
tfc_refs:
  - "src/main/resources/data/tfc/loot_table"
  - "src/generated/resources/data/tfc/loot_table"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/LootTableExport.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/LootTableExportTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/LootShapes.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/ce/templates/loot_tables.yml"
  - "itest/src/main/java/dev/tfp/itest/cases/LootCase.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-09 added `paper/loot` (LootTableParser, LootEvaluator, LootService, LootBreakListener, LootModule). The runtime evaluates every TFC table (all 4,586 parse) and drops loot for any CE block whose table is at `content/data/<ns>/loot_table/blocks/<path>.json` in the jar, but datagen ships no tables, so no block uses it; CE blocks keep the `tfp:loot_table/*` template shapes (AssetPipeline.loot via LootShapes)."
  - "Add `LootTableExport` (one `run` line in Datagen.generate): copy TFC's block and entity loot tables, ids renamed tfc -> tfp exactly as the other data (item names, block names in block_state_property, `tfc:` condition/function/number types become `tfp:`, which `LootTableParser` reads as TFC's), into `content/generated/content/data/tfp/loot_table/`. Ship only the tables of blocks we generate and of entities we have, and only those whose shape the CE templates cannot express (any TFC condition or function, `block_state_property` counts, `copy_components`, `crop_yield_uniform`, explosion decay)."
  - "For those blocks, `AssetPipeline.loot` writes no CE `loot:` (or an empty table) so the runtime is the only drop source; blocks whose table matches a template keep plain CE loot. Remove `tfp:loot_table/rock_to_loose` once raw rock is delegated and update the ledger rows (raw rock `is_isolated`, loot delegation)."
  - "Self-test case `loot` (SelfTestSuite hub line): place `tfp:rock/loose/granite[count=3]` and `tfp:wood/leaves/oak`, break each as a survival player (empty hand; shears), assert the dropped item entities match the TFC tables (3 loose rocks; leaves with shears, no leaves by hand)."
tests:
  - "datagen: export count and renamed ids against the real checkout; every exported table parses with `LootTableParser`"
  - "itest: loot case on Paper 1.21.11"
done_criteria:
  - "A loose rock and a leaves block drop per their TFC tables in a self-test"
---

# PAR-INFRA-21: Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
