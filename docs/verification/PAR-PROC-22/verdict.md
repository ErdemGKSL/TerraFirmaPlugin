# PAR-PROC-22 verification (run by PAR-PROC-33): basket takes small items, refuses a log, bundle tooltip

- Date: 2026-11-13
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at f97ef23 (no code change; this commit adds the evidence).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`, CraftEngine pack self-hosted on 127.0.0.1. No warning or
  error from our plugin in the log (only PacketEvents' update check, HTTP 403 from the sandbox).
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then a rejoin with `MC_OPTIONS="guiScale:3"`; the pack
  applied 3 s after each join.
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. A hollow snow_block room with two glowstone lamps around Bot; `gamemode survival Bot`, `weather clear`, `time set noon`.
2. `clear Bot`, `ce item give Bot tfp:basket`, `give Bot minecraft:stick 16`, `give Bot minecraft:stone 16`,
   `give Bot minecraft:oak_log 4` (hotbar slots 1 to 4).
3. `E`; left-click the sticks, left-click the basket (1.21.4+ bundle insert); the same with the stone; hover the basket.
4. Left-click the oak logs, left-click the basket; screenshot with the logs on the cursor; put the logs back and hover the
   basket again.
5. RCON `data get entity Bot Inventory[0].components."minecraft:bundle_contents"` after each round:
   `[{id: "minecraft:stone", count: 16}, {id: "minecraft:stick", count: 16}]`, and the logs back in slot 3 with count 4.
6. Steps 2 to 5 again at GUI scale 3.

## Evidence

- `01-stick-stone-in-gui2.png`: the basket tooltip at GUI scale 2: name "Basket", 16 stone and 16 stick, fill bar at one
  half (16/64 + 16/64, the vanilla bundle weight of 64-stacks), TFC size line "Very Heavy / Normal".
- `02-log-refused-gui2.png`: the 4 oak logs stay on the cursor over the basket slot; the tooltip still lists only stone
  and stick.
- `03-after-refusal-gui2.png`: logs put back, basket contents unchanged.
- `04-stick-stone-in-gui3.png`, `05-log-refused-gui3.png`: the same at GUI scale 3.
- `06-basket-icon-gui3-4x.png`: the basket icon in the hotbar at GUI scale 3, and `07-tfc-basket-texture-12x.png`: TFC's
  `textures/item/basket.png` at c3b4d9536; pixel for pixel the same woven basket.

## Criteria

| Criterion | Result |
|---|---|
| The basket shows TFC's texture and name | Pass (06 vs 07; "Basket" in 01, 04) |
| Stick and stone go in and show in its bundle tooltip, GUI scale 2 and 3 | Pass (01, 04; RCON bundle_contents) |
| The oak log is refused (stays on the cursor, contents unchanged) | Pass (02, 03, 05; RCON bundle_contents and slot 3 count 4) |

The tooltip is the 1.21.4+ vanilla bundle tooltip (grid, fill bar), not TFC 1.21.1's list; recorded in the ledger's
Divergences table by PAR-PROC-22.
