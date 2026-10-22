---
id: PAR-DATA-03
title: "Advancement criteria wiring across features"
area: DATA
status: open
wave: 35
deps: [PAR-DATA-02]
size: M
aliases: []
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/advancement/criteria/**"
  - "docs/advancements/criteria-map.md"
ui: false
in_game_steps: []
plan:
  - "Map every TFC criterion to a feature event (one table in docs), fire it from module event listeners registered here (listening to our own feature events, not editing feature packages)."
tests:
  - "paper: every criterion id has a mapping or a documented 'not reachable'"
done_criteria:
  - "TFC's tree is obtainable (self-test grants via scripted events)"
---

# PAR-DATA-03: Advancement criteria wiring across features

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
