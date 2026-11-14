# PAR-PROC-10 / PAR-PROC-37 verification: log pile, charcoal pit, charcoal pile in game

Two runs. The re-run (2026-11-14, below first) answers the reviews of ddbeb5b: it repeats the conversion the TFC way with
a look-at readout of every block id, and it supersedes the first run's `04` to `07`. The first run's `08` to `14` (axis x
models, the charcoal clicks on a log pile and a furnace, charcoal layers 1, 2 and 8) stay as they were.

## Re-run (verdict for ddbeb5b)

- Code under test: ddbeb5b (`push-to-main/dreamy-heisenberg-f5azym`), jar built with `:tools:datagen:generateContent`
  and `:dist:shadowJar` (`counts.ce_states` 1611). The fix-up commit that carries this verdict changes no runtime code
  (a class comment, the ledger, docs and a datagen test).
- Server: Paper 1.21.11, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents, by
  `spikes/client/server.sh` with `TFP_WORLD=1`, primed and restarted (`block.serverside-blocks` 2000 -> 3000); the restart
  loaded 1422 CraftEngine blocks with no issue.
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`), GUI scale 2, survival, op (for `/tfp debug block`, the
  look-at readout: the CraftEngine id and state of the targeted block). Captures are crops of 1280x720 shots.
- Run and judged by the main session (no subagent tool was available to this worker).

### What went wrong in the rejected re-run, and how this run avoids it

1. Stale pack. The priming start builds the CraftEngine pack while `serverside-blocks` is still 2000, so CraftEngine
   refuses `tfp:log_pile`, `tfp:burning_log_pile` and `tfp:charcoal_pile` ("Cannot allocate enough real block states")
   and the pack has no `iron_trapdoor.json` / `campfire.json` blockstates; the restart serves that cached pack because our
   content did not change. A one-log pile then shows a vanilla iron trapdoor (seen here before the fix). This run ran
   `ce reload all` after the restart (the rebuilt pack has both blockstates) and rejoined. Follow-up PAR-INFRA-55
   (installer: rebuild the pack on the restart that applies a raised `serverside-blocks`).
2. Ghost fire. Sneak clicks sent before the crouch had settled (0.3 to 0.5 s after shift) left a client-only fire: the
   server placed none (`execute if block ... fire` failed, the flint and steel took no damage) and the client kept its
   prediction. That is the "vanilla fire with black bars" of the rejected run, and the pile under it never lit, so
   digging in gave back the logs of an unlit log pile. Holding sneak 0.8 s before the click placed a real fire. The black
   squares are vanilla `large_smoke` particles of the fire block (vanilla `FireBlock.animateTick`, which TFC's fire has
   too); they stop when the fire is gone (`04`, `20`).

### Steps

1. Stone floor at y 119 around (90, 120, 169), noon. `give Bot oak_log 64`, flint and steel, dirt, iron shovel.
2. Sneak clicks with oak logs on the floor: one log (`15`, axis z: TFC's one-log model as an item display, the closed
   iron trapdoor carrier drawn empty), 8 logs (`16`, half block), 16 logs (`17`, full block); the stack went 64 -> 48.
3. Dirt on all four sides of the 16-log pile, stone below: `18` (readout `tfp:log_pile[axis=z,count=16]`).
4. Sneak click with flint and steel on the pile's top face: a real fire on top (`19`; durability used, fire present on the
   server), and the pile became `tfp:burning_log_pile[axis=z,count=16]`. The south dirt was taken off for one shot: `04`
   shows the burning pile itself with TFC's `molten_lit` model and the readout; TFC gives the burning pile no light and
   none is added. The fire on top was then replaced with dirt and the south dirt put back: `20` (closed on all six faces).
5. `tfp time add hours 20`: the covered pile became `tfp:charcoal_pile[layers=7]` (`21` from above with the top dirt
   removed, `22` from the side with the south dirt removed; TFC's charcoal texture).
6. One survival break with the iron shovel: the pile went to `layers=6` and exactly one `minecraft:charcoal` item entity
   (count 1) appeared, no other item (`23`). CraftEngine's break handling does not double the drop.
7. Charcoal pile layers 1 to 8 placed side by side with `/tfp debug place tfp:charcoal_pile[layers=n]` on the stone floor
   (`24`, seen from the south: 8 layers on the left down to 1 on the right), all with TFC's texture.

A burning pile that is broken drops nothing, as in TFC (`BurningLogPileBlock` has no loot table; `lightLogPile` clears
the log pile's contents; our burning device kind removes with `NOOP`), so no ledger row is needed. Logs only drop from an
unlit log pile.

### Verdict

| Criterion | Result |
|---|---|
| A covered lit log pile becomes charcoal in game (PAR-PROC-10, PAR-PROC-37) | PASS (steps 3 to 5; `18`, `19`, `04`, `20`, `21`, `22`) |
| Sneak-placing logs makes a log pile holding up to 16 logs and no vanilla log | PASS (step 2; `15` to `17`) |
| Log pile at 1, 8 and 16 logs, the burning pile, the charcoal pile layers with TFC's textures | PASS (`15` to `17`, `04`, `24`) |
| A layer break of a charcoal pile is not doubled | PASS (step 6; `23`) |

Not checked in this run: the hitbox rows and the charcoal pile support rule in game (tracked by PAR-INFRA-53, which lists
the charcoal pile layers); the smoke of TFC's `animateTick` (a recorded divergence).

## First run (2026-11-14, ddbeb5b's own capture)

- Same server and client set-up; the main session ran and judged it.
- `01` to `03`: log pile at 1 (axis z), 8 and 16 logs. `04` to `07` are superseded by the re-run (`04` now holds the
  re-run's burning pile, since the first run's `04` showed only the fire over the dirt ring).
- `08`, `09`: facing east (axis x) the one-log display lies along x on the south edge and the three-log model (block model
  turned y 90) matches it, as TFC's `blockstates/log_pile.json` and `getShapeByDirByCount` put them.
- `10`, `11`: a plain right click with charcoal on a log pile took a log out and placed no charcoal pile; on a vanilla
  furnace it opened the furnace screen. PASS.
- `12` to `14`: sneak clicks with charcoal on stone place a `tfp:charcoal_pile`: layer 1 (TFC's `charcoal_height2` as an
  item display on the heavy plate carrier), layer 2 (snow[layers=2] carrier), eight layers (the full `charcoal_block` on
  the solid pool). PASS, with a defect: one click adds two layers (follow-up PAR-PROC-40: Paper re-fires
  `RIGHT_CLICK_BLOCK` for the client's use-item packet after the denied use-item-on, and the new ray hits the new pile).
