---
id: PAR-REL-08
title: "Full playthrough and visual QA"
area: REL
status: open
wave: 55
deps: [PAR-REL-07]
size: M
aliases: ["M6-QA-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "docs/qa/**"
ui: true
in_game_steps:
  - "Owner playthrough; screenshots per checklist row"
plan:
  - "Human playthrough stone -> steel -> husbandry on 1.21.11 and 26.x; visual checklist per family; Bedrock/Geyser decision applied."
tests:
  - "checklists"
done_criteria:
  - "All blockers fixed"
---

# PAR-REL-08: Full playthrough and visual QA

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
