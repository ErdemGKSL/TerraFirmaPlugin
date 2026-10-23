---
id: PAR-INFRA-11
title: "Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks"
area: INFRA
status: done
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
  - "Rescoped: the self-test criterion (a fake lazy block updates on chunk load and under a watching player) needs a case in `itest/.../cases/`, outside files_owned; it moved with that criterion to follow-up PAR-INFRA-25, together with the config.yml keys (config is outside files_owned; settings come from JVM properties `tfp.catchup.*` until then) and indexing devices on placement (`paper/device`, outside files_owned). `ProbeCatchUp` (test-only fake lazy block over vanilla wheat) is ready for that case."
  - "Done: core/catchup (`LazyBlockIndex` + versioned codec in chunk PDC `tfp:catchup/index`, `SweepArea`, `CatchUpAdvance` with the device adapter), paper/catchup (`CatchUpService` track/untrack/chunkLoaded/touch/sweep with cached per-chunk index, async decode, `CatchUpFeeder` budgeted snapshot capture, one `AsyncDecisionPipeline` per kind and world with the commit budget, orphan pruning batched per chunk; `CatchUpKinds` async and direct kinds; `CatchUpListener` chunk load/unload, interaction, break; `CatchUpSweep` every 600 ticks over 4 chunks around each player; `CatchUpModule` (module `catchup`, service `CatchUpService`, direct kind `tfp:device` over `DeviceService.catchUp`); `ProbeCatchUp`); tests core CatchUpCoreTest (8), paper CatchUpMockBukkitTest (10), CatchUpBudgetMockBukkitTest (1, 10 240 blocks, fake clock); ARCHITECTURE.md §7.2; ledger feature row + 3 divergences; follow-up PAR-INFRA-25."
tests:
  - "paper: MockBukkit chunk load enqueues once per indexed block"
  - "paper: budget held with 10k indexed blocks (fake clock)"
done_criteria:
  - "Catch-up service built and covered by core and MockBukkit tests: chunk load enqueues once per indexed block, interaction, sweep, pruning, budget held with 10k indexed blocks"
  - "Self-test criterion (a fake lazy block updates on chunk load and under a watching player): deferred check tracked in PAR-INFRA-25"
---

# PAR-INFRA-11: Catch-up trigger service: chunk load, interaction and visible-area sweep for lazy blocks

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
