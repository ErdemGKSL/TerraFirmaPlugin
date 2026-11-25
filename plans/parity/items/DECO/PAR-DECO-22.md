---
id: PAR-DECO-22
title: "Rock walls, aqueducts and rope anchors (follow-up of PAR-DECO-06)"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-06]
size: M
aliases: ["split from PAR-DECO-06: walls need a wall behaviour (CraftEngine has none) and a vanilla wall twin, aqueducts need a connection runner, rope anchors need the rope system"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/AqueductBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockRopeAnchorBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/RockWalls.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/RockWalls*"
  - "content/generated/content/craftengine/tfp/configuration/rockwall/**"
ui: true
in_game_steps:
  - "place tfp:rock/cobble/granite_wall next to a fence and a wall of another rock; screenshot the connections"
plan:
  - "Walls: 7 kinds x 21 rocks = 147 blocks (strength 1.5 / 10, pickaxe, stone sounds; MossGrowing / MossSpreading walls of the cobble and bricks kinds hook into PAR-GATHER-06). CraftEngine has no wall behaviour: write a `tfp:wall` behaviour (up, north, east, south, west as none / low / tall, waterlogged: 324 states) or the multipart as item displays on one shared vanilla wall twin; needs TfcShapes rows of the vanilla WallBlock outlines and dump rows of the twin (the walls of PAR-DECO-18 and PAR-DECO-19 share it)."
  - "Aqueducts: 21 blocks (`tfc:rock/aqueduct/<rock>`, water and lava fluid property, connections to the neighbours, strength like the bricks): decide async, apply sync through the PAR-INFRA-10 neighbour runner."
  - "Rope anchors: 21 no-item blocks that the rock spike places for the rope system (TFC `RockRopeAnchorBlock`); depends on the rope blocks of the PAR-PHYS items."
tests:
  - "tools/datagen: RockWallsTest"
done_criteria:
  - "147 walls, 21 aqueducts and 21 rope anchors are CE blocks with TFC strength, models, drops and names; carriers pinned"
---

# PAR-DECO-22: Rock walls, aqueducts and rope anchors (follow-up of PAR-DECO-06)

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
