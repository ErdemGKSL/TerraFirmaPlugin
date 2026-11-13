# PAR-PROC-05 / PAR-PROC-29 verification: scraping unrefined paper on a log

- Date: 2026-11-13
- Code under test: the PAR-PROC-29 re-add (ScrapingBlock run line in `Datagen.generate`) on top of 755df30 (PAR-INFRA-44);
  the jar built from the worktree with `:dist:shadowJar` after `generateContent`. The commit that carries this verdict is
  that change.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel and BetterHud as installed by `spikes/client/server.sh`
  with `TFP_WORLD=1`. Primed (`RESTART REQUIRED`), world deleted, restarted: `Loaded blocks (1417)`, no CraftEngine
  issues, feature module `scraping` enabled, no plugin errors (only PacketEvents' update check, HTTP 403).
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`), pack applied after 3 s. First session at GUI scale 2,
  second session (after a client kill and a server restart) at GUI scale 3.
- The run was done by the worker session itself (no cheap-model agent was available in it); the screenshots were judged
  here.

## Carrier

`tfp:scraping` has one appearance, `minecraft:heavy_weighted_pressure_plate[power=0]` with `transparent: true` (the
selector's best free carrier for TFC's 16 x 1 x 16 plate after PAR-INFRA-42/44, `reports/hitbox-carriers-*.md`), plus the
policy's hide mapping `heavy_weighted_pressure_plate[power=0] -> [power=1]` in `scraping.yml`. The vanilla shape data
(`ScrapingBlockTest.carrierBoxOnEveryVersion`, every researched version) gives selection box(1, 0, 1, 15, 1, 15) =
14 x 1 x 14 and no collision. The outline the client draws is that inset box (`01`: the ring of the log's top outside it
selects the log). The surface itself is the item display.

Shared state check: real heavy weighted pressure plates, both `setblock` and placed by Bot from a vanilla plate item, are
drawn as pressed iron plates next to an unpressed gold plate, never empty; a lily pad on a water pool next to them is
drawn as usual (`12-heavy-plates-drawn-pressed-pond.png`). The placed-item carrier that shares the state is drawn
transparent the same way, so sharing it changes nothing visible.

## Steps (RCON + `xdotool`, Bot in survival, not op)

1. Stone floor at y 149, `minecraft:oak_log` at (816, 150, -2780) (in `#minecraft:logs`, so in `tfc:scraping_surface`).
   `ce item give Bot tfp:unrefined_paper 4`, `ce item give Bot tfp:metal/knife/copper`.
2. Right-click the log's top with the unrefined paper: the surface is placed, one paper goes in (4 -> 3), the 1 px
   surface shows the unrefined paper texture (`01-unrefined-paper-placed.png`, GUI 2, about 15 degrees from the north).
3. Bot north of the log (feet 816.5, 150, -2781.8), yaw/pitch computed per cell centre, knife right-clicks. Cell 0
   (north-west: min x, min z) first: that corner tile turns to paper, knife damage 1 (`02-one-cell-nw.png`, Bot looking
   south, so north-west is the near right corner).
4. Cells 1 to 7: the north half is paper, knife damage 8 (`04-eight-cells.png`; `05-eight-cells-top-north-up.png` from
   straight above in spectator, yaw 180 so north is at the top: paper north, unrefined south, sharp edge).
5. Cells 8 to 14 (damage 15); client killed, server stopped through `stop.sh`, server restarted, Bot rejoined (GUI 3):
   the display is back with the same 15 cells, only the south-east tile unscraped (`06-after-restart-gui3.png`).
6. Cell 15: the whole surface is paper, knife damage 16 (`07-sixteen-cells-gui3.png`). Left-click the surface: it breaks
   at once (hardness 0.2), one `minecraft:paper` item drops, the display is removed (`08-broken-paper-dropped-gui3.png`).
7. Orientation: a fresh surface (paper 3 -> 2), only cell 0 scraped; from straight above, north at the top, the scraped
   tile is the top-left (north-west) corner (`03-orientation-nw-north-up.png`).
8. Wax: right-click that surface with `minecraft:honeycomb`, `minecraft:red_dye`, `minecraft:blue_dye` (each consumed):
   unscraped tiles red, the scraped north-west tile blue (`09-waxed-red-blue.png`). A knife click on cell 5 afterwards
   changes nothing and costs no durability (damage stays 18: 16 + 1 for breaking the surface with the knife in hand in
   step 6 + 1 for cell 0 in step 7).
9. Break the log under that surface by hand: the log drops, the surface and its display go (no item display left near
   the block), and the held unrefined paper drops as an item (`10-log-broken-surface-gone.png`).
10. Heavy plates and pond: see Carrier above (`12`).

Not run: the hide steps (pending PAR-FAUNA-17, see the item's pending line); a TFC wood log (no wood CE blocks yet).

## Verdict

| Criterion | Result |
|---|---|
| Scraping unrefined paper on a log yields paper in game, the surface shows each scraped tile | **Pass** (steps 2 to 6) |
| Orientation: the north-west cell is the north-west tile | **Pass** (steps 3 and 7) |
| Wax and dyes, knife blocked on a waxed surface | **Pass** (step 8) |
| Support: the surface goes with its log, the item drops | **Pass** (step 9) |
| Persistence across relog and restart | **Pass** (step 5) |
| No vanilla heavy plate (crafted or placed) drawn empty; worldgen lily pads drawn | **Pass** (step 10) |
| Scraping a soaked hide yields a scraped hide | Pending PAR-FAUNA-17 |

## History

- 2026-11-07: the first run used `minecraft:lily_pad` as the carrier; withdrawn (our worldgen places lily pads, PAR-INFRA-42).
- 2026-11-13, first re-add: the carrier `heavy_weighted_pressure_plate[power=0]` without a hide drew a placed vanilla heavy
  plate empty (`11-heavy-plate-drawn-empty.png`); withdrawn until PAR-INFRA-44 added the hide. The run above replaces
  both; none of their screenshots is reused.
