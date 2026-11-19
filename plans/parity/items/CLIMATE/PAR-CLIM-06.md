---
id: PAR-CLIM-06
title: "Seasonal packet rewrite JMH budget and visual checklist"
area: CLIMATE
status: done
wave: 15
deps: [PAR-CLIM-05]
size: S
aliases: ["M2-SEA-01 (JMH)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/TFCColors.java"
files_owned:
  - "paper/src/jmh/java/dev/tfp/paper/climate/**"
  - "docs/perf/seasonal-*.md"
ui: false
in_game_steps: []
plan:
  - "Done: SeasonalRewriteBenchmark (paper/src/jmh, run by hand, see docs/perf/seasonal-rewrite.md): rewriteSection 0.55 us, 24-section chunk 13.9 us, 3,500-variant registry sync 278 us (HashMap stand-in); PERF-SEA-01 measured in budgets.md; ledger row and Divergences row (no paper jmh Gradle source set: paper/build.gradle.kts not owned). Pending in-game check (container cannot run client): visual checklist in docs/perf/seasonal-rewrite.md."
  - "Add a JMH benchmark of BiomePaletteRewrite per chunk section and of the 2,000-variant registry sync; record numbers in docs/perf/seasonal-rewrite.md and docs/perf/budgets.md."
tests:
  - "JMH run recorded"
done_criteria:
  - "Budget row in docs/perf/budgets.md is measured, not tbd"
---

# PAR-CLIM-06: Seasonal packet rewrite JMH budget and visual checklist


> see_not_parity: NP-CLIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
