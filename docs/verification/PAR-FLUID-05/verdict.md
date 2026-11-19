# PAR-FLUID-05 in-game check

- Date: 2026-11-19
- Tree: HEAD at the time of the commit that adds this file
- Server/client: not run (the container cannot run a Minecraft client)

## Verdict

Pending in-game check (container cannot run client)

The steps in the item (`ce item give Bot tfp:ceramic/jug`, `tfp debug fluid Bot jug tfp:milk 100`, hover screenshots at GUI
scale 2 and 3, `tfp debug fluid Bot jug tfp:beer 60`, Turkish client) still have to be run in game. PAR-POT-07 (jug) is done,
so the steps are runnable. The automated side (fluid registry, tooltip line, jug listener) is covered by the unit and
MockBukkit tests of PAR-FLUID-01 and PAR-POT-07, rerun for this commit.

| Criterion | Result |
|---|---|
| `100 / 100 mB of Milk` and `60 / 100 mB of Beer` under the jug name | pending |
| Turkish client shows TFC's Turkish pattern and fluid name | pending |
