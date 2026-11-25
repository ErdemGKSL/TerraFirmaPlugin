# PAR-METAL-16 verdict

Status: pending in-game check (the container cannot run the client next to the other sessions on this machine: load 9 on 4
cores, one client already running; the datagen run that builds the jar is also blocked by the known unrelated datagen failures
on HEAD).

What is checked without a client (2026-11-25): core `PileVisualTest` (TFC's ingot positions, the display plan), paper
`PileMockBukkitTest` on MockBukkit (new pile, add, column climb, other kind, remove, creative, adventure, support, drops, creative
void, displays one per ingot and one per full layer of one metal) and datagen `PileBlocksTest` (entries, carriers per layer,
display models and item definitions).

To run when a client can run (`in_game_steps` of the item, GUI scale 2):
1. `ce item give Bot tfp:metal/ingot/copper 64` and the same for `tin`, `bronze`, `wrought_iron`; sneak right click the ground
   repeatedly. Look at: rows of four ingots, two rows, each layer turned 90 degrees against the one below, layer height 2 px.
2. `tfp debug pile fill <x> <y> <z> ingot 64 copper,tin,bronze,wrought_iron` and `... double 36 copper,bronze`: a full pile of mixed
   metals, each ingot with its metal's smooth texture, lit (not black) by day and at night.
3. Plain right click removes the top ingot, each one comes back; the last one removes the block. Break the pile with a pickaxe:
   every ingot drops; break the support: the pile pops off.
4. Check the pile outline (selection box) against the drawn height; a double pile at all six heights.
Pass when a 64-ingot pile of mixed metals renders and returns every ingot.
