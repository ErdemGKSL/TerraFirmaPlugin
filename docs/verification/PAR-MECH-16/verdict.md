# PAR-MECH-16 verdict (also PAR-MECH-01 and PAR-MECH-11 in-game steps)

Date 2026-11-25, tree HEAD 14fd4e2 plus a local datagen fix for PAR-MECH-24 (jar built in a scratch worktree), Paper 1.21.11 build 132,
vanilla 1.21.11 client (Xvfb), CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0, TFC world, Bot creative.

Finding first: with the committed content, right-clicking with the motor/axle item throws `ClassCastException: Direction$Axis$1 cannot be
cast to String` in CraftEngine's HardcodedPropertyBlockBehavior and places nothing (axis is written as a string property). Recorded as
PAR-MECH-24 with the verified fix; the run below used the fixed content.

| Step | Result | Evidence |
|---|---|---|
| motor (axis x) + 5 oak axles + oak gearbox (hammer west and south faces) + encased axle south + axle beyond, placed by item and hammered by click; 8 right-clicks on the motor | PASS: gearbox state west=true,south=true after the hammer clicks | line-overview.png |
| `/tfp debug rotation`: every block 30.00 rpm | PASS: 9 blocks, hand east on the x line and south after the gearbox, all 30.00 rpm | debug-rotation-30rpm.png |
| a powered clutch cuts the line, unpowered joins it | PASS: clutch in the line = 9 blocks at 30 rpm; redstone block beside it: network -, 0.00 rpm, 1 block; removed: 9 blocks again | clutch-powered-disconnected.png |
| restart with the line standing: debug without touching shows the network | PASS: after a server restart the first debug (aimed at the far axle) reads 9 blocks, 30.00 rpm | after-restart-debug.png |
| sixth axle in the line breaks after 4 ticks | NOT RUN (an extra axle at the end of the z line stayed and joined the network: 10 blocks; the 6-axle straight run was not built) | |
| feel checklist (cracks, sounds, light, outline, /fill, TNT, pick-block, distance) | NOT RUN | |
| rotation.stress itest on Paper and Folia with spikes/run-matrix.sh | NOT RUN | |

Verdict: the in-game placement, hammering, network and restart parts pass; the item stays open for the sixth axle, the feel checklist and the
itest (Pending in-game check). Placing by item needs PAR-MECH-24 first.
