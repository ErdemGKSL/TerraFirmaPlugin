---
id: PAR-DATA-09
title: "Particles: action-driven TFC particles with vanilla equivalents"
area: DATA
status: open
wave: 38
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/particle/TFCParticles.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/particle/**"
  - "docs/particles.md"
ui: false
in_game_steps: []
plan:
  - "Table of TFC particles -> vanilla particle + options, only on explicit actions (locked decision); ambient ones (butterflies, moths, wind, leaves) dropped as divergence."
tests:
  - "paper: facade test"
done_criteria:
  - "docs/particles.md complete"
---

# PAR-DATA-09: Particles: action-driven TFC particles with vanilla equivalents

> see_not_parity: NP-PART-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
