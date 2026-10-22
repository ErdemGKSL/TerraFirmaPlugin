---
id: PAR-DATA-13
title: "TFC pin drift check in CI"
area: DATA
status: open
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
tests:
  - "TfcPinDriftTest"
done_criteria:
  - "CI job green"
---

# PAR-DATA-13: TFC pin drift check in CI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
