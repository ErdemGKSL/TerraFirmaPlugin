---
id: PAR-WORLD-03
title: "Worldgen performance: JMH chunk budget, vanilla baseline and exposed-gem decision"
area: WORLD
status: open
wave: 17
deps: [PAR-WORLD-02, PAR-WORLD-17]
size: M
aliases: ["M2-GEN-15", "M2-GEN-07 (JMH)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
files_owned:
  - "worldgen/src/jmh/**"
  - "docs/perf/worldgen-*.md"
ui: false
in_game_steps: []
plan:
  - "JMH of TfcTerrain.fill at 1/4/8 threads; pre-generate the same 10k chunks on vanilla and TFC worlds under spark and compare chunks/s."
  - "Count exposed gem faces per chunk to confirm or change the level-2 gem overlay; write the decision in ARCHITECTURE.md."
  - "Ore overlay (2026-10-22): the exposed-gem count is superseded by PAR-WORLD-17 (all ores are overlays, census and entity budget); write only the worldgen cost here and link the S1 verdict for the overlay decision."
tests:
  - "JMH results committed"
done_criteria:
  - "docs/perf/worldgen-chunk-cost.md has the vanilla baseline and the gem decision"
---

# PAR-WORLD-03: Worldgen performance: JMH chunk budget, vanilla baseline and exposed-gem decision

> see_not_parity: NP-VIS-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
