---
id: PAR-DECO-05
title: "Tool racks, shelves, bookshelves, lecterns"
area: DECO
status: open
wave: 46
deps: [PAR-DECO-01, PAR-INFRA-02]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ShelfBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/BookshelfBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLecternBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ToolRackBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rack/**"
  - "core/src/test/java/dev/tfp/core/rack/**"
  - "core/src/test/resources/dev/tfp/core/rack/**"
  - "paper/src/main/java/dev/tfp/paper/rack/**"
  - "paper/src/test/java/dev/tfp/paper/rack/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rack/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rack/**"
  - "content/generated/content/craftengine/tfp/configuration/rack/**"
ui: true
in_game_steps:
  - "place tfp:wood/tool_rack/oak; right-click with tools; screenshot"
plan:
  - "Tool rack (4 tools shown via item displays), shelves (items shown), chiseled-bookshelf-like TFC bookshelves, lecterns on vanilla carrier."
tests:
  - "paper: put/take"
done_criteria:
  - "Four tools shown on a rack"
---

# PAR-DECO-05: Tool racks, shelves, bookshelves, lecterns

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
