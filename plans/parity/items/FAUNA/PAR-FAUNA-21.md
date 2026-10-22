---
id: PAR-FAUNA-21
title: "Animal framework completion: species behaviour SPI, goal replacement, model re-apply checks"
area: FAUNA
status: open
wave: 39
deps: [PAR-INFRA-01]
size: M
aliases: ["M5-ENT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/CommonAnimalData.java"
  - "src/main/java/net/dries007/tfc/common/entities/EntityHelpers.java"
  - "src/main/java/net/dries007/tfc/common/entities/BrainAnimalBehavior.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/animal/**"
  - "core/src/test/java/dev/tfp/core/animal/**"
  - "core/src/test/resources/dev/tfp/core/animal/**"
  - "paper/src/main/java/dev/tfp/paper/animal/**"
  - "paper/src/test/java/dev/tfp/paper/animal/**"
  - "integration/bettermodel/src/main/java/dev/tfp/integration/bettermodel/BetterModelIntegration.java"
ui: true
in_game_steps:
  - "tfp debug animal spawn deer child"
  - "tfp debug animal spawn deer adult"
  - "bm reload"
  - "screenshot both"
plan:
  - "Current: carrier table, PDC record, lazy age/scale, model attach and re-apply, staggered 20-tick check, `animal.spawn_persist` self-test (M5-ENT-01)."
  - "Add `SpeciesBehaviour` SPI (goals, interactions, per-check hook, sounds) that FAUNA items implement in their own packages, so they never edit AnimalService."
  - "Replace carrier vanilla goals with an empty goal set + species goals; vanilla breeding/love mode stays cancelled."
  - "In-game screenshots of child/adult scale and persistence across `/bm reload`."
tests:
  - "paper: SPI dispatch test"
  - "itest: animal.spawn_persist still green"
done_criteria:
  - "A fake species behaviour runs its goal and per-check hook without AnimalService edits"
---

# PAR-FAUNA-21: Animal framework completion: species behaviour SPI, goal replacement, model re-apply checks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
