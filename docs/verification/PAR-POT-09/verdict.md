# PAR-POT-09 verdict: wooden and ceramic bowl blocks

Date: 2026-11-25. Server target: Paper 1.21.11 with CraftEngine, BetterModel and BetterHud (ports 48575 / 8403, display :114).

## Result: Pending in-game check (container cannot run client)

The container is shared with other agents' Gradle builds and an already running headless client (load average above 13,
16 GB machine), so a second server plus client was not started; the earlier bell check (PAR-METAL-18) was killed by the
memory cgroup in the same situation. No screenshot exists. Nothing about the item failed; the visual criterion is not yet
judged. The logic is covered by `BowlRulesTest`, `BowlDevicesMockBukkitTest` and `BowlBlocksTest`.

## Steps still to run (in_game_steps of the item)

1. `ce item give Bot tfp:ceramic/bowl` (the item shows TFC's bowl sprite, named "Ceramic Bowl"); give a vanilla bowl too.
2. Place the ceramic bowl on stone, and right-click the vanilla bowl on stone (not sneaking): a 12x3x12 bowl with TFC's
   texture is drawn, its outline is the low box (carrier `iron_trapdoor` bottom, no collision problems).
3. `ce item give Bot tfp:powder/salt 16`; right-click the bowl with the salt: the powder layer appears on the floor of the
   bowl and rises with the count; screenshot at GUI scale 2 and 3 (8x crop of the layer).
4. Hold raw meat and right-click the bowl: the meat is salted (tooltip shows Salted), the salt count drops by the number of
   meat items. Empty hand: take one (sneaking: 16). Break the bowl: the powder drops.

Criterion "A ceramic bowl placed holds salt": pending these steps.
