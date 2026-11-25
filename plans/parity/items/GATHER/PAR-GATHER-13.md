---
id: PAR-GATHER-13
title: "Snow item over plants: provide the SnowPilePlacer service from the weather blocks"
area: GATHER
status: done
wave: 26
deps: [PAR-GATHER-07, PAR-CLIM-04, PAR-GATHER-12]
size: S
aliases: ["found while doing PAR-GATHER-07: ItemPlaceListener decides snow piles but the pile memory (what a pile covers) is private to paper/weather/WorldBlocks, so no placer is wired and the snow item stays vanilla; groundcover needs the CE blocks of PAR-GATHER-12 with a `fluid` (water) property"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/weather/SnowPileItemPlacer.java"
  - "paper/src/test/java/dev/tfp/paper/weather/SnowPileItemPlacer*"
  - "paper/src/main/java/dev/tfp/paper/weather/WeatherBlocksModule.java"
ui: true
in_game_steps:
  - "give Bot minecraft:snow 8; As Bot right-click a short grass plant and a TFC flower; screenshot: snow piles over the plants; break one: the plant comes back"
plan:
  - "Done: weather/SnowPileItemPlacer (placePile: the covered block and a pileable block above as pile memory through WorldBlocks.write; addLayer: layers + 1 up to 8) provided by WeatherBlocksModule as the SnowPilePlacer service; SnowPileItemPlacerMockBukkitTest (3); ledger row. Pending in-game check (container cannot run client): give Bot minecraft:snow 8, right-click a short grass plant and a flower, break the pile. Taken while PAR-GATHER-12 is blocked (it waits for PAR-WORLD-31): the placer does not need the CE groundcover blocks; the second plan step (groundcover `fluid` property for waterlogging) stays with PAR-GATHER-12 / PAR-WORLD-31."
  - "Implement dev.tfp.paper.itemplace.SnowPilePlacer on top of WorldBlocks (placePile: the SNOW_PILE write with the covered block and the block above as pile memory, as the weather SnowService does for PLACE_SNOW_PILE; addLayer: layers + 1) and provide it with context.services().provide(SnowPilePlacer.class, ...) from WeatherBlocksModule (append only)."
  - "Check that the PAR-GATHER-12 groundcover blocks have a `fluid` property (none/water) so ItemPlaceListener can waterlog them; else adjust FLUID_PROPERTY in ItemPlaceListener."
tests:
  - "paper: placePile on a short grass block stores the plant as pile memory and breaking the pile restores it"
done_criteria:
  - "The snow item over a plant places a snow pile that restores the plant (screenshots reviewed); tests green"
---

# PAR-GATHER-13: Snow item over plants: provide the SnowPilePlacer service

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
