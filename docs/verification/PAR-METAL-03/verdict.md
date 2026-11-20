# PAR-METAL-03 verification: anvil blocks with items shown on top

- Date: 2026-11-19/20. Code under test: the working tree on top of 1c24e7a (jar built with `:dist:shadowJar` after
  `generateContent`; the commit that carries this verdict holds the same code).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449 (`spikes/client/server.sh`,
  `TFP_WORLD=1`, primed and restarted, ports 29575 / 8213). No CraftEngine issues for the 16 anvil blocks; feature module `anvil`
  enabled. Client: headless 1.21.11 as `Bot` (survival), GUI scale 2, display :95.

## Steps and results (RCON + xdotool, sneaking = shift held)

1. `ce item give Bot tfp:metal/anvil/copper`, right click the floor: the anvil is placed, TFC's copper anvil model, horn to
   the west for a player looking south (TFC: clockwise of the look direction) (`01-placed.png`). PASS.
2. Sneak + right click with a copper ingot: the ingot lies on top (`02-ingot-on-anvil.png`), the stack loses one. PASS
   (done criterion).
3. Steel anvil, two ingots and flux (sneaking): both ingots and the flux are drawn on the top face (`03-two-ingots-flux.png`);
   a third ingot is refused (both inputs full), the flux slot takes the stack (to 64). PASS.
4. Empty hand, sneaking, right click: one ingot (the main input) comes into the hand, the second stays
   (`06-one-extracted.png`); an earlier run showed both taken (the other hand's event), fixed by `AnvilClicks`. PASS.
5. Break the anvil: the anvil item and the contents drop (ingot, flux), the item displays are gone. PASS.
6. Server restart: the anvil, its facing and the flux display are still there (`05-after-restart.png`). PASS.
7. Rock anvil: the `tfp:rock/anvil/granite` item placed, an ingot sneak-placed on it sits on the higher top face
   (`10-ingot-on-rock-anvil.png`). PASS.
8. Hammer conversion: a copper hammer right-clicked on the top of a raw granite block turns it into the granite anvil
   (confirmed by placing an ingot on it, `11-converted-check.png`). PASS.

## Notes

- The selection box and collision of the metal anvil are the carrier's (soul campfire, 16 x 7 x 16), lower and wider than TFC's
  (ledger divergence). The rock anvil's (farmland, 15 px) is 1 px taller than TFC's 14 px; both are drawn by the
  display.
- Not covered here: the GUI (PAR-METAL-04, PAR-METAL-05), welding (PAR-METAL-06).
