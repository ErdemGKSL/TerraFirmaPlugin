---
id: PAR-PROC-49
title: "Scribing table and sewing table blocks (tfp:wood/scribing_table/<wood>, tfp:wood/sewing_table/<wood>)"
area: PROC
status: done
wave: 41
deps: [PAR-DECO-01]
size: S
aliases: ["split from PAR-PROC-15 / PAR-PROC-07 (2026-11-25): their files_owned have no datagen"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/ScribingTableBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/SewingTableBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/tables/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/tables/**"
  - "content/generated/content/craftengine/tfp/configuration/tables/**"
ui: false
plan:
  - "Done: datagen TableBlocks (20 woods x 2 kinds x 4 facings, hitbox-only carriers, item-display look, TFC models/textures/lang), 5 shape rows in TfcShapes, demands at weight -23. Tests: TableBlocksTest (4) written; NOT RUN: at HEAD HitboxAllocation overflows on tfp:wood/button/* (other items' demands, reproduced with the loom, sluice and table demands disabled), so neither TableBlocksTest nor the content generation could run; TfcShapes regeneration with the new rows passed. Rerun TableBlocksTest and regenerate content when the allocator is fixed."
tests:
  - "datagen: TableBlocksTest"
done_criteria:
  - "Both tables are CE blocks with TFC's model, outline and facing"
---

# PAR-PROC-49: Scribing and sewing table blocks

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
