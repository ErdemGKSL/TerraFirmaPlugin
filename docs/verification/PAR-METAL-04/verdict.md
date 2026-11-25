# PAR-METAL-04 verification: anvil plan selection GUI

- Date: 2026-11-25. Code under test: 2b0f349 plus the ledger commit (jar from a clean worktree of the same tree, `:dist:shadowJar`
  after `generateContent`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted, ports 32575 / 8243). Client: headless 1.21.11 as `Bot`
  (survival), GUI scale 2 and 3, display :98. The anvil is the player-placed copper anvil; clicks by xdotool.

## Steps and results

1. Copper anvil placed, copper ingot sneak-placed on it (METAL-03 flow), then a plain right click: the plan screen opens
   (`01-plan-screen-gui2.png`, `02-plan-screen-gui3.png`). 14 plans listed (TFC lists 14 recipes for `c:ingots/copper`), two
   rows of nine buttons with TFC's button frames under exactly the 14 used cells, plain panel elsewhere, no page arrows
   (one page), the inventory aligned on the 3-row chest. The rod shows its count 2. PASS.
2. Hover the pickaxe head button: tooltip "Copper Pickaxe Head" with the item's melting line (`03-tooltip-pickaxe-head-gui2.png`).
   PASS.
3. Click it (the done criterion, selecting "pickaxe head" for a copper ingot): the screen closes and `/tfp debug anvil Bot info`
   reports plan `tfp:anvil/metal/pickaxe_head/copper`, target 74, work 0, steps 0, last plan the same
   (`04-chosen-plan-info-gui3.png`). 74 equals `40 + XoroshiroRandomSource(seed -34252288505622952).forkPositional()
   .fromHashOf("tfc:metal/pickaxe_head/copper").nextInt(74)` (the first run hashed the `tfp:` key and showed 53: fixed, the
   core test pins the mapping). PASS.
4. The chain and the unfinished lamp results are CraftEngine items that do not exist yet (other items); their buttons are a
   named paper (the white sheets in `02`). Accepted, ledger Divergences.

## Not run in game

- Page arrows: no copper-family item has more than 14 plans; paging is covered by the MockBukkit test (19 plans, arrow frames,
  clamping) and the art test (arrow frames equal TFC's sprites).
- Double ingot: 3 plans by the golden core test.
