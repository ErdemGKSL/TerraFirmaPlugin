# PAR-HEAT-24 in-game check: verdict

- Date: 2026-11-18 (repair round after the failed check of 95780a9)
- HEAD at the run: 95780a9 (no plugin code changed since; the repair commit changes only the self-test skip and the steps)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0,
  `TFP_WORLD=1`, headless 1.21.11 client (`spikes/client/session.sh`), pack applied. Run by the main session.
- Site: stone floor at y 81, the firepit on a stone block at -928 82 2128, Bot in survival south of it.

## Why the previous check failed

Not a conversion bug. `/tfp debug place` replaces the block the player looks at; the tester placed the firepit into a floor
whose block below had been broken (Bot fell to y 48). The grill click converted it (item used up), then the grill's
neighbour update ran TFC's `canSurvive` (no solid block below) and broke it, so the cell read as air with water below.
Same site with a solid block under the firepit: the conversion works every time (below).

A second trap found on this run: step 4's `tfp time add hours 1` puts the cooked beef at 900 C for most of an hour, and TFC's
`burn_meat` heating recipe (`c:foods/cooked_meat` at 900 C, no output) burns it away; the fuel also runs out. The steps now
open the screen within seconds of putting the beef on, and the self-test `grill.cook` skips 500 ticks instead of 1000.

## Steps and results

| step | result | evidence |
|---|---|---|
| 1 firepit placed on stone, 4 stick bundles, sneaking firestarter | PASS: `kind tfp:firepit`, lit=true, burnTemperature 900, fuel slots 0-2 | ui webp `repair-s1-firepit-lit` |
| 2 wrought iron grill on the lit firepit, not sneaking | PASS: block `tfp:grill[axis=x,lit=true,smoke_level=1]` (`/tfp debug block`: `craftengine:custom_2714`), device kind `tfp:grill` with the firepit's three fuel slots, lit, burn 900; the grill item used up; TFC's merged model drawn (bars along x, the lit fire under them) | `step2-grill-lit-readout.png` |
| 3 raw beef on two slots | PASS: one raw beef on each of two slots (5 and 7), item displays lying flat at 0.3 scale; they turn brown when cooked | `step3-raw-beef-on-top.png` |
| 4 grill screen, scale 2 and 3 | PASS: fuel column, five grill slots, thermometer; the beef cooks in place to Cooked Beef with **Wood Grilled** in the tooltip (scale 2 and 3), then heats on (Very Hot ... Bright Red, `Danger!!`) and burns away at 900 C (TFC `burn_meat`) | `step4-screen-scale2-wood-grilled.png`, `step4-screen-scale3.png`, `step4-tooltip-scale3-wood-grilled.png`, `step4-burning-scale2.png` |
| 5 sneak-click a cooked beef, then sneak-click elsewhere on the lit grill | PASS: the cooked beef comes into the hand; the wrought iron grill comes back, the block is `tfp:firepit[lit=true]` with the same three fuel slots, the ash drops, health 850 -> 804 (1 `tfp:grill` damage) | `step5-back-to-firepit.png` |

Done criterion "Grill cooks with the grilled trait (tooltip) and items show on top": **PASS**.

Notes (no new item): only axis x was placed (the debug place default); the axis-z display turn (`0,90,0`) was not seen in
game. Self-test `grill.cook` (500-tick skip) PASSED on Paper 1.21.11 (`:itest:runSelfTest`, second start of the run dir;
the first start fails every firepit case until the restart that loads our CraftEngine content). The carrier outline is the soul campfire's 7 px box (ledger divergence, TFC 11 px); when not lit and
with ash, a sneaking empty hand on the grill takes the ash first (TFC order). Full-size lossless WebP screenshots are in the
session scratchpad `ui/PAR-HEAT-24/repair-*.webp`.
