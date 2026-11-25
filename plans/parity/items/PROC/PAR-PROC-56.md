---
id: PAR-PROC-56
title: "Scribing table: TFC screen art on the anvil screen"
area: PROC
status: open
wave: 44
deps: [PAR-PROC-50]
size: M
aliases: ["follow-up of PAR-PROC-50 (2026-11-25)"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/scribing_table.png"
  - "src/main/java/net/dries007/tfc/client/screen/ScribingTableScreen.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/scribinggui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/scribinggui/**"
  - "paper/src/main/java/dev/tfp/paper/scribing/**"
  - "paper/src/test/java/dev/tfp/paper/scribing/**"
  - "docs/verification/PAR-PROC-56/**"
ui: true
in_game_steps:
  - "Open the scribing table at GUI scale 2 and 3: the anvil screen shows TFC's scribing_table.png frame (ink icons, result frame) behind the vanilla slots and the text field"
plan:
  - "PAR-PROC-50 verified the behaviour on the vanilla anvil screen (title "Rename Items"). TFC draws textures/gui/scribing_table.png with its own layout (text field, ink level bar, result frame). Derive the art from that texture through the CE title glyph technique (docs of craftengine-gui-furniture: pixel-alignment math for textured container GUIs; anvil menus rewrite their title like chests), one `run` line in Datagen.java, no new art through imagegen. Check that the anvil text field and the slots still line up at GUI scale 2 and 3."
tests:
  - "datagen: the screen art matches the texture (image ids, sizes, ascents)"
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-56/"
done_criteria:
  - "The scribing table screen shows TFC's art at GUI scale 2 and 3 with the slots and text field aligned"
---

# PAR-PROC-56: Scribing table: TFC screen art on the anvil screen

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
