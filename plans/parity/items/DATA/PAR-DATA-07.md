---
id: PAR-DATA-07
title: "Damage types and mob effects"
area: DATA
status: open
wave: 37
deps: []
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCDamageTypes.java"
  - "src/main/java/net/dries007/tfc/common/effect/TFCEffects.java"
  - "src/generated/resources/data/tfc/damage_type"
files_owned:
  - "paper/src/main/resources/datapack/data/tfp/damage_type/**"
  - "paper/src/main/java/dev/tfp/paper/effect/**"
  - "paper/src/test/java/dev/tfp/paper/effect/**"
ui: true
in_game_steps:
  - "tfp debug thirst Bot 0; wait until death; screenshot chat"
plan:
  - "Ship TFC damage types in our datapack (death messages); effects: vanilla carriers (slowness/mining fatigue) with our icons impossible -> divergence; ink/glow ink from squid hits."
tests:
  - "paper: damage type registry present"
done_criteria:
  - "Death by dehydration shows the TFC death message"
---

# PAR-DATA-07: Damage types and mob effects

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
