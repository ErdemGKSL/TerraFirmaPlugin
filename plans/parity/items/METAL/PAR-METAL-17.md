---
id: PAR-METAL-17
title: "Metal weathering of copper-family blocks"
area: METAL
status: done
wave: 48
deps: [PAR-INFRA-11, PAR-DECO-09]
size: S
aliases: ["M4-WEATH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/WeatheringBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/WeatheringGrateBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/weathering/**"
  - "core/src/test/java/dev/tfp/core/weathering/**"
  - "core/src/test/resources/dev/tfp/core/weathering/**"
  - "paper/src/main/java/dev/tfp/paper/weathering/**"
  - "paper/src/test/java/dev/tfp/paper/weathering/**"
ui: true
in_game_steps:
  - "place tfp:metal/block/copper in front of Bot (as a player, or look at it and run /tfp debug weathering to list it)"
  - "tfp time add years 2"
  - "reload chunk (or wait a minute for the sweep; /tfp debug weathering gives the chunk a pass now); screenshot"
plan:
  - "Done: core WeatheringExposure (drip, climate, neighbour terms in TFC float arithmetic) beside the existing WeatheringRules; paper weathering module (WeatheringService: snapshot on the region, decide on the CPU pool, apply in hops of 16 after re-validation; WeatheringChunk index and stamp; WeatheringIds for the 63 blocks), /tfp debug weathering. Tests: core WeatheringExposureTest 5, paper WeatheringIdsTest 3, WeatheringMockBukkitTest 11, BootstrapTest debug list. Pending in-game check (container cannot run client); in_game_steps kept."
  - "Catch-up kind: WeatheringRules.catchUp over elapsed calendar ticks with the neighbour/climate sample; waxing/scraping as TFC."
tests:
  - "core: WeatheringLazyVsSteppedTest exists"
done_criteria:
  - "A copper block weathers after time add (in-game)"
---

# PAR-METAL-17: Metal weathering of copper-family blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
