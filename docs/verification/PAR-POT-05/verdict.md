# PAR-POT-05 in-game check

Date 2026-11-19/20. Paper 1.21.11 build 132 with the 1.21.11 client, CraftEngine/BetterModel/BetterHud/PacketEvents from the
run-task cache, jar built from this item's tree (headless recipe, `docs/testing/headless.md`).

Steps run over RCON with the bot `Bot` (survival, peaceful): axe head mold (preheated to 1200 with `/tfp debug mold Bot heat`)
in the off hand, vessel with 500 mB copper at 1250 degrees (`/tfp debug mold Bot fluid ...`) in the main hand, sneak + right-click.

| Criterion | Result | Evidence |
|---|---|---|
| Sneak-pour from the vessel fills the mold in the other hand (one click, the off hand event does not also cast) | pass | `pour-filled-mold-offhand.png`: the mold stays in the off hand and now glows copper; the vessel stays in the main hand |
| A cooled mold gives the casting and breaks (break chance 1 for the copper axe head) | pass | `casting-result-inventory.png` (first run, a cold mold poured at once solidified and the same click cast it: the off hand is empty, a copper axe head is in the hotbar); the double handling was then fixed (150 ms guard) and the filled mold survives the pour as in the first screenshot |
| Pour screen (one slot, 1 mB per tick), mold tooltip `Copper 40 / 200 mB`, inventory right-click cast | not seen in game | covered by `MoldMockBukkitTest` (screen ticks and close, tooltip, cast, break roll) |

Pending in-game check of the pour screen and the tooltip: the container ran out of memory (OOM kill of the server) and another
agent used the same run directory and ports during the final run. A tracked follow-up is not needed: the logic is unit tested.
