---
id: PAR-INFRA-31
title: "Catch-up settings in config.yml: catchup.* keys through TfpConfig and ConfigCodec"
area: INFRA
status: open
wave: 7
deps: [PAR-INFRA-25, PAR-INFRA-15]
size: S
aliases: ["follow-up of PAR-INFRA-25 (config.yml keys need ConfigCodec, outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java"
  - "paper/src/main/java/dev/tfp/paper/catchup/CatchUpModule.java"
  - "paper/src/main/java/dev/tfp/paper/catchup/CatchUpSettings.java"
  - "paper/src/test/java/dev/tfp/paper/catchup/CatchUpMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Current (PAR-INFRA-25): the catch-up service reads its settings only from the JVM properties `tfp.catchup.sweepPeriodTicks`, `sweepRadiusChunks`, `feedBudgetMicros`, `commitBudgetMicros` (`CatchUpSettings.fromProperties`). A `catchup:` section in config.yml cannot land without `ConfigCodec` (it reports unknown top-level keys as warnings, and `ConfigServiceTest`/`ConfigCase` require none), which PAR-INFRA-25 does not own and PAR-INFRA-15 (open, wave 6) is changing; hence the wave after it."
  - "Add `TfpConfig.CatchUp(sweepPeriodTicks 600, sweepRadiusChunks 4, feedBudgetMicros 1000, commitBudgetMicros 1000)` with the ranges of `CatchUpSettings` (period >= 0, 0 = sweep off; radius 0..32; budgets 1..50000); `ConfigCodec` parses and writes the section `catchup:` with keys `sweep-period-ticks`, `sweep-radius-chunks`, `feed-budget-micros`, `commit-budget-micros` (bad values: default + warning, as the other keys). Keep the file's key style (kebab-case) rather than the snake_case of the old PAR-INFRA-25 plan text."
  - "config.yml: the section with a comment block (what each key does, the sweep is the substitute for TFC's per-tick `checkForCalendarUpdate`, the period and budgets need a restart)."
  - "`CatchUpModule.enable`: `CatchUpSettings.from(context.config().current().catchUp())`, then the JVM properties override it (`fromProperties(base, System::getProperty)`), so the self-test can still pass `-Dtfp.catchup.sweepPeriodTicks=40`."
tests:
  - "core: ConfigCodecTest round trip with a custom catchup section, defaults for missing keys, warnings for out-of-range values"
  - "paper: ConfigServiceTest shipped config.yml parses without warnings; CatchUpMockBukkitTest module reads config values and the properties override them"
done_criteria:
  - "Catch-up settings read from config.yml, JVM properties still override them"
---

# PAR-INFRA-31: Catch-up settings in config.yml: catchup.* keys through TfpConfig and ConfigCodec

> see_not_parity: NP-SIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
