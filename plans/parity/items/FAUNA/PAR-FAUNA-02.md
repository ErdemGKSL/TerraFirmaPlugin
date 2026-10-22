---
id: PAR-FAUNA-02
title: "Entity models for Java-only TFC models via the rig generator"
area: FAUNA
status: open
wave: 39
deps: [PAR-FAUNA-01]
size: M
aliases: ["M5-ENT-02 (generators)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/entity"
files_owned:
  - "tools/models/tfpmodels/species/**"
  - "art/raw/entity-*/**"
ui: false
in_game_steps: []
plan:
  - "Species parameter files from each `createBodyLayer`; generate .bbmodel with the rig library; render through Blockbench headless; every animal has idle + walk."
tests:
  - "validate.py"
done_criteria:
  - "docs/art/model-coverage.md shows 60/60"
---

# PAR-FAUNA-02: Entity models for Java-only TFC models via the rig generator

> see_not_parity: NP-ENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
