# PAR-POT-08 verdict: drying bricks, mud bricks and thatch

Date: 2026-11-25. Base commit: 46a8cd6 (the item commit follows it).

**Pending in-game check (container cannot run client).** The headless server and client could not run alongside the other
agents' builds in this container (the cgroup killed the datagen and test JVMs several times with exit 137), so no screenshot
was taken. What was verified instead: datagen output (`DryingBlocksTest`), the lazy rule and the Paper glue on MockBukkit
(`DryingStateTest`, `DryingBricksMockBukkitTest`).

Steps to run (docs/testing/headless.md, one server at a time, bot `Bot`). The item file's step names `silty_loam`, which is not a
TFC soil; the soils are alfisol, andisol, aridisol, entisol, fluvisol, mollisol, oxisol, podzol.

1. `/tfp debug drying fill <x> <y> <z> alfisol 4` on a stone floor: four wet bricks appear (TFC's `wet_4_alfisol` model).
   `ce item give Bot tfp:drying_bricks/alfisol 4` and a right click on a pile adds a brick up to four; sneaking with an empty
   hand takes one.
2. Screenshot (GUI scale 2): the wet pile. `/tfp time add days 2`, wait one second (the clock runs every 20 ticks), screenshot:
   the pile uses `dry_4_alfisol`, and sneak-taking gives `tfp:mud_brick/alfisol`.
3. Rain keeps a pile wet: with rain over the pile `time add days 2` leaves it wet.
4. `tfp:thatch` (`ce item give Bot tfp:thatch 4`): placed cubes with TFC's texture; walking into one slows the player
   (PAR-GATHER-10); it collides (ledger divergence).

Criterion: "Mud bricks dry after mudBricksTicks in clear weather (in-game with time add)": pending steps 1 and 2.
