---
id: PAR-HEAT-18
title: "Fire starting in-game verification: a firestarter lights a stick and log pile into a firepit (self-test fire.start)"
area: HEAT
status: open
wave: 14
deps: [PAR-HEAT-06]
size: S
aliases: ["follow-up of PAR-HEAT-05 (its in_game_steps and in-game done-criterion)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/events/StartFireEvent.java"
  - "src/main/java/net/dries007/tfc/common/items/FirestarterItem.java"
files_owned:
  - "docs/verification/PAR-HEAT-05/**"
  - "itest/src/main/java/dev/tfp/itest/cases/FireStartCase.java"
ui: true
in_game_steps:
  - "Clear weather (`weather clear`); the ground is a grass or dirt block (not a snow layer: a firepit needs a sturdy floor), with air above it. Call the ground block G, the cell above it P."
  - "ce item give Bot tfp:firestarter"
  - "Put the pile on P as item entities, not as placed blocks (right-clicking sticks places nothing and right-clicking a log places a log block, in TFC too): over RCON `summon item <P centre> {Item:{id:\"minecraft:stick\",count:3}}` and `summon item <P centre> {Item:{id:\"tfp:wood/log/oak\",count:1}}` (any #tfc:firepit_logs log)"
  - "The bot stands at least 2 blocks from P, looks down at the TOP face of G (not at a log side, not at the pile) and holds right-click with the firestarter until the bow-like use ends (3.6 s). The fire or firepit appears in P, the cell above the target, never in the target itself. Repeat until it lights: the base chance is 0.25 (TFC's effective fireStarterChance), 0.075 in rain, plus 0.1 per kindling item (max +0.5) for the pile roll."
  - "Flint and pyrite: `ce item give Bot tfp:flint_and_pyrite`, summon a fresh pile in P as above, look at the TOP face of G and right-click once per try (no hold). Repeat until a strong start lights it (strength chance 0.25, then the pile roll 0.25 + kindling); a weak start lights nothing on plain ground. Then run `execute as Bot run tfp debug block` as below and quote its output verbatim: fire, or a firepit once PAR-HEAT-06 is in."
  - "`op Bot` once, then `execute as Bot run tfp debug block` while the bot looks at P (the `debug` node needs the admin permission and `debug block` reports on the sender player's target, so the command has to run as an opped Bot; the earlier capture's plain `tfp debug block` failed with Incorrect argument)"
  - "Screenshot at GUI scale 2: the firestarter held up (bow animation) during the use; the lit firepit in P afterwards with the log in its fuel; the sticks and log gone from the ground; the bot not burning. Crop any checkered (missing texture) block in view and name its block id."
plan:
  - "Deferred in-game check of PAR-HEAT-05 (PROTOCOL §6.5): the firepit is built through the `FirepitFactory` that PAR-HEAT-06 installs on `FireStarting` (paper/fire); before that a strong start on a stick pile places a fire block. PAR-HEAT-06 must call `services().require(FireStarting.class).firepitFactory(...)` and register a `FireLightable` that lights an unlit firepit."
  - "Add the self-test case `fire.start` (itest/.../cases/FireStartCase.java + its line in SelfTestSuite): on the self-test world, drop 3 sticks and 1 log on a block, call `FireStarting.start` with `Starter.fixed(STRONG, 1.0)` (a base of 1.0 always builds), assert the result FIREPIT, a firepit block at the position with the log in a fuel slot and lit, and no item entities left; then a WEAK start (no lightable) changes nothing; then the same pile with `Starter.flintAndPyrite` and a forced strong plan (`FireStartPlan(STRONG, 1.0, 0f)`) also gives FIREPIT. Run on Paper and Folia."
  - "Store crops and verdict.md in docs/verification/PAR-HEAT-05/."
tests:
  - "itest: fire.start on Paper and Folia 1.21.11"
done_criteria:
  - "In-game: a firestarter lights a stick+log pile into a firepit (self-test `fire.start`)"
---

# PAR-HEAT-18: Fire starting in-game verification: a firestarter lights a stick and log pile into a firepit (self-test fire.start)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
