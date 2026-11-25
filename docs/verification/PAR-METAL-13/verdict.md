# PAR-METAL-13 verdict

Date 2026-11-25, base HEAD 9f4261d plus the working tree of this item, Paper 1.21.11 + CraftEngine 26.9.2, client 1.21.11
(1280x720, GUI scale 2, display :99, software rendering, ports 33575 / 8253).

Scene (flat stone floor, built by `tfp debug channel place|putat`): crucible, two channels in a row, a mold table south of the
second channel and a second one east of it, both with an ingot mold (`tfp debug channel putat ... tfp:ceramic/ingot_mold`).
Three copper ingots in the crucible (`tfp debug crucible put`), heated by the debug run to 1479 C (300 mB of copper).

Steps and results:
1. Connections: after a neighbour update the channels join (each draws a connection arm toward its neighbour, the mold tables lose
   the stop cap on the channel side) (`01-top-before-pour.png`). The pale blue strips beside the channels are the daylight
   detector carrier's occlusion (the client culls the top face of the floor under a hitbox-only carrier): pre-existing, the
   bellows shows them too (PAR-HEAT-35 verdict, follow-up PAR-HEAT-36).
2. `tfp debug channel activateat` (the same call a right click makes): PLANNING, then both tables have the crucible as source
   and 2 routes through the first channel; molten metal is drawn in the basin and the arms of both channels, and the channels
   glow (light 10) (`02-top-pouring.png`).
3. About 14 s later each mold holds 100 mB at about 1200 C, the tables have no source, the channels no flow (`with_metal=false`),
   the crucible keeps 100 mB.
4. Within a minute the molds solidify and each table shows its casting (a copper ingot lying on the mold) (`03-castings-gui2.png`);
   the table's output slot is full.
5. Survival right click on a table with an empty hand gave a Copper Ingot (hot, "Bright Red") into the hotbar.
6. A redstone block beside a channel sets `triggered=true`, removing it sets `triggered=false` (the crucible was cold, so no pour).

| criterion | result |
|---|---|
| A crucible fills two molds through channels | PASS (2 x 100 mB, steps 2 and 3) |
| Flow visual along the path for the pour duration | PASS as a substitute (one orange molten texture in basin and arms, not TFC's per-metal fluid texture; ledger) |
| Molds on mold tables fill and give their casting | PASS (steps 3 to 5) |
| Fast pour by sneak (PourFasterPacket) | not here: TFC's packet speeds the pour of the molds in the crucible's own slots, which is PAR-METAL-23 |

Found and fixed during the run: the casting only appeared on the next access of a cooled mold; a one-second watch while the mold is
molten (`PourService.settle`) now makes that access.
