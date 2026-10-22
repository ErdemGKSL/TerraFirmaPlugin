---
id: PAR-REL-11
title: "Design docs and skills kept current"
area: REL
status: open
wave: 53
deps: []
size: S
aliases: ["X-DOC-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/TerraFirmaCraft.java"
files_owned:
  - "ARCHITECTURE.md"
  - "PRD.md"
  - ".claude/skills/**"
ui: false
in_game_steps: []
plan:
  - "Resolve UNVERIFIED notes in skills; sync ARCHITECTURE.md with every decision made by parity items (each item updates its own section; this item sweeps leftovers at milestones)."
tests:
  - "n/a"
done_criteria:
  - "No stale UNVERIFIED notes"
---

# PAR-REL-11: Design docs and skills kept current

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
