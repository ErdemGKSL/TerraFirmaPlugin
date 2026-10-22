---
id: PAR-REL-04
title: "Stone-age run self-test"
area: REL
status: open
wave: 52
deps: [PAR-PROC-01, PAR-HEAT-06, PAR-POT-03, PAR-POT-05, PAR-GATHER-01, PAR-TOOL-01]
size: M
aliases: ["M3 exit criteria"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/StoneAgeRunCase.java"
ui: false
in_game_steps: []
plan:
  - "Scripted self-test: spawn -> loose rocks -> knapped tools -> fire -> cooked food -> pottery -> copper casting, on Paper and Folia."
tests:
  - "itest"
done_criteria:
  - "Green on Paper and Folia 1.21.11"
---

# PAR-REL-04: Stone-age run self-test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
