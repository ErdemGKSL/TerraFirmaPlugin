# PAR-METAL-19 verdict: metal lamps (fuel, light, lit state)

Date: 2026-11-25. Server: Paper 1.21.11 (headless recipe of docs/testing/headless.md), CraftEngine 26.9.2.

## Result: Pending in-game check (container cannot run client)

The container's memory (16 GB shared with other agents' Gradle runs) does not leave room for the client next to the server
(PAR-METAL-18 saw the memory cgroup kill the server JVM in the same situation), so no screenshot exists and the visual
criteria are not judged. A server-only run was made (Paper 1.21.11, ports 57575 / 8493): `lamp` is in the `Feature modules:` line
(the last entry), the second start loads `configuration/lamp/lamp.yml` in CraftEngine with no issue (the "Cannot allocate enough
real block states" warnings for the lamp file appear only on the priming start, as documented in headless.md section 3) and there
is no lamp-related warning or error; the console cannot place a CraftEngine block (`ce` needs a player), so no lamp was placed.
What was verified without a client is in the item's tests (core `LampRulesTest`,
`LampFuelTsvGoldenTest`; paper `LampDeviceMockBukkitTest`; datagen `LampBlocksTest`) and in the notes below.

## Steps still to run (in_game_steps of the item)

1. `ce item give Bot tfp:metal/lamp/copper` (the item shows TFC's lamp sprite and stacks to 1).
2. Place it on a floor (looking level or down) and under a ceiling (looking up): the standing lamp and the hanging lamp with
   its chain are drawn, the outline matches `LampBlock.SHAPE` / `HANGING_SHAPE`.
3. Right click with a jug of olive oil: the oil moves into the lamp (bucket sound). Light it with a fire starter or flint and
   pyrite: the lamp model switches to the lit texture and the block emits light 15 (check the light at night).
4. `tfp time add days 10` then screenshot: still lit (a full lamp burns 75 days); with a nearly empty lamp the same command
   puts the lamp out within a second.
5. Sneak click with an empty hand puts it out; break it, the dropped lamp keeps the fuel; place it again, still fuelled and unlit.
6. Screenshots at GUI scale 2 and 3 (8x crops of the lamp) in this folder.

Criterion "A lit lamp goes out when its fuel runs out (time add)": covered by `LampDeviceMockBukkitTest`
(`aLitLampBurnsLazilyAndGoesOutWhenAJumpOfTimePassesItsFuel`, `aCallbackAfterATimeJumpShowsTheUnlitState`); the visible
part waits for step 4.
