# PAR-POT-06 verification

Status: **pending in-game check (container cannot run client)**.

Date: 2026-11-25. Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT, ports 50575 / 8423, display :116.

## What ran

- The jar built from this commit's tree started cleanly (`Done (148.794s)`), with the `largevessel` feature module listed among the enabled modules and no CraftEngine issue reported for `configuration/largevessel/large_vessel.yml` (the 17 blocks, the 34 render items, the block item models and the `heavy_core[waterlogged=false]` carrier mapping all loaded).
- The headless client did not reach the world: with the shared machine at 14 GB of 16 GB used, the client stalled after its texture atlases (13 minutes, no join) and the server was then killed by the kernel. No screenshot exists.

## Steps still to run (from the item's `in_game_steps`)

1. `ce item give Bot tfp:ceramic/large_vessel`; place it; look at the placed block (it faces the way the player looked).
2. Put meat in the nine-slot screen, click the seal button (or sneak-click the block): the button frame turns to the sealed sprite, the slots lock, the block model changes to the sealed one.
3. Reopen and hover the meat: the tooltip shows the Preserved trait and a later expiry.
4. Hitbox: the outline is the `heavy_core` 8x8x8 box, open and sealed, plain and one glazed colour; break it: stone sounds, the vessel drops with its contents.

The criteria are covered server-side by `LargeVesselMockBukkitTest` (seal applies and removes `preserved` with the creation date `LargeVesselRules.sealFood` gives).
