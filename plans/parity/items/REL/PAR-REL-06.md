---
id: PAR-REL-06
title: "Husbandry run self-test and 200-animal load test"
area: REL
status: open
wave: 53
deps: [PAR-FAUNA-08, PAR-FAUNA-17, PAR-FAUNA-06]
size: M
aliases: ["M5 exit criteria"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/HusbandryRunCase.java"
  - "docs/perf/animals-200.md"
ui: false
in_game_steps: []
plan:
  - "Tame -> breed -> products -> slaughter scripted; spark profile with 200 animals."
tests:
  - "itest"
done_criteria:
  - "No region-thread spikes above budget"
---

# PAR-REL-06: Husbandry run self-test and 200-animal load test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
