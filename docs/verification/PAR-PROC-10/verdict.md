# PAR-PROC-10 / PAR-PROC-37 verification: log pile, charcoal pit, charcoal pile in game

- Date: 2026-11-14
- Code under test: the PAR-PROC-37 commit that carries this verdict (its parent is 460e994 on
  `push-to-main/dreamy-heisenberg-f5azym`); the jar was built from that working tree with `:dist:shadowJar` after
  `:tools:datagen:generateContent` (`counts.ce_states` 1611 in the jar's datagen summary).
- Server: Paper 1.21.11 (run-paper cache), CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449,
  PacketEvents, installed by `spikes/client/server.sh` with `TFP_WORLD=1` (TFC world), primed and restarted. The priming
  start raised `block.serverside-blocks` 2000 -> 3000 ("1611 + margin"); the restart loaded 1422 CraftEngine blocks with no
  CraftEngine issue and the feature modules `charcoal` and `charcoal_pile_support`. Without the `ce_states` count (first
  try, the installer sized from the 1353 blocks and kept 2000) CraftEngine could not allocate the real states of the quern,
  composter, firepit and some ores: our 1611 states plus CraftEngine's default pack (446) need 2057.
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`, GUI scale 2, pack applied after 3 s), survival, not op.
  Captures are crops of 1280x720 shots, scaled to half size.
- Run by the main session (no subagent tool was available to this worker); the main session judged every capture.

## Steps

1. Stone floor test site at (63, 64, 160) (`testroom.sh`, then a stone floor), noon, clear weather. `give Bot oak_log 64`,
   dirt, flint and steel, charcoal.
2. Sneak + right click with oak logs on the floor, facing north: a `tfp:log_pile` (axis z) with one log, no vanilla log
   block (`01`, the one-log state on its hitbox-only carrier: TFC's model as an item display over a closed iron trapdoor
   outline); 7 more sneak clicks: 8 logs, two full 4 px layers (`02`); 8 more: 16 logs, a full block (`03`); the stack went
   64 -> 48, nothing was placed above. Each sneak click took exactly one log. Facing east (axis x): the one-log display
   lies along x on the south edge and the three-log model (block model turned y 90) matches it (`08`, `09`), as TFC's
   blockstates/log_pile.json and `getShapeByDirByCount` put them.
3. Dirt on the four sides. A plain click with flint and steel on the pile took a log (TFC: a non-sneak `useItemOn` on a log
   pile extracts, whatever the held item), 15 logs left. A sneak click with flint and steel put fire on top; the fire lit
   the pile: `tfp:burning_log_pile` with TFC's `molten_lit` burning model (`04`). Dirt on top (replacing the fire).
4. `tfp time add hours 20` (20000 calendar ticks): the covered pile became a charcoal pile (`05` from above, `06` from the
   side after removing the east dirt): charcoal texture, about seven layers.
5. Dig in: one survival break took one layer off and dropped exactly one `minecraft:charcoal` entity (`07`; `@e[type=item]`
   near the pile: one charcoal, count 1), so CraftEngine's own break handling does not double the drop.
6. A plain right click with charcoal on a log pile took a log out (47 logs in hand, the pile one log fewer) and placed no
   charcoal pile (charcoal stayed at 16) (`10`); on a vanilla furnace it opened the furnace screen (`11`).
7. Sneak clicks with charcoal on the stone floor place a `tfp:charcoal_pile`: layer 1 (`12`, 2 px, TFC's
   `charcoal_height2` as an item display on the heavy plate carrier), layer 2 (`13`, snow[layers=2] carrier with TFC's
   model), eight layers (`14`, the full `charcoal_block` on the solid pool).

## Verdict

| Criterion | Result |
|---|---|
| A covered lit log pile becomes charcoal in game (PAR-PROC-10, PAR-PROC-37) | PASS (steps 2 to 5) |
| Sneak-placing logs makes a log pile holding up to 16 logs and no vanilla log (CraftEngine's HIGHEST block-item placement denied) | PASS (step 2) |
| Log pile at 1, 8 and 16 logs, burning pile, charcoal pile layers with TFC's textures | PASS (`01` to `04`, `12` to `14`) |
| A layer break of a charcoal pile is not doubled | PASS (step 5) |
| Plain charcoal click: the furnace opens, the log pile gives a log, no charcoal pile | PASS (step 6) |
| A sneak click with charcoal places a charcoal pile | PASS, with a defect: one click adds two layers (two charcoal) |

Defect found (follow-up PAR-PROC-40, not a done criterion of either item): the vanilla client sends a use-item packet
after the use-item-on packet the server denied, and Paper re-fires `PlayerInteractEvent` `RIGHT_CLICK_BLOCK` for it with a
fresh ray trace when the targeted position changed; the ray now hits the new pile, so `CharcoalListener` adds a second
layer. Seen with charcoal (one click: 16 -> 14 charcoal, a two-layer pile); log clicks took one log each.

Not checked here: the smoke of TFC's `animateTick` (a recorded divergence: particles only on explicit actions).
