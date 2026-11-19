---
id: PAR-DATA-13
title: "TFC pin drift check in CI"
area: DATA
status: done
wave: 39
deps: []
size: S
aliases: ["X-DATA-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - ".github/workflows/tfc-drift.yml"
  - "tools/datagen/src/main/java/dev/tfp/datagen/TfcPinDrift.java"
ui: false
in_game_steps: []
plan:
  - "Run :tools:datagen:tfcPinDrift in CI against a cached TFC clone; fail on unacknowledged drift; ported-header commit check."
  - "Done 2026-11-19: TfcPinDrift.headerDrift (ported headers must name a prefix of docs/porting/tfc-pin.txt) with TfcPinDriftTest (a stale header is flagged; the repository itself has none); .github/workflows/tfc-drift.yml fails on header drift and prints the upstream changes since the pin (the existing tfcPinDrift task) against a cached clone fetched by the pinned hash. The workflow itself has not run on GitHub from this container."
tests:
  - "TfcPinDriftTest"
done_criteria:
  - "CI job green"
---

# PAR-DATA-13: TFC pin drift check in CI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
