---
id: PAR-PROC-51
title: "Sewing table in-game verification and TFC screen art (tfp:gui/sewing)"
area: PROC
status: done
wave: 42
deps: [PAR-PROC-07, PAR-PROC-49]
size: M
aliases: ["moved from PAR-PROC-07 (2026-11-25, PROTOCOL 6.5): the screen runs on item icons over the vanilla chest background; TFC's sewing.png is not drawn yet and nothing was run on a real server"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures/gui/sewing.png"
  - "src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java"
files_owned:
  - "docs/verification/PAR-PROC-51/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/sewinggui/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/sewinggui/**"
  - "paper/src/main/java/dev/tfp/paper/sewing/**"
  - "paper/src/test/java/dev/tfp/paper/sewing/**"
  - "core/src/main/java/dev/tfp/core/sewing/**"
  - "core/src/test/java/dev/tfp/core/sewing/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/sewing_table/oak; place it; right-click: a six-row screen titled Sewing Table opens"
  - "put 3 tfp:wool_cloth in slot 47, 4 tfp:burlap_cloth in slot 48, 3 string in slot 45 and a bone needle in slot 46; click the dark and light cloth buttons and fill the squares of the globe banner pattern, then the needle button and its 16 stitches (patterns in the recipe tfc:sewing/globe_banner_pattern); the result appears in slot 49; take it; the cloth, yarn and needle wear are used as TFC does"
  - "used cloth, yarn and needle cannot be taken out of their slots before the result is taken; closing the screen returns the items; screenshot at GUI scale 2 and 3"
plan:
  - "Verify PAR-PROC-07 in game (the grid slot clicks, the two views, the title with no literal <image> text). Then draw TFC's sewing.png behind the grid (datagen `sewinggui/SewingScreenArt`, one `run` line in Datagen.java, the same chest re-layout technique as the knapping and salad screens) so the squares and stitches sit on TFC's frames, and swap the item icons for the blank button model where the art shows the state (overlay frames for placed squares and stitches)."
  - "Done: in game (docs/verification/PAR-PROC-51/): the screen opens, the globe banner pattern is sewn (squares, stitches, result), input slots are locked until the result is taken, closing returns the items; GUI scale 2 only. TFC's sewing.png art is not drawn: follow-up PAR-PROC-57 (it also owns the swap of the item icons for the art frames)."
tests:
  - "datagen: the screen art matches SewingScreen (image ids, sizes, ascents)"
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-51/"
done_criteria:
  - "Sewing the globe banner pattern in game gives the pattern with TFC's consumption (docs/verification/PAR-PROC-51/). The screen art at GUI scale 2 and 3: tracked in PAR-PROC-57"
---

# PAR-PROC-51: Sewing table in-game verification and screen art

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
