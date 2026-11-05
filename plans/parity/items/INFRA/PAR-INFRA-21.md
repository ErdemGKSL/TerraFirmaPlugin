---
id: PAR-INFRA-21
title: "Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime"
area: INFRA
status: done
wave: 4
deps: [PAR-INFRA-09, PAR-INFRA-22, PAR-INFRA-27]
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
  - "Before delegating: PAR-INFRA-22 must have wired the correct-tool check (else rock, ore and metal blocks drop their table by hand), and PAR-INFRA-27 must have made the TFC metal pickaxes count for the rock template's `correct_tools` (else rock, ore and metal blocks drop nothing with any TFC pickaxe). Do not delegate rock, ore or metal blocks before both are done. In the self-test, also break a delegated raw rock by hand (no drop) and with `tfp:metal/pickaxe/bronze` (its TFC table). Decide the startup window (tables load async; a delegated block drops nothing until they are loaded): hold delegated breaks until loaded, or keep the recorded divergence. Check that a delegated CE block in an explosion does not also drop its carrier's vanilla item or CE loot (no double drops)."
  - "Done: `LootTableExport` (Datagen run line after the block generators) ships the renamed TFC tables of all 83 spawned TFC entities (`CarrierTable`) and of every generated block whose table has no template shape (`delegateBlock`, called by `AssetPipeline.loot` instead of failing; such a block gets no CE `loot:` and the items its table names become CE items); `run` fails datagen on a leftover `tfc:` id, an unknown `tfp:` type or a delegated block that still has a CE `loot:` (no double drops). No generated block is delegated yet: every one matches a template. Raw/hardened rock (42 `tfc:is_isolated` tables) are not switched here because that changes `DatagenTest` (not in files_owned): moved to PAR-INFRA-28, which also removes `tfp:loot_table/rock_to_loose`. Loose rocks and leaves are not CE blocks yet (PAR-WORLD-09/10): the done criterion is deferred to PAR-INFRA-29 (PROTOCOL §6.5). Startup window: kept the recorded divergence (only the shipped tables load, the case waits for them). Self-test case `loot` (SelfTestSuite; `SelfTestSuiteTest` count 19 -> 20, the hub line's companion): tables load with no parse error and entity tables are present; per delegated block the TFC drops by hand/shears/bronze pickaxe and one real explosion with nothing from CE or the carrier on top; lines for blocks not delegated yet name their item. Tests: datagen `LootTableExportTest` (6); itest `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper`: loot PASSED (83 tables loaded, no errors; block lines pending), 22 cases, 0 failing."
tests:
  - "datagen: export count and renamed ids against the real checkout; every exported table parses with `LootTableParser`"
  - "itest: loot case on Paper 1.21.11"
done_criteria:
  - "Deferred check tracked in PAR-INFRA-29"
---

# PAR-INFRA-21: Loot delegation: datagen ships TFC loot tables, CE blocks that need TFC conditions drop through the Paper loot runtime

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
