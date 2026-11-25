# PAR-INFRA-65 verification: block flammability in game

- Date: 2026-11-25
- Plugin jar: branch tip `ea8f190` (includes PAR-INFRA-47's burn settings on the CraftEngine blocks). A first run on an older jar
  (commit 6e783d9, before PAR-INFRA-47) is the control: the `tfp:wood/log/oak` + leaves cluster did not burn at all in
  129 s with the fire relit every 10 s, while an identical vanilla oak log and leaves cluster burnt away completely.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (peaceful, `doFireTick` on). Client: vanilla 1.21.11 under Xvfb, pack applied, Bot in creative. Run by the worker session itself.

## Steps and results (blocks counted by RCON every 5 s, `tfp:` clusters built by placing one block and `/clone`)

| step | result | evidence |
|---|---|---|
| 3x3 `tfp:wood/log/oak` with `tfp:wood/leaves/oak` above (18 blocks) on stone, fire beside the base (a fire block set again every 3 s: the flint and steel on the base), counts at 0, 23, 50, 89, 131, 149 s | PASS: 18, 13, 8, 7, 7, 7 blocks left: fire spread and consumed 11 of 18 | `gui2-tfp-oak-cluster-t0.png`, `gui2-tfp-oak-cluster-t10.png`, `gui2-tfp-oak-cluster-t30.png` (the wood visuals are missing-texture cubes: see below) |
| same relight on a 3x3x2 `tfp:rock/raw/granite` cluster | PASS: 18 of 18 blocks at 149 s, no fire spread | (counts) |
| vanilla control, 3x3 oak log + leaves with the same relight | 18, 18, 18, 18, 17, 14 at 0, 23, 50, 93, 115, 134 s: burns more slowly than ours here (random fire ticks), same order of magnitude | (counts) |
| a `tfp:wood/log/oak` next to lava | PASS: fire above the log within 40 s (`ignitedByLava`); the vanilla control log next to lava had not caught in 80 s (random tick) | `gui2-lava-ignites-tfp-log.png` |
| metal block | NOT run (no `tfp` metal block was placed) | |

| Criterion | Result |
|---|---|
| Logs and leaves burn and catch from lava at vanilla pace; granite does not | **Pass** (metal not checked; thatch and planks not part of this run) |

## Finding (not this item)

At both jars `tfp:wood/log/*` and `tfp:wood/leaves/*` draw as the missing-texture cube in the client (client log
`Missing block model: tfp:block/wood/log/chestnut`, `.../leaves/dense_leaves/sequoia`): datagen writes the block entries
but no block models for the wood logs and leaves. It does not affect flammability.

## Self-test

`flammability.spread` (`FlammabilityCase`, added to `SelfTestSuite`) passes on Paper 1.21.11 with CraftEngine (`:itest:runSelfTest -Pmc=1.21.11`): fire consumed 4 to 5 of the 18 wood blocks in the self-test window, the granite cluster stayed 18 of 18. The case sets `fire_spread_radius_around_player` to -1 for its run (1.21.11 fires only tick near players, and the self-test has none).
