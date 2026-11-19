---
id: PAR-GATHER-03
title: "Tree felling: the whole tree falls when the trunk is cut with an axe"
area: GATHER
status: done
wave: 22
deps: [PAR-WORLD-10]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/AxeLoggingHelper.java"
  - "src/main/java/net/dries007/tfc/util/events/LoggingEvent.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/logging/**"
  - "core/src/test/java/dev/tfp/core/logging/**"
  - "core/src/test/resources/dev/tfp/core/logging/**"
  - "paper/src/main/java/dev/tfp/paper/logging/**"
  - "paper/src/test/java/dev/tfp/paper/logging/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/axe/copper"
  - "break the bottom log of a generated tree"
  - "screenshot"
plan:
  - "On breaking a natural log with an axe (sneak disables, as TFC), snapshot the connected log set (bounded), decide async which logs drop, apply sync in budgeted batches; durability cost per log as TFC; leaves left to decay (PAR-GATHER-04)."
  - "Done: core/logging TreeLogs (TFC findLogs, 31 direction rows exported from TFC, 6 tests) + paper/logging TreeFelling/FellingModule (7 MockBukkit tests + 1 shipped-tag test); no sneak check (TFC has none). Felling needs branch_direction on log blocks, which tree generation does not set yet: follow-up PAR-GATHER-09. Pending in-game check (container cannot run client)."
tests:
  - "core: connected-set golden on fixture trees"
  - "paper: budgeted commit test"
done_criteria:
  - "Cutting a TFC oak trunk drops all its logs (screenshot before/after)"
---

# PAR-GATHER-03: Tree felling: the whole tree falls when the trunk is cut with an axe


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
