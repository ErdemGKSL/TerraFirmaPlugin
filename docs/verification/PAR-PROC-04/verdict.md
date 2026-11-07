# PAR-PROC-04 verification: grinding wheat grain in the quern

- Date: 2026-11-07
- Base commit: 8068b05 plus the PAR-PROC-04 change (datagen regenerated, `:dist:shadowJar`)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel and BetterHud as installed by `spikes/client/server.sh`
  (vanilla world, primed and restarted); headless client 1.21.11 as `Bot` (`spikes/client/session.sh`, pack applied after
  3 s). CraftEngine: `Loaded items (2972)`, `Loaded blocks (1396)`, no load issues; feature module `quern` enabled; no
  plugin errors in the log (only PacketEvents' update check, HTTP 403).

## Steps (RCON + `xdotool click 3`, Bot in survival)

1. Stone floor at y 99; `ce item give Bot tfp:quern`; Bot at (3.5, 100, 2.3) pitch 43 right-clicks the floor: the quern is
   placed at (3, 100, 4) from its block item (`01-placed.png`). (`tfp debug place` is player-only, so the block item was used.)
2. `ce item give Bot tfp:handstone`; Bot at (3.5, 100, 3.3) pitch 43 right-clicks the quern top: the handstone goes in
   (`has_handstone=true`, the 12 px carrier outline; handstone display).
3. `ce item give Bot tfp:food/wheat_grain 4`; right-click: the grain goes into the input (`03-grain.png`, the hand is empty
   afterwards).
4. Empty hand, right-click on the handstone: the grind starts; screenshot 1.2 s later (`04-grinding.png`, the handstone
   turned mid-step about its vertical axis).
5. 4 s later (`05-ground.png`): the handstone back at rest, one flour on TFC's first output position (north-west corner),
   three grains left on top.
6. Bot at pitch 62 right-clicks the base's side: `data get entity Bot Inventory[0]` = `craftengine:id
   "tfp:food/wheat_flour"`, count 1, with its food record (`tfp:food`).

## Results

| Criterion | Result |
|---|---|
| Grinding wheat grain gives flour in-game | **Pass**: one wheat grain became one `tfp:food/wheat_flour` in the output after the 90-tick grind, taken by a click on the base |
| Block, handstone and GUI-less interaction | **Pass**: TFC's base model on the selector's carriers, handstone and input drawn by displays, the turn interpolated, every step by right-click only |
