---
id: PAR-INFRA-29
title: "Loot delegation check: loose rocks and leaves drop by their TFC tables in the self-test"
area: INFRA
status: open
wave: 22
deps: [PAR-WORLD-09, PAR-WORLD-10]
size: S
aliases: []
tfc_refs:
  - "src/main/resources/data/tfc/loot_table/blocks/rock/loose"
  - "src/main/resources/data/tfc/loot_table/blocks/wood/leaves"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/LootCase.java"
ui: false
in_game_steps: []
plan:
  - "Deferred check of PAR-INFRA-21 (PROTOCOL §6.5): PAR-INFRA-21's self-test case `loot` (`LootCase`) checks loose granite `[count=3]` by hand (exactly 3 loose rocks, and again in an explosion) and oak leaves with shears (1 leaves, at most a sapling besides) and by hand (no leaves; sticks or a sapling may drop), but at PAR-INFRA-21 neither `tfp:rock/loose/granite` (PAR-WORLD-09) nor `tfp:wood/leaves/oak` (PAR-WORLD-10) is a generated CE block, so the case reports those lines as `not delegated yet`."
  - "The generators of PAR-WORLD-09 and PAR-WORLD-10 must delegate these blocks: their TFC tables have no `tfp:loot_table/*` template shape (loose rocks: `block_state_property` counts and `explosion_decay`; leaves: `match_tool` on `#c:tools/shear` / silk touch, sapling and stick chances), so the CE entry gets no `loot:` and the generator calls `LootTableExport.delegateBlock` (which ships the renamed table and reports the items it names). `LootTableExport.run` fails datagen if a delegated block still has a CE `loot:`."
  - "Run `:itest:runSelfTest -Pmc=1.21.11 -Pplatform=paper` twice (the first start installs the pack) and check that the loot case's loose rock and leaves lines report drops instead of `not delegated yet`. Fix the case only if the placed states or tool ids changed."
tests:
  - "itest: loot case on Paper 1.21.11 with the loose rock and leaves lines run"
done_criteria:
  - "A loose rock and a leaves block drop per their TFC tables in a self-test"
---

# PAR-INFRA-29: Loot delegation check: loose rocks and leaves drop by their TFC tables in the self-test

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
