---
id: PAR-INFRA-38
title: "TfpCommands.reply: no blocking console wait when the reply future needs the caller's own thread"
area: INFRA
status: open
wave: 8
deps: [PAR-INFRA-01]
size: S
aliases: ["PAR-INFRA-16 review follow-up: console `tfp debug` commands that defer to a region task stall Paper's main thread for 2 s (Watchdog report in the in-game run)"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/boot/TfpCommands.java"
  - "paper/src/test/java/dev/tfp/paper/boot/TfpCommandsReplyTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: `TfpCommands.reply` waits up to CONSOLE_WAIT_MILLIS (2 s) with `future.get` when the sender is the console. On Paper the console runs on the main thread, which owns every region; a debug action that completes its future from a region task can then only complete after the wait times out, so each such call stalls the server for 2 s and parallel calls add up to a Watchdog report. PAR-INFRA-16 made its own `oreoverlay place` run inline in that case; other `tfp debug` actions may still hit it."
  - "Fix: never block the thread a pending future may need: when the future is not done, reply asynchronously (`whenComplete` -> send the reply on the sender's scheduler) instead of `get(timeout)`, or wait only when the caller does not own any region (Folia console thread)."
tests:
  - "paper: a console reply whose future is completed by a task queued on the same thread replies once and does not block (MockBukkit or a fake executor)"
done_criteria:
  - "No console `tfp debug` command blocks the main thread on Paper waiting for its own region task; test green; ./gradlew check green"
---

# PAR-INFRA-38: TfpCommands.reply: no blocking console wait when the reply future needs the caller's own thread

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.

Follow-up of the PAR-INFRA-16 review (in-game follow-up 5): `TfpCommands` is not in PAR-INFRA-16's files.
