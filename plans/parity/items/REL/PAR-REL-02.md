---
id: PAR-REL-02
title: "Performance budgets in CI (JMH) and threading gate"
area: REL
status: done
wave: 52
deps: []
size: M
aliases: ["X-PERF-01", "X-THR-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - ".github/workflows/perf.yml"
  - ".github/workflows/check.yml"
  - "docs/perf/budgets.md"
  - "core/src/jmh/**"
  - ".github/pull_request_template.md"
ui: false
in_game_steps: []
plan:
  - "Done: JMH source set in core (Collapse, Rotation, Worldgen benchmarks), budgets.json + check-budgets.py, perf.yml, check.yml gate, PR template, budgets.md has no open rows. Also edited core/build.gradle.kts (JMH wiring, outside files_owned: no other way to add a source set). Verified locally with :core:jmh and :core:checkLicenseHeaders; CI itself not run (no PR)."
  - "JMH sources for the budgets marked tbd; CI fails on >20% regressions without a note; PlatformIsolationTest in CI; PR template checklist."
tests:
  - "CI runs"
done_criteria:
  - "budgets.md has no tbd rows"
---

# PAR-REL-02: Performance budgets in CI (JMH) and threading gate

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
