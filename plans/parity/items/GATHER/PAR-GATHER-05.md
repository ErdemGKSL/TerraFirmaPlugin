---
id: PAR-GATHER-05
title: "Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning"
area: GATHER
status: done
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
  - "Done: core/soil (SoilTable: hoe to farmland, rooted dirt to dirt, shovel to path, 100 mB water to mud; GrassRules: canBeGrass/canPropagate/spread offsets/slime-chunk and rising-rock gates), paper/soil (SoilListener + SoilModule). Tests: SoilTableTest (7), GrassRulesTest (6), SoilListenerMockBukkitTest (7). The grass spread/death/rising-rock world sweep is PAR-GATHER-11; farmland and path CE blocks come with PAR-FARM-01 / PAR-WORLD-42 (the listener leaves the click alone while the target block is not loaded). Pending in-game check (container cannot run client)."
  - "Hoe on dirt/grass -> TFC farmland (enableFarmlandCreation); shovel -> path; water bottle/water on dirt -> mud (enableDirtToMudCreation); hoe on rooted dirt -> dirt; grass spreads/dies by light as TFC on a lazy sweep; grassSpawningRocksChance."
tests:
  - "paper: MockBukkit hoe/shovel conversions"
done_criteria:
  - "Hoeing TFC dirt yields TFC farmland in-game"
---

# PAR-GATHER-05: Soil interactions: hoeing, paths, mud, rooted dirt, grass spread and rock spawning

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
