---
id: PAR-FAUNA-08
title: "Livestock AI and roster: graze, follow food, herd; all 18 domestic species"
area: FAUNA
status: done
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
  - "tfp debug livestock showcase"
  - "screenshot"
plan:
  - "Paper Mob Goals on carriers (throttled, staggered): temptation by food, panic, graze idle animations, herd following; showcase pen with every species (`tfp debug showcase livestock`)."
  - "Note: the showcase is `/tfp debug livestock showcase` (the built-in `showcase` literal takes a block family word owned by boot/TfpCommands; a module cannot add a child under it); in_game_steps updated."
  - "Done: PAR-FAUNA-08 commit. core/livestockai: LivestockRoster (the 18 domestic species and their TFC brain, LivestockAi/OviparousAi/TFCCamelAi literals, horse tempt), Temptation (DelegatingTemptingSensor + FollowTemptation), BreedApproach (BreedBehavior), LivestockShowcase (pen layout). paper/livestockai: LivestockAiModule replaces the carrier goals of the 10 livestock and 2 camels with swim, panic, avoid (herd warning), breed (walk + pairing), tempt by food (rotten rule), baby follows adult, idle stroll/graze, look; horses swap the vanilla tempt goal for TFC food; `/tfp debug livestock showcase` fences a pen with a female and a male of all 18 species. Cat/dog stay with PAR-FAUNA-14, rabbit with PAR-FAUNA-09, egg laying PAR-FAUNA-07. Tests: LivestockAiGoldenTest (8, core), LivestockGoalsMockBukkitTest (10, paper goal selection); ran :core:test :paper:test (BootstrapTest.commandTreeStructure fails on another module's unlisted `channel` literal, unrelated). Ledger row + 8 divergences. Pending in-game check (container cannot run client)."
tests:
  - "paper: goal selection"
done_criteria:
  - "Showcase pen screenshot with all 18 species"
---

# PAR-FAUNA-08: Livestock AI and roster: graze, follow food, herd; all 18 domestic species

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
