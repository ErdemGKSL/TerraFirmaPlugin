# PAR-SURV-03 verification: eating in game, recorded and persisted

- Date: 2026-11-05. Base commit bf4509a plus the PAR-SURV-03 change (dist jar built from the working tree after
  `tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, vanilla world, priming start then restart, `docs/testing/headless.md`),
  CraftEngine, BetterModel, BetterHud and PacketEvents as pinned by the harness. Difficulty easy.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival, pack applied 3 s after join.
- Run by the worker session itself: no cheap-model agent could be started from this session (PROTOCOL §6.1); the main
  session judged the results. Eating = hotbar slot 1, right button held 3.5 s (`xdotool mousedown 3`).

## Steps and readings (RCON)

1. `effect give Bot minecraft:hunger 10 5`, `tfp debug hunger Bot 10`; `tfp debug player Bot`: `nutrition records: 0`.
2. `ce item give Bot tfp:food/mutton 1`, eat: food level 10 -> 14 (TFC hunger 4), `nutrition records: 1`.
3. `give Bot minecraft:bread 2`, hold right-click: refused, still 2 bread, food level stays 14 (TFC: no food component).
4. `give Bot minecraft:pumpkin_pie 1`, eat: food level 14 -> 18 (TFC's pie hunger 4, not vanilla's 8), pie used up,
   `nutrition records: 2`.
5. `give Bot minecraft:milk_bucket 1`, `effect give Bot minecraft:speed 300 0`, drink: held item `minecraft:bucket`,
   Speed still active, `nutrition records: 3` (TFC `MILK`, dairy 2).
6. Screenshot after eating: `hud-after-eating.png` (health 901/901, above the 850 a player with no records
   shows at full food: the nutrition health modifier rose with the records; thirst and hunger bars drawn).
7. Client killed, server stopped with `stop.sh`, server started again, client joined: `tfp debug player Bot` shows
   `nutrition records: 3`.

## Results

| Criterion | Result |
|---|---|
| In-game eat recorded | **Pass**: one record per meal (mutton, pumpkin pie, milk), TFC food levels written |
| Persisted across restart | **Pass**: 3 records after a full server restart |
| Vanilla food without a TFC definition | **Pass**: bread cannot be eaten (item kept, no change) |

Verdict: **pass**.
