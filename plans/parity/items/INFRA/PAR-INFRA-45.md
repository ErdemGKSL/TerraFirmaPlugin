---
id: PAR-INFRA-45
title: "InstallerTest teardown flake: rebuildWaitsUntilBetterHudIsNotReloading fails deleting @TempDir plugins"
area: INFRA
status: done
wave: 12
deps: []
size: S
aliases: []
tfc_refs: []
files_owned:
  - "paper/src/test/java/dev/tfp/paper/install/InstallerTest.java"
ui: false
in_game_steps: []
plan:
  - "Found by the PAR-FLUID-11 test run (2026-11-13, docs-only commit 9f6c72d): a full `./gradlew :paper:test` failed once with `InstallerTest > rebuildWaitsUntilBetterHudIsNotReloading()` in JUnit teardown, not in an assertion: `JUnitException: Failed to close extension context ... IOException: Failed to delete temp directory .../junit-<n>. The following paths could not be deleted: <root>, plugins` with a suppressed `DirectoryNotEmptyException`. Rerunning InstallerTest alone passes (24/24)."
  - "Likely cause: work on the trigger's io executor still writes under `<tmp>/plugins` (or `stateDir()`) after the test returns: a `CompletableFuture.delayedExecutor` HUD poll in `PackRebuildTrigger.awaitHud`, the follow-up after `settled()` completes, or an `Installer.applyPending` task. Confirm by looping the test (a `@RepeatedTest` copy run locally) and logging writes after return."
  - "Fix inside the test only: give the tests that start a PackRebuildTrigger a test-owned io executor and, in `@AfterEach`, shut it down and await termination (or wait for the trigger's pending work) before JUnit deletes the `@TempDir`. Do not change PackRebuildTrigger unless the loop shows a real product race; then add a follow-up for its owner."
  - "Done: test-only fix. Cause confirmed from the code: with `Runnable::run` as io, the BetterHud poll in `PackRebuildTrigger.awaitHud` runs on the JDK delayer thread, which completes `settled()` and only then writes the pack record (`Installer.recordPackBuilt`, AtomicFiles temp file + move) under `plugins/TerraFirmaPlugin/install`, racing JUnit's @TempDir delete. InstallerTest now gives the three BetterHud-wait tests a test-owned `TrackedIo` (single thread, counts queued and running tasks, drops tasks after close); `@AfterEach` drains and shuts it down. `rebuildWaitsUntilBetterHudIsNotReloading` also waits for io and asserts the record. 50 repeated runs of it green (temporary @RepeatedTest, 73/73), `:core:test :paper:test :tools:datagen:test` green. PackRebuildTrigger unchanged (record after settled() is by design, not a product race)."
tests:
  - "paper: InstallerTest passes 50 repeated runs of rebuildWaitsUntilBetterHudIsNotReloading and the full `:paper:test` stays green"
done_criteria:
  - "No InstallerTest case leaves background io work running past its own end; the @TempDir teardown no longer fails"
---

# PAR-INFRA-45: InstallerTest teardown flake: rebuildWaitsUntilBetterHudIsNotReloading fails deleting @TempDir plugins

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
