---
id: PAR-FAUNA-13
title: "Pests and critters: rats raiding containers, lobsters, crayfish, isopods"
area: FAUNA
status: open
wave: 43
deps: [PAR-FAUNA-04, PAR-FAUNA-03]
size: M
aliases: ["M5-WILD-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/prey/Pest.java"
  - "src/main/java/net/dries007/tfc/common/container/PestContainer.java"
  - "src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/critter/**"
  - "core/src/test/java/dev/tfp/core/critter/**"
  - "core/src/test/resources/dev/tfp/core/critter/**"
  - "paper/src/main/java/dev/tfp/paper/critter/**"
  - "paper/src/test/java/dev/tfp/paper/critter/**"
ui: true
in_game_steps:
  - "tfp debug pest force (new)"
  - "open a chest with food; screenshot"
plan:
  - "Pest infestation on container open (PestInfestation gates), feast movement and item theft, lazy discard; critters in water."
tests:
  - "core: PestRulesGoldenTest exists"
done_criteria:
  - "A rat appears and eats food from an opened chest (forced roll)"
---

# PAR-FAUNA-13: Pests and critters: rats raiding containers, lobsters, crayfish, isopods

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
