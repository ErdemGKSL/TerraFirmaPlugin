---
id: PAR-PROC-53
title: "Sluice in game: water arrival is not noticed, thrown deposits give no loot in 20 s, outline and model check"
area: PROC
status: open
wave: 34
deps: [PAR-PROC-13]
size: S
aliases: ["found by PAR-PROC-47 (2026-11-25) in game"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/sluice/**"
  - "paper/src/test/java/dev/tfp/paper/sluice/**"
  - "docs/verification/PAR-PROC-53/**"
ui: true
in_game_steps:
  - "Place tfp:wood/sluice/oak facing south (upper half z, lower z+1), a one-block-wide stone channel behind it with a water source 7 blocks behind the input cell so the input (one up, one against the facing of the upper half) is flowing level 7 (Bukkit), and a one-deep pit two ahead of the upper half. Without touching the sluice: the output water appears in the pit"
  - "Throw 3 tfp:deposit/native_copper/granite onto the upper half (Bot, ctrl+q aiming at it): they are taken within about 2 s, the pickup sound plays every 5 s and loot items appear above the output water"
plan:
  - "Findings from PAR-PROC-47 (Paper 1.21.11): (1) the sluice placed before its water arrives never creates the output water: the only re-reads are neighbourChanged/placed/intake, and the input cell (one up, one back) is diagonal to the upper half, so no neighbour update reaches it; `/tfp debug device` shows working=false until something pokes the block (setblock stone/air next to it made `working=true` and the output water appeared). TFC polls in serverTick. Needs a trigger that does not tick: e.g. a water-flow listener (BlockFromToEvent / FluidLevelChangeEvent) that re-runs refresh for sluices around the changed block, or re-read on use; document it as a divergence if it is not equivalent."
  - "(2) With the sluice working, 3 thrown deposits left the ground (taken), but no loot appeared and the device state stayed `ticksRemaining` unchanged and `behind` kept growing: the wake-up (PaperSluiceEffects.wake, WAKE_TICKS) did not run the production within 25 s. Reproduce with `/tfp debug device` after the throw; check that hasSoil sees the stacks `refresh` reads (ctx.stacks()) and that `pending` (the set of woken keys) is cleared; add a MockBukkit test that steps the scheduler."
  - "(3) Not checked: selection outline of both halves vs TFC and the model alignment per facing (the model drew as a stair-like ramp)."
tests:
  - "MockBukkit: a sluice whose input water arrives after placement creates the output water; a wake after soil intake produces loot after the cycle"
done_criteria:
  - "A sluice placed before its water works once the water arrives, and thrown deposits become loot at the output in game (screenshots and verdict in docs/verification/PAR-PROC-53/)"
---

# PAR-PROC-53: Sluice in game fixes

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
