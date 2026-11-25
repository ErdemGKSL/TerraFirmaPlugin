---
id: PAR-PROC-57
title: "Sewing table: TFC screen art (sewing.png) behind the grid"
area: PROC
status: open
wave: 44
deps: [PAR-PROC-51]
size: M
aliases: ["follow-up of PAR-PROC-51 (2026-11-25)"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/sewing.png"
  - "src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/sewinggui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/sewinggui/**"
  - "paper/src/main/java/dev/tfp/paper/sewing/**"
  - "paper/src/test/java/dev/tfp/paper/sewing/**"
  - "core/src/main/java/dev/tfp/core/sewing/**"
  - "core/src/test/java/dev/tfp/core/sewing/**"
  - "docs/verification/PAR-PROC-57/**"
ui: true
in_game_steps:
  - "Open the sewing table at GUI scale 2 and 3: TFC's sewing.png is drawn behind the six-row grid; the squares and stitches sit on its frames; sew the globe banner pattern again"
plan:
  - "PAR-PROC-51 verified the sewing behaviour in game on the vanilla chest background with item icons (docs/verification/PAR-PROC-51/; grid slot centres: x 496 + 36c, y 190 + 36r at GUI scale 2). Draw TFC's sewing.png behind the grid (datagen `sewinggui/SewingScreenArt`, one `run` line in Datagen.java, the same chest re-layout technique as the knapping and salad screens, art derived from the TFC texture only), and swap the item icons for the blank button model where the art shows the state (overlay frames for placed squares and stitches)."
tests:
  - "datagen: the screen art matches SewingScreen (image ids, sizes, ascents)"
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-57/"
done_criteria:
  - "The sewing screen shows TFC's art at GUI scale 2 and 3 and the globe banner pattern can still be sewn"
---

# PAR-PROC-57: Sewing table: TFC screen art (sewing.png) behind the grid

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
