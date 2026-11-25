# PAR-METAL-02 in-game check

Date 2026-11-25, tree at 730b576 plus the item's uncommitted ledger lines. Paper 1.21.11 build 132 with the 1.21.11 client (Xvfb :100),
CraftEngine/BetterModel/BetterHud/PacketEvents from the run-task cache, jar from this tree (headless recipe,
`docs/testing/headless.md`). The bot `Bot` in survival; fluids and heat set with `/tfp debug mold Bot fluid|heat`, clicks sent with xdotool
(sneak + right click).

| Criterion | Result | Evidence |
|---|---|---|
| Vessel with 500 mB molten brass (1250) in the main hand, bell mold (preheated) in the off hand: sneak-right-click pours 100 mB; the mold glows red in the off hand and holds `tfp:metal/brass` 100 | pass | `pour-brass-into-bell-mold.png`, `pour-brass-into-bell-mold-hotbar.png` |
| The cooled bell mold (heat 300) cast by sneak-use gives a bell, the mold breaks (break chance 1: main hand empty) | pass | `bell-cast-result.png`, `bell-cast-result-hotbar.png`: a bell in hotbar slot 2, named "Brass Bell" (data check: `minecraft:bell` with item_name); it is the vanilla placeholder until PAR-METAL-18 adds `tfp:brass_bell` (ledger divergence) |
| Fire ingot mold with 100 mB bronze, cooled: sneak-use gives `tfp:metal/ingot/bronze` and the mold is kept, drained (stack size 16 again) | pass | `fire-ingot-mold-cast.png` (data check over RCON: slot 0 the empty fire ingot mold, slot 1 the bronze ingot) |

Notes: the first server run was killed by the container's memory limit and by a RiverCurrentTracker stall at the water spawn (not
this item's code); the checks above are the second run, on land.
