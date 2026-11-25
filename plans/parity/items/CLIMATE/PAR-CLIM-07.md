---
id: PAR-CLIM-07
title: "Weather command and TFC weather state (`/tfp weather`)"
area: CLIMATE
status: done
wave: 33
deps: [PAR-DATA-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/commands/WeatherCommand.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/command/weather/**"
  - "paper/src/test/java/dev/tfp/paper/command/weather/**"
ui: true
in_game_steps:
  - "tfp weather rain"
  - "tfp weather clear"
plan:
  - "Current: core/command/WeatherCommand parses the four literals."
  - "Bind it under `/tfp weather clear|rain|thunder|snow` with the TFC `/time` hints and the world tracker flag."
  - "Done: already bound by PAR-DATA-01 (DataCommandTree); TFC has only enable/disable/clear/rain, so no thunder/snow literals (ledger Divergences). Added command/weather/WeatherCommandTextsTest (2), ledger rows. Pending in-game check (container cannot run client)."
tests:
  - "paper: BootstrapTest tree + MockBukkit execution"
done_criteria:
  - "`/tfp weather rain` replies as TFC and changes the weather"
---

# PAR-CLIM-07: Weather command and TFC weather state (`/tfp weather`)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
