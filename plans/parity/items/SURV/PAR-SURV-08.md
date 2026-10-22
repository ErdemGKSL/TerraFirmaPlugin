---
id: PAR-SURV-08
title: "Alcohol and intoxication"
area: SURV
status: open
wave: 9
deps: [PAR-SURV-04, PAR-FLUID-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/fluids/Alcohol.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/generated/resources/data/tfc/tfc/drinkable"
files_owned:
  - "core/src/main/java/dev/tfp/core/intox/**"
  - "core/src/test/java/dev/tfp/core/intox/**"
  - "core/src/test/resources/dev/tfp/core/intox/**"
  - "paper/src/main/java/dev/tfp/paper/intox/**"
  - "paper/src/test/java/dev/tfp/paper/intox/**"
ui: true
in_game_steps:
  - "tfp debug fluid Bot jug tfp:beer 100 (debug from PAR-FLUID-01)"
  - "drink"
  - "screenshot effect"
plan:
  - "Drinking alcohol (from a jug) adds intoxication ticks capped at maxIntoxicationTicks; nausea/effects as the drinkable defines; lazy decay from a timestamp in PlayerData."
tests:
  - "core: golden for intoxication add/decay"
done_criteria:
  - "Drinking beer from a jug shows nausea"
---

# PAR-SURV-08: Alcohol and intoxication

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
