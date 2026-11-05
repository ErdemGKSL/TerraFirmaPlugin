---
id: PAR-INFRA-28
title: "Raw and hardened rock loot through the Paper runtime: delegate the 42 tfc:is_isolated tables, remove tfp:loot_table/rock_to_loose"
area: INFRA
status: open
wave: 5
deps: [PAR-INFRA-21]
size: S
aliases: []
tfc_refs:
  - "src/main/resources/data/tfc/loot_table/blocks/rock/raw"
  - "src/main/resources/data/tfc/loot_table/blocks/rock/hardened"
  - "src/main/java/net/dries007/tfc/util/loot/IsIsolatedCondition.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/LootShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/ce/templates/loot_tables.yml"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/LootTableExportTest.java"
  - "itest/src/main/java/dev/tfp/itest/cases/LootCase.java"
ui: false
in_game_steps: []
plan:
  - "Current (PAR-INFRA-21): `LootTableExport.delegateBlock` ships a block's TFC table for the Paper runtime and `AssetPipeline.loot` gives such a block no CE `loot:` whenever the table matches no `tfp:loot_table/*` template; `LootTableExport.run` checks the exported set (no `tfc:` left, only TFC's own `tfp:` types, no CE `loot:` on a delegated block). Raw and hardened rock (42 blocks: `report.divergence(\"tfc:is_isolated\")`) still match `LootShapes.rockToLoose` and keep the `tfp:loot_table/rock_to_loose` template, because `DatagenTest.emitsCraftEngineEntriesForCubeBlocksOnly` (not owned by PAR-INFRA-21) asserts that template on the fixture's raw granite. Both deps of the switch are done: PAR-INFRA-22 (the CE correct-tool gate on delegated breaks) and PAR-INFRA-27 (TFC metal pickaxes are correct tools on rock)."
  - "Drop the `rockToLoose` branch of `AssetPipeline.loot` (the tables then fall through to `delegateBlock`), delete `LootShapes.rockToLoose` and the `tfp:loot_table/rock_to_loose` template. `delegateBlock` already registers the loose rocks the tables name as CE items and skips the raw block itself (and any other emitted block). Keep a `report.divergence(\"tfc:is_isolated\", id)` per delegated rock while no caller sets `isolated` (the `WorldTracker` port, PAR-PHYS-02)."
  - "Update `DatagenTest` (raw granite has no `loot:`, its table is `data/tfp/loot_table/blocks/rock/raw/granite.json`, `tfp:rock/loose/granite` is still a CE item), `LootTableExportTest.aBlockWithNoTemplateShapeIsDelegated` (the fixture's raw granite is delegated too), and the pinned-checkout counts (42 block tables, 83 entity tables). Rerun datagen (`generateContent`, `verifyContent`)."
  - "Self-test: `LootCase` then runs its raw granite lines (`tfp:rock/raw/granite` by hand: nothing; with `tfp:metal/pickaxe/bronze`: 1-4 loose rocks; in an explosion: 1-4 loose rocks and nothing from CraftEngine or the carrier). Run `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper` twice (the first start installs the pack)."
  - "Ledger: the raw rock `is_isolated` divergence row (the table is delegated; only the `WorldTracker` caller is missing) and the loot delegation row."
tests:
  - "datagen: DatagenTest and LootTableExportTest as above; PinnedCheckoutTest stays green"
  - "itest: the loot case's raw granite lines on Paper 1.21.11"
done_criteria:
  - "Raw granite drops nothing by hand and 1-4 loose rocks with a bronze pickaxe and in an explosion, through the Paper runtime, in the self-test"
---

# PAR-INFRA-28: Raw and hardened rock loot through the Paper runtime: delegate the 42 tfc:is_isolated tables, remove tfp:loot_table/rock_to_loose

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
