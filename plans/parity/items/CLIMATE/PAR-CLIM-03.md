---
id: PAR-CLIM-03
title: "Snow and ice accumulation: lazy-vs-stepped equivalence and server check"
area: CLIMATE
status: open
wave: 12
deps: [PAR-CLIM-02]
size: S
aliases: ["M2-WTH-02", "M2-WTH-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/weather/Snow*"
  - "core/src/test/java/dev/tfp/core/weather/Snow*"
  - "paper/src/main/java/dev/tfp/paper/weather/Snow*"
  - "paper/src/test/java/dev/tfp/paper/weather/Snow*"
  - "paper/src/main/java/dev/tfp/paper/weather/ChunkSnowTime.java"
ui: true
in_game_steps:
  - "tfp time add months <n> until the HUD date reads January (cold site, tfp debug chunk < -5 C)"
  - "tfp debug snowtime <cx> <cz> 1000"
  - "Unload/reload the chunk (tp away and back), screenshot the snow cover"
plan:
  - "Current: SnowRule, SnowCatchUp, SnowService and the catch-up on load are done; server check of accumulation passed once."
  - "Write the lazy-vs-stepped equivalence test over one climate sequence (stepped onTickChunk vs SnowCatchUp)."
  - "Server check: melting on a warm day, first-load cost of new chunks recorded in docs/perf."
  - "Close the rain and melt divergence rows in the ledger or update them."
tests:
  - "core: SnowLazyVsSteppedTest"
done_criteria:
  - "Equivalence test green; melt observed in-game; ledger rows updated"
---

# PAR-CLIM-03: Snow and ice accumulation: lazy-vs-stepped equivalence and server check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
