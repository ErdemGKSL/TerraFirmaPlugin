---
id: PAR-PROC-07
title: "Sewing table GUI"
area: PROC
status: done
wave: 41
deps: [PAR-INFRA-03, PAR-DECO-01]
size: M
aliases: ["M3-PROC-02 (sewing)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/SewingTableBlock.java"
  - "src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java"
  - "src/generated/resources/data/tfc/recipe/sewing"
files_owned:
  - "core/src/main/java/dev/tfp/core/sewing/**"
  - "core/src/test/java/dev/tfp/core/sewing/**"
  - "core/src/test/resources/dev/tfp/core/sewing/**"
  - "paper/src/main/java/dev/tfp/paper/sewing/**"
  - "paper/src/test/java/dev/tfp/paper/sewing/**"
  - "core/src/main/java/dev/tfp/core/craft/Sewing*"
ui: false
in_game_steps:
  - "place a sewing table; right-click; screenshot GUI (moved to PAR-PROC-51)"
plan:
  - "Done: core `sewing/SewingScreen` (6-row chest: squares view 8 x 4 on slots 9y+x, stitches view 9 x 5 on slots 0..44, yarn 45, tool 46, inputs 47-48, result 49, buttons 50-53) and `SewingSession` (material select, press via SewingRules, update = recipe match + TFC's clear of the active material, mayTake, take = consumption + reset); paper `sewing/SewingMenu` (supply from the item slots, icons per view, sounds, result slot, needle wear, close returns items, stillValid), `SewingListener` (right-click on tfp:wood/sewing_table/*, result take, used cloth/yarn/needle stay), `SewingModule` (tags tfc:sewing_light_cloth, tfc:sewing_dark_cloth, tfc:sewing_needles, c:strings; the 25 tfc:sewing recipes from the recipe index). No custom art: vanilla chest background with item icons; art and the in-game check moved to PAR-PROC-51. Tests: core SewingScreenTest (6), SewingSessionTest (11); paper SewingMockBukkitTest (10). Divergences recorded in the ledger.",
  - "Not started (worker session 2026-11-25): needs a GUI design for 8x4 squares + 9x5 stitch points + 5 item slots + 5 buttons on 6 chest rows (two views, a title-glyph art per view: image-generation/art), the sewing recipe schema (tfc:sewing squares/stitches) and the table block from PAR-PROC-49. Do after PAR-PROC-49 content is generated."
  - "Textured GUI with the 12-pixel square/stitch grid as clickable button slots (approximation of TFC's free grid: divergence row), yarn/tool/inputs/result slots; SewingRules."
tests:
  - "paper: grid click mapping"
done_criteria:
  - "Sewing a recipe gives the result (paper SewingMockBukkitTest, all 25 recipes in core SewingSessionTest). Deferred check tracked in PAR-PROC-51 (in game, with the TFC screen art)"
---

# PAR-PROC-07: Sewing table GUI


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
