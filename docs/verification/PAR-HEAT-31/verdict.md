# PAR-HEAT-31 verdict

Date: 2026-11-25. Base commit: f064c546 (plus the uncommitted PAR-HEAT-31 / 37 changes of this run). Server: Paper 1.21.11 (run-task jar cache),
CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0, TerraFirmaPlugin jar built from the working tree.
Client: the official 1.21.11 client under Xvfb (`spikes/client/session.sh`, CraftEngine self-host on 127.0.0.1, pack applied 3 s after the join),
bot `Bot` (op, creative unless stated), GUI scale 2 for the first run and 3 for the second (`MC_OPTIONS=guiScale:3`). Judged by the main session
(not a cheap model) on the crops in this folder. The headless runs were driven directly (xdotool + rcon) because the shared container
OOM-killed the server three times when other sessions built at the same time.

## Steps run and criteria

| Step / criterion | Result | Evidence |
|---|---|---|
| `tfp debug place tfp:firebox[lit=true]` (on a stone block): TFC's `firebox_on` model, light, copper box | PASS: the lit cube column with the animated side bars | `../PAR-HEAT-12/firebox_block_lit.png` |
| Right-click: 16 slots on chest columns 3-6, thermometer on the left, status row under the slots, GUI scale 2 and 3 | PASS: frames line up at both scales | `../PAR-HEAT-12/firebox_screen_scale2_4coal.png`, `firebox_screen_scale3_4coal.png` |
| 4 coal in (shift-click x4): one item per slot, cascade to the top-left slots | PASS | same crops |
| Lit with a firestarter (sneak + hold); thermometer marker rises and one coal burns; status row tooltip | PASS: tooltip `No heatable area detected` (no kiln above); the thermometer tooltip names the heat level | `../PAR-HEAT-12/firebox_status_tooltip_scale2.png` |
| Break in survival: the firebox drops (and its coal) | PASS: the block item and the 3 unburnt coal dropped. The copper break sound is checked in the generated entry (`device_sound_break: minecraft:block.copper.break`, `FireboxBlockTest`); the headless client cannot record sound | `../PAR-HEAT-12/firebox_broken_drops.png` |
| `tfp:stove[lit=false,facing=north]`: TFC's stove model, facing north (front toward the player) | PASS | `stove_unlit.png` |
| Two logs in (right click), lit with the firestarter | PASS: TFC's `stove_lit` model with the fire | `stove_lit.png` |
| Right-click with a ceramic pot: `tfp:stove_pot` | PASS: the pot sits on the lit stove (the pot item is kept in creative) | `stove_pot_lit.png` |
| Sneak + right-click with an empty hand: the pot comes back, the stove again | PASS: pot count 2 to 3, the lit stove model is back | `stove_after_pot_taken_back.png` |
| Right-click the stove with a wrought iron grill: nothing happens | PASS at GUI scale 3 after the fix (the stove's screen opener skips the grill); the first run, with only the listener, opened the firepit screen (CraftEngine's handler does not honour the cancelled Bukkit event here) | `stove_grill_refused.png` |
| Stove box 1,0,1 to 15,12,15 | APPROXIMATE: the selection box is the selector's carrier (a cactus state), about 3 px taller (ledger divergence) | the outline in `stove_unlit.png` |

Not checked in game: the stove's hot floor (the firepit's world rules cover the stove ids since this item).
