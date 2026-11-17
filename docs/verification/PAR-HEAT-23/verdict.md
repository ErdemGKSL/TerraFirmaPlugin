# PAR-HEAT-23 verification

- Date: 2026-11-17 (repair round after the verify run of cd58fbe)
- HEAD under test: cd58fbe (code unchanged in this round; this commit adds evidence only)
- Server: Paper 1.21.11 (spikes/client/server.sh, TFP world), CraftEngine self-hosted pack. Client: vanilla 1.21.11 headless (spikes/client/session.sh), bot `Bot`, op, survival with resistance.
- Site: stone platform at y 140, firepits at (-924 141 -2130) axis x and (-922 141 -2130) axis z.

## Why the earlier run saw "flames but lit=false"

Re-run by the main session. Lighting works with both starters:

- Flint and pyrite (sneak + right click): the strong strike is a chance draw (`FlintAndPyriteItem`), so several strikes are weak and do nothing; after 12 strikes the pit read `lit=true, burnTicks=380, burnTemperature=900` (`gui3-flint-and-pyrite-lit-readout.png`).
- Firestarter (sneak, hold past 3.6 s): lit at once, `lit=true, burnTicks=600, lastMaxBurnTicks=600` (`../PAR-HEAT-19/lit-readout.png`).

A `tfp:stick_bundle` burns 600 ticks (30 s), so a pit lit on its last bundle goes out before a slow readout; that matches the earlier `lit=false, burnTemperature 0` read with the lit model seen right after the strike. Separate finding (follow-up PAR-HEAT-33): after a calendar jump (`/tfp time add`) that burns the fuel out, the lit model stays until the next click, because the burn-out callback runs in server ticks.

## Steps and results

| step | result |
|---|---|
| `/tfp debug place tfp:firepit[lit=false]` (looking at a placeholder block on stone) | unlit TFC model, no daylight detector look (`gui2-unlit-x-left-z-right.png`) |
| 3-4 `tfp:stick_bundle` right-clicked in | readout lists the fuel in slots 0..3 |
| light: firestarter and flint and pyrite while sneaking | `lit=true`, TFC lit model (rocks, hot coals, campfire flames) |
| raw beef put in the input slot through the screen, `/tfp time add hours 1` | input empty, cooked beef in slot 5, ash 2 (`../PAR-HEAT-19/cooked-after-1h.png`) |
| empty-hand click on the burnt-out pit | the ash comes first (TFC order: unlit with ash gives the wood ash) |
| water bucket on a lit pit | `lit=false`, temperature 0, bucket empty, unlit model (`water-bucket-douse-readout.png`) |
| bronze shovel on a lit pit | `lit=false`, shovel damaged, unlit model (`shovel-douse-readout.png`) |
| axis z (`/tfp debug place tfp:firepit[lit=false,axis=z]`) | the model is turned 90 degrees, lit and unlit (`gui2-lit-x-left-z-right.png`, `gui3-lit-x-left-z-right.png`) |
| burn-out while loaded (no clicks, 75 s) | both pits switch to the unlit model on time (`gui3-unlit-after-burnout-x-left-z-right.png`) |
| real `minecraft:daylight_detector` at power 10 and 0 (carrier and hide) | both look like daylight detectors, no firepit (`gui3-real-detectors-power10-and-0.png`) |
| rain burn rate | not run in game (the site was below 0 C, snow); covered by `FirepitSim` tests (burn -2 per tick in rain) |

## Criteria

- Lit firepit cooks raw beef in game, screenshot of the lit visual: **pass** (`../PAR-HEAT-19/cooked-after-1h.png`, `../PAR-HEAT-19/lit-readout.png`).
- Lit and unlit firepits show TFC's models on both axes, no daylight detector look: **pass** (the four `gui2-*` / `gui3-*` crops).

Full-size lossless WebP screenshots of this run: scratchpad `ui/PAR-HEAT-23/`.
