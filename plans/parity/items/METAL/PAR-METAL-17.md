---
id: PAR-METAL-17
title: "Metal weathering of copper-family blocks"
area: METAL
status: open
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
  - "place tfp:metal/block/copper"
  - "tfp time add years 2"
  - "reload chunk; screenshot"
plan:
  - "Catch-up kind: WeatheringRules.catchUp over elapsed calendar ticks with the neighbour/climate sample; waxing/scraping as TFC."
tests:
  - "core: WeatheringLazyVsSteppedTest exists"
done_criteria:
  - "A copper block weathers after time add (in-game)"
---

# PAR-METAL-17: Metal weathering of copper-family blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
