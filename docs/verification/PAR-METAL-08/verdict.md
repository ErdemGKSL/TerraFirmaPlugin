# PAR-METAL-08 charcoal forge block entity: in-game verdict

Date 2026-11-25. HEAD at the time of the run: the jar was built from the working tree after commit 67f7b17 (device, module,
fire lightables, MockBukkit tests, `forge.continuous` case). Headless Paper 1.21.11 (build 132), CraftEngine 26.9.2,
BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT, PacketEvents 2.14.0, 1.21.11 client under Xvfb :101, pack self-hosted on
127.0.0.1:8273, server port 35575, bot `Bot` in creative (clicks and chat typed with xdotool; replies read from the chat
line in the screenshots). GUI scale default; the check has no HUD or GUI criterion (the forge screen is PAR-METAL-09).

Scene: a stone floor at y 70, a charcoal pile `tfp:charcoal_pile[layers=7]` at (691, 71, 273) placed with
`/tfp debug place`, stone on its four sides (insulation) and open sky above.

## Finding that was fixed during the run

First run (screenshots not kept): the pile lit with flint and pyrite became a vanilla fire block and, after the jar was
rebuilt with the module, a forge without a device record (`/tfp debug device`: "no device block entity"). Cause: the forge's
CraftEngine entry (PAR-METAL-07's `DeviceSpecs.charcoalForge`) did not carry the `tfp:device` behaviour. Fixed in
`DeviceSpecs` (`.deviceBehaviour()`); the datagen run that regenerates the YAML was killed by the container's memory limit
(other agents share it), so for this run the one `behavior: {type: tfp:device}` block was added by hand to the server's copy
of `charcoal_forge.yml`, exactly what the generator now writes. `DeviceSpecParityTest` builds its expected text from the
same `DeviceSpecs.charcoalForge`, so it follows the change.

## Steps and results

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Place a 7 layer pile with stone on four sides | pile shown, cold | `01-pile-unlit-insulated.png` |
| 2 | Strong start with flint and pyrite on the pile (a few clicks: the tool's chance) | the pile becomes the forge, lit, the animated heat top shows | `02-lit-from-pile-first-burn.png` |
| 3 | `/tfp debug charcoalforge Bot info` some seconds later | `lit false, 178 C, burn 0 ticks, heat level 0`: the 200 tick first burn at 500 C ran out with no fuel, the forge went dark by itself (the callback at the burn-out tick) and still held 178 C | `03-burnt-out-after-200-ticks-info.png` |
| 4 | Right click with charcoal twice (fuel slots), with a copper ingot (input slot 5), then flint and pyrite | `lit true, 160 C, burn 1640 ticks, heat level 1, inputs: 5 paper 112 C`: fuel taken from slot 0, the ingot already warm | `04-fuel-and-ingot-lit-info.png` |
| 5 | About 55 s later, no click on the forge | `lit true, 1350 C, burn 280 ticks, heat level 6; inputs: 5 paper 1064 C`: the top shows the level 6 glow, the ingot (heat capacity above 2) follows the forge | `05-ingot-1064C-forge-1350C-level6.png` |
| 6 | Another half minute, again no click | `1350 C, burn 1440 ticks (the second charcoal was taken), heat level 6; inputs: 5 paper 1350 C`: the ingot is at the forge temperature | `06-ingot-reaches-1350C-no-clicks.png` |

("paper" is the CraftEngine copper ingot's base material in `info`.)

## Criteria

- An ingot left in the forge reaches the forge temperature without clicks: pass (steps 5 and 6). The pure rule behind it is
  tested for equality with stepping (`CharcoalForgeSimTest`), the MockBukkit test `anIngotLeftInTheForgeReachesTheForgeTemperatureWithoutClicks`
  and the self-test case `forge.continuous` (written, compiled; not run here: a whole server self-test did not fit in the
  container's memory next to the other agents' servers).
- Creation from a charcoal pile with a valid structure, lighting, the heat level following the temperature, the burn-out
  showing on time: pass (steps 2 to 6).
- Not checked in game: the structure being refused (open side) and a crucible above the forge (covered by the MockBukkit and
  core tests), rain, bellows air.

Screenshots are 1280x720 palette PNGs (about 120 KB each).
