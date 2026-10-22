---
id: PAR-FAUNA-20
title: "Pluckable feathers, animal sounds"
area: FAUNA
status: open
wave: 45
deps: [PAR-FAUNA-05]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/Pluckable.java"
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/pluck/**"
  - "paper/src/test/java/dev/tfp/paper/pluck/**"
  - "paper/src/main/java/dev/tfp/paper/animal/sound/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn chicken adult; right-click empty hand"
plan:
  - "Plucking feathers from poultry (pluck damage type); carriers silent + TFC sounds played on our events (ambient on a throttled timer, hurt, death)."
tests:
  - "paper: pluck cooldown"
done_criteria:
  - "Plucking a chicken gives a feather"
---

# PAR-FAUNA-20: Pluckable feathers, animal sounds

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
