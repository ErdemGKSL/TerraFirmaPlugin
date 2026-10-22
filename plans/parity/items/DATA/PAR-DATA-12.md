---
id: PAR-DATA-12
title: "Block info readout (Jade-like tooltips) on the actionbar"
area: DATA
status: open
wave: 39
deps: [PAR-INFRA-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tooltip/BlockEntityTooltips.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/EntityTooltips.java"
  - "src/main/java/net/dries007/tfc/compat/jade"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/lookinfo/**"
  - "paper/src/test/java/dev/tfp/paper/lookinfo/**"
ui: true
in_game_steps:
  - "sneak and look at a crop; screenshot actionbar"
plan:
  - "Client mod overlays are impossible; closest: when sneaking and looking at a TFC device/crop/animal, show its BlockEntityTooltips text on the actionbar (throttled, 4/s)."
tests:
  - "paper: text per kind"
done_criteria:
  - "Sneak-look at a crop shows growth %"
---

# PAR-DATA-12: Block info readout (Jade-like tooltips) on the actionbar

> see_not_parity: NP-UI-01, NP-CLIENT-03, NP-MOD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
