# PAR-FAUNA-40 verdict

Date: 2026-11-25. HEAD 5194f59 plus this item's published models. Paper 1.21.11-132 with the 1.21.11 client (headless,
`docs/testing/headless.md`), CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0; jar built
by `:tools:datagen:generateContent` + `:dist:shadowJar`, pack applied after the join.

Steps: `testroom.sh` site; Bot (op) typed `/tfp debug animal spawn penguin adult` and `/tfp debug animal spawn turtle adult`
in chat (the command needs a player sender); the animal was teleported in front of Bot; `damage <animal> 1
minecraft:player_attack by Bot` (penguin) and `damage <turtle> 1 minecraft:generic`; screenshots about 1.5 s after the hit
(play dead lasts longer).

| Criterion | Result |
|---|---|
| Published models carry `play_dead` (tfp_penguin, tfp_penguin_baby, tfp_turtle), diff adds only that animation | pass: `publish_models.py` diff is +35 / +35 / +115 lines of the `play_dead` animation; `validate.py --lenient` OK on all three (the turtle's strict UV-overlap notes are TFC's shared UVs, unchanged) |
| A hit penguin lies on its back | pass: `penguin_play_dead.png` (belly and feet up) |
| A hit turtle draws head and legs in | pass: `turtle_before_hit.png` (head and flippers out) against `turtle_play_dead.png` (shell and plastron only) |

Verdict: pass (judged by the main session).
