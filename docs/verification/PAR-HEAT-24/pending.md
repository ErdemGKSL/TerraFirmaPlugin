# PAR-HEAT-24 in-game check: pending (verify stage)

Run the item's `in_game_steps` (plans/parity/items/HEAT/PAR-HEAT-24.md) on the headless Paper 1.21.11 server and client.
Besides the done criterion (grilled trait in the tooltip, items on top), look at:

- the grill model (item display of `tfp:render/grill_lit` / `_unlit`) on both axes: bars along x for axis x, along z for
  axis z, the fire part under the grill, no snow or soul campfire visible;
- the lit-to-unlit morph after burn-out and the five raw/cooked items sitting on the grill bars;
- the `tfp:gui/grill` screen art at GUI scale 2 and 3;
- the outline: the carrier is an unlit soul campfire (7 px, TFC 11 px); clicks on the top still pick TFC's slots.
