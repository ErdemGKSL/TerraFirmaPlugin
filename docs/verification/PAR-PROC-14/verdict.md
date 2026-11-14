# PAR-PROC-14 / PAR-PROC-36 verification: making and eating a salad in game

- Date: 2026-11-14
- Base commit: 98d0e86 plus the PAR-PROC-36 change (datagen regenerated, `:dist:shadowJar`)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449
  (`spikes/client/server.sh`, priming start then restart; vanilla world)
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival; GUI scale 2, then a second
  session with `MC_OPTIONS="guiScale:3"`. Pack applied 3 s after join both times.
- Run by the worker session itself: no cheap-model agent could be started from this session (PROTOCOL §6.1); the main
  session judged every screenshot.

## Steps and results

1. `ce item give Bot tfp:food/carrot 4`, `tfp:food/red_apple 2`. `ce item give Bot tfp:ceramic/bowl 4` answers
   "Unknown item": there is no CE ceramic bowl item yet, so the step used `give Bot minecraft:bowl 4` (TFC's
   `#tfc:salad_bowls` is `#c:bowls`, which holds the vanilla wooden bowl).
2. Sneak + right-click with the bowls: the salad screen opens with TFC's `salad.png` art (inputs row, bracket, bowl
   picture, bowl and salad frames, player inventory) and the 4 bowls in the bowl slot; every item sits inside its drawn
   frame, no filler panes (`gui2_open_bowls.png`, 4x crop `gui2_slots_x4.png`; GUI scale 3: `gui3_open_bowls.png`).
3. Carrots into input 1, apples into input 2: the output shows 2 salads (`gui2_two_salads.png`). Its tooltip
   (`gui2_salad_tooltip.png`): "Healthy Salad", expiry in 5 days (decay 4.0), "Nutrition:", saturation 8 %,
   Fruit 0.7, Vegetables 1.1, then "1 x Carrot", "1 x Red Apple" in grey.
4. Take the salads: carrots 2, apples 0, bowls 2 left; the output now shows the carrot-only salad of the rest
   (`gui2_after_take.png`), as TFC recomputes it. Closing returns the 2 carrots and 2 bowls; the hotbar holds 2 salads.
5. Eat one salad (food level 9 after a Hunger effect): the stack goes to 1 and the bowls to 3 (one bowl came back). The
   nutrition tab (`gui2_nutrition_after_eating.png`) shows fruit and vegetables above grain and protein, which were equal
   before: the salad's mixed nutrients were recorded, not the definition's zeros.

| Criterion | Result |
|---|---|
| Making a salad in game: screen with TFC's art, mixed-nutrition salad, inputs shrink, bowl returns after eating | **Pass** (steps 2 to 5) |
| Eating a salad adds its mixed nutrients | **Pass** (step 5; `NutritionEatDynamicFoodMockBukkitTest`) |

No plugin errors in the server log (the one exception is PacketEvents' own update check, HTTP 403 from GitHub).

## Independent rerun on commit 1610510 (2026-11-14)

Paper 1.21.11, vanilla 1.21.11 client, GUI scale 2 and 3, same steps (`minecraft:bowl` again, since
`tfp:ceramic/bowl` has no CE item yet). Every salad step matched the run above: the screen opened with the 4 bowls in
place, 2 vegetable salads were made, the inputs shrank to carrots 2, apples 0, bowls 2, eating one salad at food 9 raised
it to 13 with nonzero grain, fruit, vegetables and protein, and a bowl came back (server NBT: bowls 3, salads 2 and 1).
The tester marked the run "rejected" only for noise outside this item, so the verdict stays **Pass**:
- the Bot died twice from a leftover thirst debug value (`Bot dehydrated to death`; once "You Died" at TFC health 0/761
  with no server death line, a debug-path question for its own item); both runs were redone after `tfp debug thirst Bot 100`;
- `gamerule keepInventory` is `keep_inventory` on this server version;
- PacketEvents' update check failed with HTTP 403 from GitHub on the sandbox network.
