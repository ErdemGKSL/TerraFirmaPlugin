# PAR-PROC-04 verification: grinding wheat grain in the quern

- Date: 2026-11-07
- Code under test: commit 93248fc (the jar built from it with `:dist:shadowJar`). The commit that carries this verdict adds
  only a core test, docs and these captures, so the plugin code is the same.
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, as installed by
  `spikes/client/server.sh` with `TFP_WORLD=1` (TFC world). Primed and restarted: the priming start logged `RESTART
  REQUIRED`; the restart did not, CraftEngine `misc.delay-configuration-load: false`, `Loaded items (2972)`, `Loaded blocks
  (1396)`, no CraftEngine issues, feature module `quern` enabled, no plugin errors (only PacketEvents' update check, HTTP
  403).
- Client: headless 1.21.11 as `Bot` (`spikes/client/session.sh`, `MC_OPTIONS="guiScale:2"`, pack applied after 3 s). All
  captures at GUI scale 2, cropped to the quern and the hotbar.

## Steps (RCON + `xdotool click 3`, Bot in survival, not op)

1. Stone floor at y 99 (`execute in minecraft:overworld run fill ...`); `ce item give Bot tfp:quern`; Bot at (3.5, 100,
   2.3) pitch 43 right-clicks the floor: the quern is placed at (3, 100, 4) from its block item.
2. Bot at (3.5, 100, 3.3) yaw 0 pitch 43 (crosshair on the base top, inside x/z 3..13 px). `ce item give Bot
   tfp:food/wheat_grain 4`, right-click with the grain and no handstone: nothing happens, the grain stays in hand (count
   4), as in TFC (`getPlayerSelection` gives BASE without a handstone unless a handstone is held; BASE only takes the
   output) (`01-no-handstone-grain-rejected.png`).
3. `ce item give Bot tfp:handstone`, select its hotbar slot, right-click the base top: the handstone mounts
   (`has_handstone=true`, handstone and handle drawn on top, the hand is empty) (`02-handstone-mounted.png`).
4. Select the grain, right-click: the grain goes into the input, drawn in the feed hole (`03-grain-inserted.png`).
5. Empty hand, right-click on the handstone's north side (TFC HANDSTONE place, not the centre feed hole): the grind starts;
   1 s later the handstone is turned mid-step (`04-grinding-1s.png`).
6. 5 s later (after TFC's 90 ticks): the handstone at rest, one wheat flour at TFC's first output position (north-west
   corner, bottom right in the view facing south), the grain on top one fewer (`05-ground-5s.png`).
7. Pitch 62, right-click the base's side: `data get entity Bot Inventory[0]` = `item_model "tfp:food/wheat_flour"`, count
   1. Then pitch 32, sneaking, empty hand, right-click the handstone: the handstone and the 3 grains come back;
   `Inventory[1].components."minecraft:damage"` = 1 of `max_damage` 250 (TFC `Helpers.damageItem` once per grind)
   (`06-handstone-damaged-out.png`, durability bar on the handstone).
8. Same steps again in creative: the handstone mounts. Same steps on an unprimed first-run server (degraded mode, `RESTART
   REQUIRED` in the log): the handstone and the grain also go in, so the degraded mode does not explain the earlier
   failed run.

## Why the 2026-11-07 run saw nothing

- Its `03-handstone-right-click` capture shows the crosshair on the **side** of the quern base, from about two blocks
  away. TFC gives BASE for that hit (outside `HANDSTONE_AABB`, which starts at the base top), and BASE only takes the
  output, so TFC does nothing there either. The handstone mounts only from a click on the base top inside x/z 3..13 px.
  The grain was then refused because no handstone was mounted (step 2 above, TFC behaviour). Core test
  `QuernAimTest.aHeldHandstoneMountsOnlyFromTheBaseTop` pins both rays. TFC's red selection box, which would show the
  difference, is a recorded divergence (ledger, `drawHighlight` row).
- `/tfp debug place tfp:quern` answered "Incorrect argument for command" because Bot was not op: the `debug` node
  requires the admin permission, so brigadier does not offer it to the player. As op the same command places the quern
  (`07-debug-place-as-op.png`: "Placed tfp:quern at world 6 99 4"; `08-debug-place-not-op.png`: the error after
  `deop Bot`). The parser accepts namespaced ids; no defect. Note: it places at the targeted block (replacing the floor
  block), so the steps use the block item.

## Results

| Criterion | Result |
|---|---|
| Grinding wheat grain gives flour in-game | **Pass**: handstone mounted, one wheat grain became one `tfp:food/wheat_flour` after the 90-tick grind, taken by a click on the base; the handstone took 1 damage |
| Block, handstone and GUI-less interaction | **Pass**: every step by right-click only; input refused without a handstone, as TFC |
