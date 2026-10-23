---
id: PAR-INFRA-25
title: "Catch-up service wiring: self-test catchup.lazyblock, config.yml keys, device indexing on placement"
area: INFRA
status: open
wave: 5
deps: [PAR-INFRA-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CatchUpCase.java"
  - "itest/src/test/java/dev/tfp/itest/SelfTestSuiteTest.java"
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/catchup/**"
  - "paper/src/test/java/dev/tfp/paper/catchup/**"
  - "paper/src/main/java/dev/tfp/paper/device/DeviceModule.java"
  - "paper/src/main/java/dev/tfp/paper/device/DeviceService.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-11 built the catch-up trigger service (`core/catchup`: `LazyBlockIndex` + codec, `SweepArea`, `CatchUpAdvance`; `paper/catchup`: `CatchUpService`, `CatchUpKinds`, `CatchUpFeeder`, `CatchUpListener`, `CatchUpSweep`, `CatchUpModule`, test-only `ProbeCatchUp`). It is enabled as module `catchup`, but its self-test, its config.yml keys and device indexing need files outside that item."
  - "Add `CatchUpCase` (`catchup.lazyblock`, SelfTestSuite hub; update `SelfTestSuiteTest` for the new case): register `ProbeCatchUp.kind()` through `services().require(CatchUpService.class).kinds()`, place wheat age 0 in a test chunk, `track` it, advance the calendar by 3 stages, unload and reload the chunk (age follows on load), then advance again with a player (or a fake player position) within the sweep radius and wait one sweep period (age follows under a watching player; run with `-Dtfp.catchup.sweepPeriodTicks=40`). Run on Paper 1.21.11 and Folia 1.21.11 (delegated per PROTOCOL 5.3)."
  - "Move the settings from JVM properties (`CatchUpSettings.fromProperties`) to `config.yml` (`catchup.sweep_period_ticks` 600, `catchup.sweep_radius_chunks` 4, `catchup.feed_budget_micros` 1000, `catchup.commit_budget_micros` 1000) through `TfpConfig`; keep the properties as overrides for the self-test."
  - "Index devices: when a device is placed or its block entity created (`DeviceService.placed` / `created`), call `CatchUpService.track(block, CatchUpModule.DEVICE_KIND)` if the service is present, so every device catches up on chunk load (`CatchUpModule` already registers the direct kind over `DeviceService.catchUp`). Module order: `catchup` is listed after `device`, so look the service up lazily."
tests:
  - "itest: CatchUpCase passes on Paper 1.21.11 and Folia 1.21.11"
  - "paper: a placed device is indexed under tfp:device and caught up on chunk load (MockBukkit)"
done_criteria:
  - "A fake lazy block updates on chunk load and under a watching player (self-test `catchup.lazyblock`)"
  - "Catch-up settings read from config.yml"
---

# PAR-INFRA-25: Catch-up service wiring: self-test catchup.lazyblock, config.yml keys, device indexing on placement

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
