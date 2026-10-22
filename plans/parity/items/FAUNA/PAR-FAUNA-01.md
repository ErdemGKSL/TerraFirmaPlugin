---
id: PAR-FAUNA-01
title: "Entity model import: TFC bbmodels and Java animations converted to BetterModel"
area: FAUNA
status: open
wave: 38
deps: []
size: M
aliases: ["M5-ENT-02", "X-MOD-01"]
tfc_refs:
  - "resources/entity_models"
  - "src/main/java/net/dries007/tfc/client/animation"
  - "src/main/java/net/dries007/tfc/client/model/entity"
files_owned:
  - "tools/models/**"
  - "content/generated/content/bettermodel/**"
  - "docs/art/model-coverage.md"
ui: false
in_game_steps: []
plan:
  - "Datagen/tools step: import TFC .bbmodel files, convert the Java AnimationDefinitions with the verified axis rule, rename to BetterModel names (idle, walk, run, attack, sleep, swim), drop stale channels, validate (--lenient), emit per-texture variant models."
tests:
  - "tools/models: validate.py over all outputs"
done_criteria:
  - "All imported models validate; contact sheet rendered and reviewed"
---

# PAR-FAUNA-01: Entity model import: TFC bbmodels and Java animations converted to BetterModel

> see_not_parity: NP-ENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
