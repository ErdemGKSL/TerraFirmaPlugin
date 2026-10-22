---
id: PAR-FAUNA-05
title: "Livestock: feeding, familiarity and breeding interactions"
area: FAUNA
status: open
wave: 41
deps: [PAR-FAUNA-04]
size: M
aliases: ["M5-LIV-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/Mammal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/livestock/**"
  - "core/src/test/java/dev/tfp/core/livestock/**"
  - "core/src/test/resources/dev/tfp/core/livestock/**"
  - "paper/src/main/java/dev/tfp/paper/livestock/**"
  - "paper/src/test/java/dev/tfp/paper/livestock/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn cow adult male; ... female"
  - "feed both daily (tfp time add days 1 between)"
  - "tfp debug animal info"
plan:
  - "Right-click with food (species tag) feeds once per day, raising familiarity (LivestockRules); mating when both are ready; pregnancy and birth via lazy catch-up on the per-animal check; babies grow (scale updates)."
tests:
  - "core: livestock goldens exist"
  - "paper: feed/mate interactions"
done_criteria:
  - "Two familiar cows produce a calf after gestation (time add)"
---

# PAR-FAUNA-05: Livestock: feeding, familiarity and breeding interactions

> see_not_parity: NP-UI-01, NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
