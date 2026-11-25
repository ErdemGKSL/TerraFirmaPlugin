---
id: PAR-DECO-19
title: "Alabaster and mud brick stairs, slabs and walls; wattle and daub"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-07, PAR-DECO-06]
size: L
aliases: ["split from PAR-DECO-07: shapes need vanilla twin states (see PAR-DECO-18); wattle is a multipart non-cube block with add-sticks/daub/dye interactions"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/WattleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/StainedWattleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/building/**"
  - "content/generated/content/craftengine/tfp/configuration/building/**"
  - "paper/src/main/java/dev/tfp/paper/wattle/**"
ui: true
in_game_steps:
  - "place tfp:wattle; add 4 sticks, daub, dye; screenshot each step"
plan:
  - "Alabaster bricks and polished (16 colours x slab, stairs, wall) and 8 soils of mud bricks x slab, stairs, wall: reuse the shape strategy of DECO-06/DECO-18 (twin pool)."
  - "Wattle and 16 stained/unstained wattle: WattleBlock multipart (top, bottom, left, right, fence, woven, sticks), add sticks/daub/dye in place with connection updates via the neighbour runner."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Shape blocks emitted with carriers; wattle building steps work in-game"
---

# PAR-DECO-19: Alabaster and mud brick stairs, slabs and walls; wattle and daub

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
