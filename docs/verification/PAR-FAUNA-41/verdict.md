# PAR-FAUNA-41 in-game check

- Date: 2026-11-25. Tree: 12be5e8 plus the PAR-FAUNA-41 change (uncommitted at run time), content regenerated with
  `:tools:datagen:generateContent`, jar from `:dist:shadowJar`.
- Server: Paper 1.21.11 (`spikes/client/server.sh`, vanilla default world), CraftEngine, BetterModel, BetterHud,
  PacketEvents as cached by run-paper. Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), pack applied.
- CraftEngine load: no issue in `configuration/lobstertrap/lobster_trap.yml` after the restart (the wet appearances'
  `auto_state: {type: waterlogged_tintable_leaves, id: lobster_trap}` loads).

## Steps
1. Stone floor at y=149..150 with a 3x3 water pool (y=150), creative Bot, noon, clear weather.
2. `ce item give Bot tfp:lobster_trap 4`; Bot right-clicks the floor of the pool (trap placed at 998,150,999, replacing a
   water source: `fluid=water`) and the stone (trap at 999,151,997: `fluid=empty`).
3. Screenshots with the HUD hidden (`wet-and-dry.png`, `wet-close-dry-right.png`).
4. Bot inside the pool east of the wet trap holds W toward it for 2 s: `Pos` stays at x=999.30 (blocked by a full cube at
   x=998; player half width 0.3).

## Verdict
| Criterion | Result |
|---|---|
| A trap placed in water shows water around TFC's model | pass: the water surface and the water body run through the trap's cell, the cage is drawn inside it (`wet-and-dry.png` front, `wet-close-dry-right.png` centre) |
| A dry trap shows none | pass: the trap on the stone has no water (`wet-and-dry.png` back, `wet-close-dry-right.png` right) |
| Walking into the wet trap: full collision, no swim prediction into the cell | pass (step 4) |
