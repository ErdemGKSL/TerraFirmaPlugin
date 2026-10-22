---
id: PAR-REL-09
title: "Release packaging"
area: REL
status: blocked
wave: 56
deps: [PAR-REL-08]
size: M
aliases: ["M6-REL-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - "docs/release/**"
  - "README.md"
ui: false
in_game_steps: []
plan:
  - "BLOCKED on the final product name/namespace (PRD open question). Then: NOTICE review, EUPL vs GPL dependency review, server-owner docs, listings, 1.0.0."
tests:
  - "checkNotice"
done_criteria:
  - "Released"
---

# PAR-REL-09: Release packaging

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
