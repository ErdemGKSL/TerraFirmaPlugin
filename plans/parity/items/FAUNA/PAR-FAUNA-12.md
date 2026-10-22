---
id: PAR-FAUNA-12
title: "Amphibians: turtles, penguins, seals, crocodiles, frogs (play dead, land/water)"
area: FAUNA
status: open
wave: 43
deps: [PAR-FAUNA-11]
size: S
aliases: ["M5-WILD-03 (amphibians)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/ai/amphibian"
  - "src/main/java/net/dries007/tfc/common/entities/aquatic/Penguin.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/amphibian/**"
  - "core/src/test/java/dev/tfp/core/amphibian/**"
  - "core/src/test/resources/dev/tfp/core/amphibian/**"
  - "paper/src/main/java/dev/tfp/paper/amphibian/**"
  - "paper/src/test/java/dev/tfp/paper/amphibian/**"
  - "core/src/main/java/dev/tfp/core/aquatic/PlayDead*"
  - "core/src/test/java/dev/tfp/core/aquatic/PlayDead*"
ui: true
in_game_steps:
  - "tfp debug animal spawn turtle adult; hit it; screenshot"
plan:
  - "PlayDeadTimer, land/water switching, penguin/seal/turtle behaviours."
tests:
  - "core: PlayDeadTimerGoldenTest exists"
done_criteria:
  - "A hit turtle plays dead on land"
---

# PAR-FAUNA-12: Amphibians: turtles, penguins, seals, crocodiles, frogs (play dead, land/water)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
