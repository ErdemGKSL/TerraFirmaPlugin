# PAR-HEAT-37 verdict

Date: 2026-11-25. Base commit: f064c546 (plus the uncommitted PAR-HEAT-31 / 37 changes of this run). Server: Paper 1.21.11 (run-task jar cache),
CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0, TerraFirmaPlugin jar built from the working tree.
Client: the official 1.21.11 client under Xvfb (`spikes/client/session.sh`, CraftEngine self-host on 127.0.0.1, pack applied 3 s after the join),
bot `Bot` (op, creative unless stated), GUI scale 2 for the first run and 3 for the second (`MC_OPTIONS=guiScale:3`). Judged by the main session
(not a cheap model) on the crops in this folder. The headless runs were driven directly (xdotool + rcon) because the shared container
OOM-killed the server three times when other sessions built at the same time.

## Steps run and criteria

| Step / criterion | Result | Evidence |
|---|---|---|
| `tfp debug place tfp:pot[lit=false]`: TFC's pot over the stand and fire, as an item display on a hitbox-only carrier | PASS: `firepit_pot` over `firepit_unlit` (and over `firepit_lit_low` when lit) | `pot_block_models.png`, `pot_block_lit.png` |
| Fuel (logs), fire, water, three vegetables, open the screen at GUI scale 2 and 3 | PASS: the title reads `Pot` (no literal `<image>` text), the four fuel and five input frames sit on the chest slots, thermometer, the three flames and the bubble art | `pot_screen_scale2.png`, `pot_screen_scale3.png` |
| Thermometer moves with the pot temperature | PASS: the marker is at different heights across the shots (fire just lit, later) | the screenshots of the two scales |
| Wait (`tfp time add hours 1`), the status button reads the soup | PASS: tooltip `2x Filling Soup` | `pot_status_filling_soup_scale2.png` |
| Take the soup with a bowl; the food tooltip shows the mixed nutrition | PASS: `Filling Soup`, Grain 0.1, Vegetables 2.4, saturation 20 %, water 20 %, `1 x Potato / Cabbage / Carrot`, expiry date | `soup_item_tooltip.png` |
| Fluid band on the screen | The pot icon shows the contents as a translucent band over the pot (grey-brown for the soup); TFC's water blue was not seen (the water bucket was accepted only once, in the first run) | `pot_screen_scale2.png` |
| Bubbles move while boiling | NOT CAPTURED: the boil window is about 60 s and the shared container OOM-killed the server twice during the capture. The bubble frames are `PotScreenLayout.boilingFrame` (unit-tested); three shots 1 s apart outside the boil window were identical, as expected. Follow-up PAR-HEAT-38 | |
