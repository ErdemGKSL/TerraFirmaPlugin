---
id: PAR-FAUNA-03
title: "Entity texture variants, gendered and aged textures, render review"
area: FAUNA
status: open
wave: 40
deps: [PAR-FAUNA-02]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/GenderedRenderAnimal.java"
  - "src/main/resources/assets/tfc/textures/entity/animal"
files_owned:
  - "integration/bettermodel/src/main/java/dev/tfp/integration/bettermodel/variant/**"
  - "content/generated/content/bettermodel/variants/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn cow adult male"
  - "tfp debug animal spawn cow old female"
  - "screenshot"
plan:
  - "Variant selection from AnimalRecord (gender, old age, colour) applied at model creation; render review per species."
tests:
  - "integration/bettermodel: variant pick"
done_criteria:
  - "Screenshots of male/female/old cows"
---

# PAR-FAUNA-03: Entity texture variants, gendered and aged textures, render review

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
