---
id: PAR-FAUNA-08
title: "Livestock AI and roster: graze, follow food, herd; all 18 domestic species"
area: FAUNA
status: open
wave: 42
deps: [PAR-FAUNA-05, PAR-FAUNA-03]
size: M
aliases: ["M5-LIV-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/ai/livestock"
  - "src/main/java/net/dries007/tfc/common/entities/TFCEntities.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/livestockai/**"
  - "core/src/test/java/dev/tfp/core/livestockai/**"
  - "core/src/test/resources/dev/tfp/core/livestockai/**"
  - "paper/src/main/java/dev/tfp/paper/livestockai/**"
  - "paper/src/test/java/dev/tfp/paper/livestockai/**"
ui: true
in_game_steps:
  - "tfp debug showcase livestock"
  - "screenshot"
plan:
  - "Paper Mob Goals on carriers (throttled, staggered): temptation by food, panic, graze idle animations, herd following; showcase pen with every species (`tfp debug showcase livestock`)."
tests:
  - "paper: goal selection"
done_criteria:
  - "Showcase pen screenshot with all 18 species"
---

# PAR-FAUNA-08: Livestock AI and roster: graze, follow food, herd; all 18 domestic species

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
