---
id: PAR-GATHER-05
title: "Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning"
area: GATHER
status: open
wave: 18
deps: [PAR-WORLD-04]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/DirtBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/HoeOverlayBlock.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/soil/**"
  - "core/src/test/java/dev/tfp/core/soil/**"
  - "core/src/test/resources/dev/tfp/core/soil/**"
  - "paper/src/main/java/dev/tfp/paper/soil/**"
  - "paper/src/test/java/dev/tfp/paper/soil/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:stone/hoe/sedimentary"
  - "use on grass"
  - "tfp debug block"
plan:
  - "Hoe on dirt/grass -> TFC farmland (enableFarmlandCreation); shovel -> path; water bottle/water on dirt -> mud (enableDirtToMudCreation); hoe on rooted dirt -> dirt; grass spreads/dies by light as TFC on a lazy sweep; grassSpawningRocksChance."
tests:
  - "paper: MockBukkit hoe/shovel conversions"
done_criteria:
  - "Hoeing TFC dirt yields TFC farmland in-game"
---

# PAR-GATHER-05: Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
