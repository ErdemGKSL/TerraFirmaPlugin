---
id: PAR-INFRA-59
title: "Flaky OreOverlayServiceTest.blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing: the fast path sometimes sends no spawn under a loaded full ./gradlew check"
area: INFRA
status: done
wave: 16
deps: []
size: S
aliases: ["found by the PAR-INFRA-55 review (2026-11-17): ./gradlew check failed once at OreOverlayServiceTest.java:252 ('[same bundle as the block change] expected: 1 but was: 0'); three isolated reruns and a second full check passed"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/oreoverlay/OreOverlayService.java"
  - "paper/src/test/java/dev/tfp/paper/oreoverlay/OreOverlayServiceTest.java"
ui: false
in_game_steps: []
plan:
  - "Seen 2026-11-17: after settle(0) and blockChanged(BOT, 5, 11, 5, AIR, out) the outbox held 0 spawns instead of 1 (log of the failing run kept outside the repo). The test passes alone and in most full runs, so a race decides it: suspects are the fast path running while a patch or a neighbour-chunk border read is still pending (settled() only waits for chunk 0's own build/patch), the viewer's loaded set or near-gate not yet holding the chunk, or the spawn token bucket refusing the charge."
  - "Fix: find the race (loop the test 200x under CPU load), then make the test wait for the state the fast path needs (quiesce() before the block change) or make blockChanged deterministic for that state. Do not weaken the assertion."
  - "Cause: blockChanged queued the cell's patch (markDirty, region nextTick 1 ms in the test) before FastPath.decide read the tables; under load the patch read the world, patched the table and sent the up face itself first, so the fast path found nothing to do and the block change's outbox held 0 spawns (the same for the cover removal)."
  - "Done: OreOverlayService.blockChanged decides the fast path first and queues the patch in a finally; regression test fastPathDecidesBeforeThePatchItQueuesCanRun (a Regions mode where the queued patch runs to completion inside markDirty) failed with the reported message on the old order and passes now; blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing passed 200/200 repeats under 2x nproc CPU burners; ./gradlew check passes. No divergence (packet-only overlay runtime)."
tests:
  - "paper: OreOverlayServiceTest passes 200 repeated runs under load"
done_criteria:
  - "The test no longer fails intermittently; ./gradlew check passes"
---

# PAR-INFRA-59: Flaky OreOverlayServiceTest.blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
