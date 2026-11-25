# PAR-PROC-01 / PAR-PROC-20 verification: knapping in game

- Date: 2026-11-25
- Plugin jar: built from commit 6e783d9 (clean worktree `:dist:shadowJar`; includes PAR-TOOL-01's stone tool heads).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (`spikes/client/server.sh`, priming start then restart, TFC world). Client: vanilla 1.21.11 under Xvfb, pack applied, Bot in survival
  (`tfp debug thirst Bot 100`, peaceful). GUI scale 2 (`gui2-*`) and GUI scale 3 (`gui3-*`, `MC_OPTIONS=guiScale:3`).
- Run by the worker session itself (no cheap-model agent available); every screenshot below was looked at and judged here.

## Steps and results

| step | result | evidence |
|---|---|---|
| `ce item give Bot tfp:rock/loose/granite 2`, right-click air | PASS: the knapping screen opens: TFC's art laid into a chest, 25 granite cells on columns 1 to 5 / rows 0 to 4, arrow and output frame under chest slot 26 (8x crops of a cell and of the empty output frame: bevelled frame, no extra white square) | `gui2-knapping-open.png`, `gui3-knapping-open.png`, `gui2-cell-x8.png`, `gui2-output-frame-x8.png` |
| click the ten cells (0,0) (2,0) (3,0) (4,0) (4,1) (4,3) (0,4) (2,4) (3,4) (4,4) | PASS: each clicked cell turns into the panel (after the first two clicks and after the last); the output frame then shows the stone axe head; the granite stack went from 2 to 1 | `gui2-knapping-click1.png`, `gui2-knapping-click2.png`, `gui2-knapping-all-ten.png`, `gui3-knapping-all-ten.png`, `gui2-output-axehead-x8.png` |
| take the output | PASS: the item is "Stone Axe Head", "Igneous Intrusive", Very Light, Very Small (hotbar tooltip) | `gui2-axehead-in-inventory.png` |
| 5 `minecraft:clay_ball`, right-click air, click one cell | PASS: clay_ball tiles, the clicked cell shows the dark disabled tile; closing leaves 5 clay (nothing consumed) | `gui2-clay-open.png`, `gui2-clay-one-cell-off.png` |
| `minecraft:leather`, click one cell | PASS: the leather is gone from the hotbar, the window stays open, the clicked cell is the panel | `gui2-leather-click.png` |
| `ce item give Bot tfp:fire_clay 5`, click one cell | PASS: fire clay tiles (violet-grey), the clicked cell shows the disabled tile; 5 fire clay left after closing | `gui2-fire-clay-one-cell-off.png` |
| `ce item give Bot tfp:goat_horn 1`, click one cell | PASS for the visuals: goat horn tiles (dark), the clicked cell shows the disabled tile, the horn is gone, the window stays open. NOT checked: "no horn sound plays" (the headless client has no audio output) | `gui2-goat-horn-one-cell-off.png` |
| granite, ten cells removed, hover an on cell and a removed cell | PASS at GUI scale 2 and 3: only the vanilla white slot highlight, no tooltip box on either | `gui2-hover-on-cell-no-tooltip.png`, `gui2-hover-removed-cell-no-tooltip.png`, `gui3-hover-on-cell-no-tooltip.png`, `gui3-hover-removed-cell-no-tooltip.png` |

| Criterion | Result |
|---|---|
| Knapping an axe head from two granite loose rocks in game | **Pass** |
| Clay, fire clay and goat horn show their disabled texture on off cells; leather hides them | **Pass** (horn sound not audible here) |
| No knapping cell, on or off, shows a tooltip | **Pass** (scale 2 and 3) |

Not run: the 1.21.4 and 26.x client checks of the hide_tooltip / tooltip_display rewrite (only 1.21.11 is installed here).
