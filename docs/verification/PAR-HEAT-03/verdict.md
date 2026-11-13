# PAR-HEAT-03 verification: hot item entities cool in water

- Date: 2026-11-13
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at 6f3a9e2 plus the PAR-HEAT-03 change (the commit that adds this
  file).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`. The module list ends with `hot_items`.
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2; the pack applied 3 s after the join.
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. `fill` a stone floor at y 98 with a 3x3 water pool (one deep) in it; `gamemode survival Bot`, `clear Bot`,
   `ce item give Bot tfp:metal/ingot/copper`.
2. Bot stands in the pool looking down: `tfp debug heat Bot 900`, key `Q` (drop), `tp` Bot 6 blocks away at once, wait 5 s,
   `tp` Bot back onto the ingot (picked up), `tfp debug heat Bot`.
3. The same on the dry stone floor next to the pool.
4. Step 2 again with a screenshot of the ingot in the pool and of the inventory after the pickup.

## Readings

| Run | Stored at pickup | Now (about 140 ticks after the set) |
|---|---|---|
| Water (step 2) | 372.00012 C (10 checks: 900 - 10 x (2.8 + 50), the golden value of `HotItemCoolingTest`) | 358.56 C, VERY_HOT |
| Air (step 3) | 868.64 C (passive cooling only) | 860.80 C, BRIGHT_RED |
| Water (step 4) | 317.24 C (the item lay a little longer) | 310.80 C, VERY_HOT |

## Evidence

- `01-ingot-in-water.png`: the dropped copper ingot lying in the pool.
- `02-picked-up-very-hot.png`: the ingot back in hotbar slot 1 after the pickup; the held-item popup reads "Very Hot٭".

## Criteria

| Criterion | Result |
|---|---|
| Dropping a 900 C ingot in water and picking it up after 5 s reads colder than in air | Pass: 358.6 C after water against 860.8 C after air at the same age; the pickup wrote the replayed water checks into the stack (in-place mirror write is seen by the inventory) |
