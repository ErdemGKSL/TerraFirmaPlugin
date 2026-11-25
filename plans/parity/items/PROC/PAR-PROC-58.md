---
id: PAR-PROC-58
title: "Sluice intake: thrown deposits that rest on the ramp are never taken"
area: PROC
status: open
wave: 35
deps: [PAR-PROC-53]
size: S
aliases: ["found by PAR-PROC-47 (2026-11-25, second run) in game"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/sluice/**"
  - "paper/src/test/java/dev/tfp/paper/sluice/**"
  - "docs/verification/PAR-PROC-58/**"
ui: true
in_game_steps:
  - "a working sluice (water arrived): drop 2 tfp:deposit/native_copper/andesite from 4 blocks away so they land on the upper half: after at most a few seconds both are taken and loot appears at the output"
plan:
  - "Finding: deposits thrown onto the upper half came to rest at y = the block's base (100.0 for a sluice at y 100; the carrier has no collision there) and stayed there: TFC's gather box needs dy >= 0.5, and SluiceIntake gave up when the item was not in a box one second after it spawned (still sliding). Fix: the box starts at dy 0 (divergence: TFC's real ramp collision lifts items above 0.5), and an item not yet in a box is offered again each second like TFC's once-a-second gather."
  - "Code done: SluiceDevice.inGatherBox dy >= 0, SluiceIntake.offer retries; SluiceMockBukkitTest updated; the sluice tests pass."
  - "Pending in-game check (docs/verification/PAR-PROC-58/): with the fix the thrown deposits still were not taken: they leave the upper half within a second. The flowing input water (level 1, one block up and behind) spreads over the ramp (the carrier has no water-blocking collision) and sweeps the items past the output pit (resting at z = upper + 3.4). To do: keep the water off the ramp block (check what the carrier lets water do; TFC's sluice shape blocks it) or take the item at spawn / on each move (EntityMoveEvent is not available: use a short poll while a sluice is within a few blocks), then rerun the step."
tests:
  - "MockBukkit: the box starts at the block base; an item outside the box is offered again"
done_criteria:
  - "Thrown deposits resting on the ramp are taken by a working sluice in game"
---

# PAR-PROC-58: Sluice intake: thrown deposits that rest on the ramp are never taken

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
