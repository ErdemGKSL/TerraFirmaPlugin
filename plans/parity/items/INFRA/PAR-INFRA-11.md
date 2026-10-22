---
id: PAR-INFRA-11
title: "Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks"
area: INFRA
status: open
wave: 4
deps: [PAR-INFRA-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/catchup/**"
  - "core/src/test/java/dev/tfp/core/catchup/**"
  - "core/src/test/resources/dev/tfp/core/catchup/**"
  - "paper/src/main/java/dev/tfp/paper/catchup/**"
  - "paper/src/test/java/dev/tfp/paper/catchup/**"
ui: false
in_game_steps: []
plan:
  - "Index lazy blocks per chunk (kind + position) in the chunk PDC when placed; on chunk load enqueue a snapshot catch-up per indexed block, decided async, applied sync with the budget."
  - "Optional slow sweep (configurable, default every 600 ticks) for chunks near players so visible crops and bushes change stage while watched."
  - "Expose `CatchUpKinds.register(kind, pureAdvance)` for crops, bushes, torches, drying bricks, weathering, saplings."
tests:
  - "paper: MockBukkit chunk load enqueues once per indexed block"
  - "paper: budget held with 10k indexed blocks (fake clock)"
done_criteria:
  - "A fake lazy block updates on chunk load and under a watching player (self-test)"
---

# PAR-INFRA-11: Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
