---
id: PAR-REL-07
title: "Performance audit with 50 bots"
area: REL
status: open
wave: 54
deps: [PAR-REL-04, PAR-REL-05, PAR-REL-06]
size: M
aliases: ["M6-PERF-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "docs/perf/audit-*.md"
ui: false
in_game_steps: []
plan:
  - "Scripted load (50 bots, pre-generated world, animals) on Paper and Folia, spark profiles, PRD budgets (<= 2 ms/tick share)."
tests:
  - "profiles committed"
done_criteria:
  - "Budgets met or documented"
---

# PAR-REL-07: Performance audit with 50 bots

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
