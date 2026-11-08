---
id: PAR-FLUID-13
title: "Bucket double-use guard keyed on day time: buckets stop working when the daylight cycle is off"
area: FLUID
status: open
wave: 12
deps: [PAR-FLUID-02, PAR-FLUID-10]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/bucket/BucketListener.java"
  - "paper/src/test/java/dev/tfp/paper/bucket/BucketUseGuardMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Found by PAR-FLUID-09 (in-game, Paper 1.21.11, `docs/verification/PAR-FLUID-09/verdict.md`). `BucketListener.onInteract` keeps the last 'tick' a player's bucket acted (`lastUse`) so the main-hand and off-hand events of one click act once, and cancels any use in the same 'tick'. The tick is `player.getWorld().getFullTime()`, which on Paper is the world's day time, not a tick counter: with `gamerule advance_time false` (1.21.11; `doDaylightCycle false` on older versions, common on servers) the day time never changes, so after the first bucket use every later right-click with any tank or TFC fluid bucket is cancelled and does nothing until the time changes (reproduced: a filled wooden bucket could not be emptied until `advance_time` was set back to true). The same happens under `/tick freeze`, where game time stops but players still interact. TFC has no such guard (`Item.use` runs once per hand; a full bucket's main-hand use succeeds and the off-hand use is not reached)."
  - "Fix: key the guard on something that changes every interaction and works on Folia, for example skip the OFF_HAND `PlayerInteractEvent` when the MAIN_HAND event of the same click already acted (remember the acting event, compare in the next event on the same thread), or a monotonic per-region tick; never world day time or game time. BucketListener.java is also owned by PAR-FLUID-10, hence the dep."
tests:
  - "paper: MockBukkit: with the world time fixed (daylight cycle off), two separate right-clicks fill then empty a wooden bucket; the off-hand event of one click still does not act twice"
done_criteria:
  - "A tank bucket fills and then empties on two clicks while the world time does not advance (MockBukkit test)"
  - "One click still acts once across the main-hand and off-hand events (MockBukkit test)"
---

# PAR-FLUID-13: Bucket double-use guard keyed on day time: buckets stop working when the daylight cycle is off

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
