---
id: PAR-FAUNA-09
title: "Prey AI and wild animals"
area: FAUNA
status: open
wave: 42
deps: [PAR-FAUNA-04, PAR-FAUNA-03]
size: M
aliases: ["M5-WILD-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/prey"
  - "src/main/java/net/dries007/tfc/common/entities/ai/prey"
files_owned:
  - "core/src/main/java/dev/tfp/core/prey/**"
  - "core/src/test/java/dev/tfp/core/prey/**"
  - "core/src/test/resources/dev/tfp/core/prey/**"
  - "paper/src/main/java/dev/tfp/paper/prey/**"
  - "paper/src/test/java/dev/tfp/paper/prey/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn deer adult"
  - "approach; record 5 frames"
plan:
  - "Flee goal driven by PreyFlee (sensing via nearby entity snapshots), graze/idle, ramming prey charge; winged prey flutter; fox/rabbit/armadillo/panda/frog behaviours as TFC."
tests:
  - "core: PreyFleeGoldenTest exists"
  - "paper: goal transition"
done_criteria:
  - "A deer flees from a player in-game"
---

# PAR-FAUNA-09: Prey AI and wild animals

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
