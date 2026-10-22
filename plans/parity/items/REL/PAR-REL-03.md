---
id: PAR-REL-03
title: "Dependency and version updates (MC, Paper, Folia, CE, BetterModel, BetterHud)"
area: REL
status: open
wave: 52
deps: []
size: S
aliases: ["X-COMPAT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - "content/compat.yml"
  - "docs/compat/versions.md"
ui: false
in_game_steps: []
plan:
  - "Re-check versions, update pins and the CI matrix, re-verify version-specific skill claims."
tests:
  - "CompatCheck tests"
done_criteria:
  - "versions.md dated this month"
---

# PAR-REL-03: Dependency and version updates (MC, Paper, Folia, CE, BetterModel, BetterHud)

> see_not_parity: NP-PLAT-01, NP-PLAT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
