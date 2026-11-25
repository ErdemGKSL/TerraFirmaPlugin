# PAR-PROC-03 / PAR-PROC-28 verification: advanced crafting and vanilla recipe leakage in game

- Date: 2026-11-25
- Plugin jar: built from commit 6e783d9 (clean worktree `:dist:shadowJar`; includes PAR-PROC-24 tools and PAR-DECO-01 planks).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0.
  Client: vanilla 1.21.11 under Xvfb, pack applied, Bot in survival. GUI scale 2 (`gui2-*`) and 3 (`gui3-*`).
- Run by the worker session itself; every screenshot was looked at and judged.

## Steps and results

| step | result | evidence |
|---|---|---|
| knife + oat bread x2 + cooked beef in the sandwich layout (`oat_bread_sandwich_xsx`: knife top-left, bread top-middle, filling middle, bread bottom) | PASS: the result slot shows the sandwich; after the craft (shift-click) the sandwich is in the inventory, the inputs shrank (bread 4 to 2, beef 2 to 1) and the knife stays in the grid with a durability bar (damaged by one, `tfp:damage_crafting_remainder`) | `gui2-sandwich-grid-result.png`, `gui2-after-craft-knife-remainder.png`, `gui2-knife-durability-x12.png`, `gui3-sandwich-grid-result.png`, `gui3-after-craft-knife-remainder.png` |
| two stacks of `tfp:food/beef` given a day apart (`tfp time add days 1` between the gives), one in each of two slots of the 2x2 grid | PASS: the result is Raw Beef x2 (the number of non-empty slots); the result expires 8:05 July 6, the older stack's slot 1 reads 8:03 July 6, the newer stack's slot 2 12:50 July 7: the result carries the older date (2 minutes apart: the tooltips were taken minutes of real time apart) | `gui2-food-combine-two-slots.png`, `gui2-food-combine-result-tooltip.png`, `gui2-food-combine-slot1-tooltip.png`, `gui2-food-combine-slot2-tooltip.png` |
| three `tfp:rock/loose/granite` (paper base) + leather in the vanilla book layout | PASS: no result. Control: three vanilla paper + leather gives the book | `gui2-leak-3-rocks-leather-no-result.png`, `gui2-leak-control-3-paper-leather-gives-book.png` |
| two `tfp:wood/planks/oak` stacked vertically | PASS: 4 sticks | `gui2-tfc-planks-make-4-sticks.png` |
| crafter block (powered with a redstone block) holding the leakage grid (3 loose rocks + leather) | PASS: nothing crafted, no item dropped, the crafter keeps its items (`triggered=true`) | `gui2-crafter-leak-grid.png` |
| crafter block holding the two-planks grid | does NOT craft sticks (grid kept, `triggered=true`, no item dropped). This is the divergence `CraftListener#onCrafter` documents: the server matches recipes in a crafter by vanilla base material, our planks are not vanilla planks there, so the server finds no recipe and fires no `CrafterCraftEvent`; recipes of our own runtime are not crafted by a crafter. The step's "it crafts sticks" expectation cannot hold; the leakage criterion (nothing crafts) holds | `gui2-crafter-planks-grid.png` |

| Criterion | Result |
|---|---|
| A knife recipe crafts in game and the knife loses one durability | **Pass** |
| Self-test `craft.leakage` (`CraftLeakageCase`) passes on Paper 1.21.11 with CraftEngine | **Pass** (1027 of 1070 recipes, all cancelled; `:itest:runSelfTest -Pmc=1.21.11`) |
| Food combining: two slots give a stack of 2 with the older date | **Pass** |
| In-game leakage: our item in a book shape gives no result, in the crafting table and in a crafter | **Pass** (ladder shape not run: the book layout covers the same guard) |
| TFC planks make 4 sticks in the crafting grid | **Pass** (the crafter does not, recorded divergence) |
