# PAR-METAL-20 charcoal forge top orientation: verdict

- Date: 2026-11-06
- Code: base `094744d` plus the PAR-METAL-20 change (every lit level's `entity_renderer` item display carries
  `rotation: '0,180,0'`); jar built from that tree after `tools/worktree/share-build.sh` and `generateContent`
- Server: Paper 1.21.11 (`spikes/client/server.sh`, vanilla world), Java 25; CraftEngine, BetterModel, BetterHud and
  PacketEvents from the run-paper cache; pack self-hosted on 127.0.0.1
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), bot `Bot`, HUD hidden (F1)
- Judged by: the main session, from the capture below and the texel classification. The run itself was done in the main
  session (no cheap-model agent tool was available in this run)

## Steps

1. Stone floor at y 200, air above; `tfp time set noon`, `time set 6000`, clear weather.
2. Bot in spectator at (0.5, 203.4, 0.5), yaw 0, pitch 90 (looking straight down at the forge at (0, 201, 0); one top
   texel is 9 screen px). `/tfp debug place tfp:charcoal_forge[heat_level=7]` typed in chat, then `[heat_level=0]`.
3. Filmed the top with `ffmpeg x11grab` at 30 captures/s: 120 captures (4 s) of level 7, 60 captures (2 s) of level 0.
4. Each capture sampled at the 16x16 texel centres of the top and matched by normalised correlation against every frame of
   TFC's `textures/block/devices/charcoal_forge/7.png` (13 frames) or `0.png` under the 8 orientations (4 turns, with and
   without a mirror).

## Results

| criterion | evidence | result |
|---|---|---|
| Cold top (level 0, block model) has TFC's block-model orientation | 60/60 captures: best orientation `rot180` (the up face seen from above with yaw 0, as vanilla and TFC draw it), correlation 0.99952 each; the other 7 orientations at most 0.40 | pass |
| Lit top (level 7, item display of `heat_7`) has the same orientation | 120/120 captures: best orientation `rot180`, worst best correlation 0.99997; the other orientations at most 0.67. Before this change the lit captures matched `rot0` (PAR-METAL-10 capture 14) | pass |
| The lit top still animates with TFC's frames | the 120 captures cover all 9 distinct images of TFC's animation (frames 0 to 8) | pass |
| Scale stays centred | the lit top fills the same 144x144 px screen box as the cold top (no shift after the half turn) | pass |

`01-cold-and-lit-top-vs-tfc.png`: per row (level 0, level 7) the in-game top, TFC's frame 0 turned 180°, TFC's frame 0
unturned. Both in-game tops equal the turned texture.

Server log: no error or warning from TerraFirmaPlugin or CraftEngine apart from the known solid-state reserve notice.

Other `tfp:item/render` users: firepit, grill and pot have no `entity_renderer` yet (PAR-HEAT-06/08/09), the PAR-INFRA-17
rendered cubes draw a uniform cube and `CrackedOverlays` already accounts for the display's half turn, so no follow-up item.
