# PAR-HEAT-36 verification: the firepit no longer culls the face of the block below

- Date: 2026-11-25
- Fix: `HitboxDemands` asks the grill's outline row for the firepit demand, so the firepit shares the grill's hitbox-only
  carrier `soul_campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]` (a noOcclusion block) instead of
  `daylight_detector[inverted=false,power=0]` (6 px slab, occludes). Plugin jar built from the branch tip of 2026-11-25
  (`ea8f190` plus this change; datagen regenerated, `firepit.yml` carries the soul campfire on all four appearances, no
  `block_state_mappings`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0.
  Client: vanilla 1.21.11 under Xvfb, pack applied, Bot in creative with night vision. Run by the worker session itself.
- Site: a one-block stone floor (y 99) with water, grass and open air (a cave) directly under it.

| step | result | evidence |
|---|---|---|
| before the fix (detector carrier), unlit firepit seen from 1.5 blocks | the front and left edge of the block show a dark teal strip (the floor's top face is culled, the dark inside of the block shows through the gaps of TFC's model) | `gui2-before-daylight-detector-carrier.png` |
| after the fix, the same firepit from the same position | the floor texture shows around and between the rocks, no dark strip; the only black is the coals plate of TFC's `firepit_unlit` model (texture `coals`) | `gui2-after-soul-campfire-carrier.png` |
| `/tfp debug place tfp:firepit[...]` unlit x, unlit z, lit x, lit z, from 1.5 blocks and from above | PASS on all four: stone floor around the rocks, nothing of the water, grass or cave below shows; the lit ones show the flame and coals; selection box is the carrier's 7 px | `gui2-after-unlit-x-close.png`, `gui2-after-unlit-z-close.png`, `gui2-after-lit-x-close.png`, `gui2-after-lit-z-close.png`, `gui2-after-three-from-above.png`, `gui3-after-lit-x-close.png` |

| Criterion | Result |
|---|---|
| In game nothing below shows through a placed firepit on both axes, lit and unlit (GUI 2 and 3) | **Pass** |

Divergence (ledger): the firepit's selection and collision are the soul campfire's 7 px, TFC's 6 px.
