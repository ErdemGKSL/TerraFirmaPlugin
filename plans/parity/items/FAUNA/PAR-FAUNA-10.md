---
id: PAR-FAUNA-10
title: "Predator AI: hunting, home range, sleep schedule, packs"
area: FAUNA
status: open
wave: 43
deps: [PAR-FAUNA-09]
size: M
aliases: ["M5-WILD-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/predator"
  - "src/main/java/net/dries007/tfc/common/entities/ai/predator"
files_owned:
  - "core/src/main/java/dev/tfp/core/predator/**"
  - "core/src/test/java/dev/tfp/core/predator/**"
  - "core/src/test/resources/dev/tfp/core/predator/**"
  - "paper/src/main/java/dev/tfp/paper/predator/**"
  - "paper/src/test/java/dev/tfp/paper/predator/**"
ui: true
in_game_steps:
  - "tfp time set midnight; tfp debug animal spawn wolf adult (x3); spawn deer"
  - "record"
plan:
  - "Goals from PredatorRules: rest/hunt schedule, home wander, give-up, attack reach, sleep pose animation; pack alpha and respect for wolves/direwolves/hyenas."
tests:
  - "core: predator goldens exist"
done_criteria:
  - "A wolf pack hunts a deer at night"
---

# PAR-FAUNA-10: Predator AI: hunting, home range, sleep schedule, packs

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
