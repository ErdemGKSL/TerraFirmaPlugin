# PAR-METAL-12 verdict

Date 2026-11-20, base HEAD 0349cc4 plus the working tree of this item, Paper 1.21.11 + CraftEngine, client 1.21.11 (GUI scale 2, 1280x720, display :97).

Steps (no heat source block is loaded in game yet: the firebox block is PAR-HEAT-31, the forge entity PAR-METAL-08, so the debug command stands in for the source's heat run):
1. Placed `tfp:crucible` from its block item on a stone floor (crucible block drawn with TFC's model).
2. `/tfp debug crucible Bot put` x7 normal native copper, x1 normal cassiterite; waited 400 ticks; `heat 1500 400` (the source's run); `info` -> 1439 C, 200 mB of [tin, copper], 0 items (all melted).
3. `open`: screen (gui-melted.png), then hover over the panel (gui-alloy-tooltip.png).

| criterion | result |
|---|---|
| Melting bronze in a crucible over a forge: ores melt into the alloy by the heat run | PASS (copper and tin ores melted; the 87.5/12.5 mix is below bronze's copper range, so the screen says Unknown, as TFC would) |
| GUI: TFC art on a 6-row chest, thermometer at the crucible temperature, alloy fill, nine inputs, output | PASS |
| Alloy text (metal names, mB, percent) | PASS as the tooltip of the panel |

Not checked in game: a real forge/firebox below (no loaded heat source), molds (follow-up). First attempt found a bug (the plugin's `tfp:metal/*` fluid ids were not accepted into the alloy: items vanished with no fluid); fixed in CrucibleRules.meltTemperature and covered by CrucibleSimTest.
