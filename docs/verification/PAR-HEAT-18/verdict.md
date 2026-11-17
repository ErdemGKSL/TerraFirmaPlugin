# PAR-HEAT-18 verification: fire starting into a firepit (self-test fire.start)

Date 2026-11-17. Base commit 8040a7c plus this item's change to `itest/.../cases/FireStartCase.java`. Paper 1.21.11 build
132 and Folia 1.21.11 build 14, CraftEngine 26.9.2, `:itest:runSelfTest -Pmc=1.21.11`, two starts per platform on the
same run directory (the first start only raises CraftEngine's `block.serverside-blocks`; until the restart `tfp:firepit`
fails with "Cannot allocate enough real block states" and the case skips for that reason).

**Verdict: blocked on PAR-WORLD-10.** The firepit is now loaded (PAR-HEAT-19) and is built from the pile, but it cannot
light: TFC's logs are not CraftEngine items yet, so the only `#tfc:firepit_logs` log is vanilla `minecraft:oak_log`
(through `#minecraft:logs_that_burn`). No TFC fuel file names it (`fuel/oak_logs.json` is `#tfc:oak_logs`, the tfp
logs), so TFC's `consumeFuel` takes it out of the consume slot for no burn time (ash at 50 %) and the firepit stays
unlit, exactly as in TFC. The first run (before this change) failed on "the firepit is not lit"; the case now asserts that
TFC outcome and reports SKIPPED with the reason, and asserts lit when a TFC log is available.

| # | Check | Paper 1.21.11 | Folia 1.21.11 |
|---|---|---|---|
| 1 | Strong start (base 1.0) on 3 sticks + 1 log gives `FIREPIT`, a `tfp:firepit` in the pile cell, ground items gone | pass | pass |
| 2 | That firepit is lit with the log as fuel | **not reachable**: unlit, the non-fuel log consumed (fuel slots 0, burnTicks 0, ash 0) | same |
| 3 | Weak start on a fresh pile: `WEAK`, cell air, the 2 stacks still on the ground | pass | pass |
| 4 | Flint and pyrite with a forced strong plan: `FIREPIT` | pass (unlit, as 2) | pass (unlit, as 2) |
| 5 | No thread-ownership finding | n/a | pass (assertions on, none logged) |
| 6 | In-game steps (screenshots) | not run: they give `tfp:wood/log/oak`, which does not exist before PAR-WORLD-10 | |

Case output (Folia, second start): `pile at 904,65,8: strong: unlit tfp:firepit (fuel slots 0, burnTicks 0, ash 0),
ground clear; weak: nothing changed, 2 stacks on the ground; flint and pyrite: unlit tfp:firepit (fuel slots 0,
burnTicks 0, ash 0), ground clear`. `firepit.cook` (PAR-HEAT-19, stick bundle as fuel) PASSED on both in the same runs.

Other failures in these runs, not touched by this item: `device.roundtrip` (second start), `worldgen.terrain_generates`
(Paper), `animal.spawn_persist` (Folia second start), `ce.pack_issues` (first start).

Once PAR-WORLD-10 is done: rerun both platforms (two starts each) expecting `fire.start` PASSED (lit), then the in-game
steps of the item file.
