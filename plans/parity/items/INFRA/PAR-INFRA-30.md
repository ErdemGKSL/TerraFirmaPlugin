---
id: PAR-INFRA-30
title: "TFC's 41 overrides of vanilla loot tables (glass, panes, snow, chests, zombie, drowned) ship in a datapack"
area: INFRA
status: done
wave: 5
deps: [PAR-INFRA-21]
size: S
aliases: ["follow-up of PAR-INFRA-21 (vanilla loot overrides not exported)"]
tfc_refs:
  - "src/main/resources/data/minecraft/loot_table/blocks"
  - "src/generated/resources/data/minecraft/loot_table/entities"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/VanillaLootOverrides.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/VanillaLootOverridesTest.java"
  - "itest/src/main/java/dev/tfp/itest/cases/VanillaLootCase.java"
ui: false
in_game_steps: []
plan:
  - "Current (PAR-INFRA-21): `LootTableExport` reads only `data/tfc/loot_table`, so TFC's 41 replacements of vanilla tables are not shipped and vanilla blocks and mobs drop vanilla loot. TFC @ c3b4d9536 has 39 block tables in `src/main/resources/data/minecraft/loot_table/blocks/` (glass, the 16 stained glasses, tinted glass, glass pane and the 16 stained panes drop themselves without silk touch; `snow` and `snow_block` drop only with silk touch, which matters because `SnowService` places vanilla snow layers; `chest` and `trapped_chest` drop `tfc:wood/chest/oak` with `copy_components` of `minecraft:custom_name`) and 2 entity tables in `src/generated/resources/data/minecraft/loot_table/entities/` (`zombie` and `drowned`: 0-2 rotten flesh only, no looting, no rare iron/carrot/potato)."
  - "Add `VanillaLootOverrides` (one `run` line in Datagen.generate): copy the 41 tables with the usual tfc -> tfp rename into a datapack under `data/minecraft/loot_table/**`, so the server's own loot replaces vanilla's under the same ids. Decide where they go: the bootstrap datapack (`TfpBootstrap.DATAPACK_RESOURCE`) or a datagen-written pack beside the seasonal one; whichever needs a file outside this item's files_owned becomes a hub line or a follow-up (PROTOCOL §3)."
  - "Chest and trapped chest name a CE item (`tfp:wood/chest/oak`), which a vanilla table cannot drop. Either ship them through the Paper loot runtime (a vanilla-block break hook over `LootService` that drops the CE item and copies the custom name), or leave the vanilla chest drop and record it. Record the choice in the ledger."
  - "Fail datagen when a shipped `minecraft:` table still names a `tfc:`/`tfp:` id that is not a vanilla item (other than through the chest decision above), and check the count against the pinned checkout (39 blocks, 2 entities)."
  - "Ledger: replace the PAR-INFRA-21 divergence row 'TFC's overrides of vanilla loot tables' with a feature row, keeping a divergence only for what stays different."
  - "Done: datagen `VanillaLootOverrides` (one run line in Datagen) ships 37 block + 2 entity minecraft: tables (renamed) into the bootstrap datapack tfp_seasonal_biomes (content/datapack/tfp_seasonal_biomes/data/minecraft/loot_table/), the one generated pack TfpBootstrap registers, so no new registration was needed. chest and trapped_chest are skipped (they name tfp:wood/chest/oak and tfp:wood/trapped_chest/oak) and keep the vanilla drop: ledger divergence, revisit when TFC chests are CE items. Datagen fails on a shipped table naming a tfc:/tfp: id or of the wrong type, and on a full checkout whose counts are not 39/2. Tests: VanillaLootOverridesTest (4), itest case loot.vanilla_overrides (glass and pane by hand, snow and snow block with a shovel, snow with silk touch, zombie and drowned 64 rolls each), passed on Paper 1.21.11 self-test."
tests:
  - "datagen: VanillaLootOverridesTest: fixture and pinned checkout give 39 block and 2 entity minecraft: tables, renamed, no leftover tfc:"
  - "itest: a glass block broken by hand drops glass; a snow layer broken with a shovel drops nothing; a zombie table rolls only rotten flesh"
done_criteria:
  - "Glass and panes drop themselves without silk touch, snow and snow blocks drop only with silk touch, and zombies and drowned drop only 0-2 rotten flesh, on Paper 1.21.11 in the self-test; the chest decision is recorded in the ledger"
---

# PAR-INFRA-30: TFC's 41 overrides of vanilla loot tables (glass, panes, snow, chests, zombie, drowned) ship in a datapack

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
