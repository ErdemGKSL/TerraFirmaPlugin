# PAR-PROC-05 / PAR-PROC-29 verification: scraping unrefined paper on a log

- Date: 2026-11-07
- Code under test: the PAR-PROC-29 change on top of commit 2ef7e11 (the jar built from the worktree with `:dist:shadowJar`
  after `generateContent`; the commit that carries this verdict is that change).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel and BetterHud as installed by `spikes/client/server.sh`
  with `TFP_WORLD=1`. Primed (`RESTART REQUIRED`), world deleted, restarted: `Loaded blocks (1397)` (one more than the
  quern run: `tfp:scraping`), no CraftEngine issues, feature module `scraping` enabled, no plugin errors (only
  PacketEvents' update check, HTTP 403).
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`), pack applied after 3 s. First session at GUI scale 2,
  second session (after the restart) at GUI scale 3.
- The run was done by the worker session itself (no cheap-model agent was available in it); the screenshots were judged
  here.

## Carrier

`tfp:scraping` has one appearance, `minecraft:lily_pad` with `transparent: true` (the selector's best free carrier for TFC's
16 x 1 x 16 plate, score 0.55: every carpet is kept for players, `reports/hitbox-carriers-*.md`). The outline the client
draws is the lily pad's 14 x 1.5 x 14 box (visible in `01`), the surface itself is the item display.

Withdrawn after review (PAR-PROC-29 repair): `minecraft:lily_pad` is also the vanilla placeholder our worldgen places for
TFC's floating plants (`PlantBlocks` FLOATING/FLOATING_FRESH), and a transparent appearance draws every real lily pad
empty; this run (a log on a stone floor, no water) could not see that. The scraping entry is not generated until
PAR-INFRA-42 corrects the carrier policy; then this section, the outline shot `01` and the ledger's outline divergence row
are redone on the new carrier, together with one look at a pond (floating plants still drawn). The scraping behaviour
checked below does not depend on the carrier.

## Steps (RCON + `xdotool`, Bot in survival, not op)

1. Stone floor at y 119, `minecraft:oak_log` at (1536, 120, -2541) (in `#minecraft:logs`, so in `tfc:scraping_surface`;
   TFC wood logs are not CraftEngine blocks yet). `ce item give Bot tfp:unrefined_paper 4`, `ce item give Bot
   tfp:metal/knife/copper`.
2. Right-click the log's top with the unrefined paper: the surface is placed, one paper goes in (4 -> 3), the 1 px surface
   shows the unrefined paper texture (`01-unrefined-paper-placed.png`, GUI 2, about 24 degrees).
   Note: the very first right-click after the join did nothing (the client had not been clicked into yet); the same click
   repeated placed the surface.
3. Bot on a 2-block pillar north of the log (feet 1536.5, 122, -2542.5), yaw/pitch computed per cell centre, knife
   right-clicks. Cell 0 (north-west: min x, min z) first: that corner tile turns to paper, knife damage 1
   (`02-one-cell-nw.png`, Bot looking south, so north-west is bottom right). Orientation from the south in spectator, north
   at the top: the scraped tile is the top-left (north-west) corner (`03-orientation-north-up.png`).
4. Cells 1 to 7: the north half is paper, knife damage 8 (`04-eight-cells.png`, `05-eight-cells-8x.png` 8x crop of the
   boundary: input texture south, output north, sharp 4 px tile edges).
5. Client killed, server stopped through `stop.sh`, server restarted, Bot rejoined (GUI 3): the display is back with the
   same eight cells (`06-after-restart-gui3.png`).
6. Cells 8 to 15: the whole surface is paper, knife damage 16 (`07-sixteen-cells-gui3.png`). Left-click the surface:
   it breaks at once (hardness 0.2), one `minecraft:paper` item drops, the display is removed
   (`08-broken-paper-dropped-gui3.png`).
7. Wax: a fresh surface (paper 3 -> 2), cells 0, 5, 10, 15 scraped (damage 20), then right-click with
   `minecraft:honeycomb`, `minecraft:red_dye`, `minecraft:blue_dye` (each consumed): unscraped tiles red, the four scraped
   tiles blue (`09-waxed-red-blue.png`, dusk light). A knife click on cell 3 afterwards changes nothing and costs no
   durability (damage stays 20).
8. Break the log under that surface by hand: the log drops, the surface and its display go, and the held unrefined paper
   drops as an item (`10-log-broken-surface-gone.png`).

Not run: the hide steps (pending PAR-FAUNA-17, see the item's pending line); a TFC wood log (no wood CE blocks yet).

## Verdict

| Criterion | Result |
|---|---|
| Scraping unrefined paper on a log yields paper in game, the surface shows each scraped tile | **Pass** (steps 2 to 6) |
| Orientation: the north-west cell is the north-west tile | **Pass** (step 3) |
| Wax and dyes, knife blocked on a waxed surface | **Pass** (step 7) |
| Support: the surface goes with its log, the item drops | **Pass** (step 8) |
| Persistence across relog and restart | **Pass** (step 5) |
| Scraping a soaked hide yields a scraped hide | Pending PAR-FAUNA-17 |

## Re-add attempt 2026-11-13 (not committed; PAR-PROC-29 blocked on PAR-INFRA-44)

After PAR-INFRA-42 the selector's carrier is `minecraft:heavy_weighted_pressure_plate[power=0]` (vanilla shape data: selection
14 x 1 x 14 inset 1 px, no collision). The re-add (run line, regenerated `scraping.yml`) was built on top of 7976702 and run on
Paper 1.21.11 build 132, CraftEngine 26.9.2, headless 1.21.11 client (GUI 2, then GUI 3 after a client kill and server
restart). All non-hide steps behaved as in the run above. It is withdrawn again because the carrier is not free: TFC ships a
crafting recipe for the heavy weighted pressure plate (two wrought-iron ingots), and a placed vanilla heavy plate at power=0
is drawn empty by the transparent appearance (`11-heavy-plate-drawn-empty.png`: a `setblock` heavy plate in front of the
gold light plate on the left is invisible). Follow-up: PAR-INFRA-44. The screenshots of this attempt are not kept (the
entry is not shipped); the verdict above stays the reference until the re-add on the final carrier.
