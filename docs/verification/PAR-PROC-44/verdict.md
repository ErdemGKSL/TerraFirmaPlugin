# PAR-PROC-44 verification: barrel wiring in game (PAR-PROC-35's checks)

Run 2026-11-18 by the worker session (no subagent tool was available to delegate the run; the same session judged every
shot). HEAD `fd00cbb` (no code change in this item).

- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents
  (from `spikes/client/server.sh`), `TFP_WORLD=1`, fresh run folder. Priming start, waited for `Generated resource pack`,
  stop, worlds deleted, restart; the restart rebuilt the pack itself (PAR-INFRA-55: `Rebuilding the CraftEngine resource
  pack ... serverside-blocks was raised`, `Generated resource pack in 5394ms`) before the join. No `Found N issue(s)`, no
  SEVERE line, no plugin exception.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), 1280 x 720, player `Bot`, survival, pack applied 3 s
  after the join. GUI scale 2 for the first session (`options.txt guiScale:2`), then a rejoin with
  `MC_OPTIONS="guiScale:3"`.
- Site: smooth quartz floor at y 120 around (-2192, -1714), open sky, noon, clear vanilla weather. Commands typed in the
  client chat (the debug commands need a player sender), RCON for setup and inventory queries.

## Steps

1. `op Bot`; glass at (-2192, 121, -1715); Bot at (-2191.5, 121, -1712.5) yaw 180 pitch 29;
   `/tfp debug place tfp:wood/barrel/oak` -> `Placed tfp:wood/barrel/oak at world -2192 121 -1715.`
2. `give Bot minecraft:water_bucket 5` (slots 0-4); hotbar keys 1..5, one real right click (xdotool) each;
   `/tfp debug barrel` -> `fluid: 5000 mB minecraft:water / 10000 mB`. RCON: `clear Bot minecraft:bucket 0` -> 5,
   `clear Bot minecraft:water_bucket 0` -> none; `fill ... y 121 and y 122 ... air replace minecraft:water` over the
   17 x 17 site -> `No blocks were filled` (no world water).
3. Hotbar slot 6 (empty hand), right click: the barrel screen opens from the block, tank half full (GUI scale 2); the same
   after the rejoin at GUI scale 3.
4. Escape; Bot on a barrier beside the barrel at (-2191.5, 122, -1713.3) pitch 62, HUD hidden: the tinted water plane at
   half height inside the barrel. Entity query: one `item_display` (`tfp:render/barrel_fluid`) at the block's bottom
   centre, `translation [0, 0.5078125, 0]` = TFC's 0.140625 + 0.734375 x 0.5.
5. Back at the first spot, slot 1 (empty bucket), one right click: selected item `minecraft:water_bucket`, 4 empty buckets
   and 1 water bucket in the inventory, no world water; `/tfp debug barrel` -> `4000 mB`.
6. `/tfp debug barrel fill 0`, `/tfp debug barrel rain true` (clock 9915, `raining above (forced)`); 20 s later
   `/tfp debug barrel` -> `115 mB minecraft:water`, clock 10375: 460 ticks = 115 checks of 4 ticks, exactly TFC's
   1 mB per 4 ticks (100 mB per 400). `/tfp debug barrel rain false`.

## Results

| Criterion | Evidence | Result |
|---|---|---|
| Five water buckets give 5000 mB, five empty buckets back, no water placed in the world | `01-five-buckets.png`, RCON counts | **Pass** |
| The GUI opens from the block showing the half tank (GUI scale 2 and 3) | `02-gui2.png`, `02-gui3.png` | **Pass** |
| The placed barrel shows the fluid surface at half height | `03-surface.png` (2x), entity translation 0.5078 | **Pass** |
| An empty bucket takes 1000 mB back (water bucket in hand, 4000 mB, no world water) | `04-bucket-out.png` | **Pass** |
| Rain adds 100 mB per 400 ticks | `05-rain.png` (115 mB over 460 ticks) | **Pass** |

## Notes (no criterion affected)

- The rain switch is `/tfp debug barrel rain true|false` (Brigadier bool); the item steps and the ledger say `on|off`,
  which the command rejects (`Invalid boolean`). The run used `true` / `false`.
- `rain false` settles the barrel with the rain now off, so the 160 ticks between the last rainy read (10375) and the
  switch (10535) were not credited (115 mB stayed 115). That is the recorded both-ends divergence of `BarrelRain`
  (ledger), here caused by the debug switch, not a new defect.
- The crops were also copied to `docs/verification/PAR-PROC-35/` so that item's evidence path resolves.
