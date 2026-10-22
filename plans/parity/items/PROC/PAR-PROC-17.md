---
id: PAR-PROC-17
title: "Glass basin and hot poured glass; poured glass blocks and glass items"
area: PROC
status: open
wave: 31
deps: [PAR-PROC-16, PAR-INFRA-02]
size: M
aliases: ["M6-GLASS-01 (basin)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GlassBasinBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/HotPouredGlassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/PouredGlassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/GlassBasinBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/HotPouredGlassBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/glassblock/**"
  - "core/src/test/java/dev/tfp/core/glassblock/**"
  - "core/src/test/resources/dev/tfp/core/glassblock/**"
  - "paper/src/main/java/dev/tfp/paper/glassblock/**"
  - "paper/src/test/java/dev/tfp/paper/glassblock/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/glassblock/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/glassblock/**"
  - "content/generated/content/craftengine/tfp/configuration/glassblock/**"
ui: true
in_game_steps:
  - "pour glass on a table; wait 5 s; screenshot"
plan:
  - "Pouring onto a table (brass/iron) makes hot poured glass that settles in 100 ticks (lazy) into poured glass (16 colours); basins for glass panes; generate all glass items (batches, bottles, lens, lamp glass)."
tests:
  - "paper: settle catch-up"
done_criteria:
  - "Poured glass placed in-game"
---

# PAR-PROC-17: Glass basin and hot poured glass; poured glass blocks and glass items

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
