---
id: PAR-CLIM-06
title: "Seasonal packet rewrite JMH budget and visual checklist"
area: CLIMATE
status: open
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
  - "Add a JMH benchmark of BiomePaletteRewrite per chunk section and of the 2,000-variant registry sync; record numbers in docs/perf/seasonal-rewrite.md and docs/perf/budgets.md."
tests:
  - "JMH run recorded"
done_criteria:
  - "Budget row in docs/perf/budgets.md is measured, not tbd"
---

# PAR-CLIM-06: Seasonal packet rewrite JMH budget and visual checklist

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
