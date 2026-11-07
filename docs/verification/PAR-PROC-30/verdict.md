# PAR-PROC-30 verification: bricks craft and place as vanilla bricks

- Date: 2026-11-07. Base commit 9635378 plus the PAR-PROC-30 change (`minecraft:bricks` reclassified `tfc_present`,
  content regenerated with `./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc`, `:dist:shadowJar`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel, BetterHud and PacketEvents as installed by
  `spikes/client/server.sh` with `TFP_WORLD=1` (TFC world), priming start then restart. `Loaded recipes in 264.59ms (161)`,
  no `Found N issue(s)` line.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival, pack applied 3 s after join;
  one session at GUI scale 3 and one at GUI scale 2.
- Run by the worker session itself (no cheap-model agent could be started from this session); the worker judged every
  screenshot.
- Generated content after the fix: none of the 12 `visuals/<family>/<profile>/nether-end-*/suppressed_blocks.json` lists
  `minecraft:bricks`, and no `visual_mappings.yml` maps it.

## Steps

1. `setblock` a crafting table in front of Bot on a smooth stone pad, right-click it open (xdotool).
2. `give Bot minecraft:brick 5`, `ce item give Bot tfp:mortar 4`; pick up each stack and right-click one per cell in
   XYX/YXY/XYX; screenshot; shift-click the result; `data get entity Bot Inventory`.
3. Close, select the bricks, place one on the floor (right-click), screenshot; mine it with an iron pickaxe; list the item
   entities near Bot.
4. Same grid with 4 plain `minecraft:paper` (the material of `tfp:mortar`) in the Y cells; screenshot.
5. Vanilla 2x2 of 4 `minecraft:brick`; screenshot.
6. Relaunch the client at GUI scale 2 and repeat step 2.

## Results

| Check | Evidence | Result |
|---|---|---|
| `tfp:crafting/bricks` shows 4 Bricks, GUI scale 3 | `bricks-gui3.png` | **Pass** |
| Shift-take gives `minecraft:bricks` x4 | `{Slot: 8b, id: "minecraft:bricks", count: 4}` | **Pass** |
| Same at GUI scale 2 | `bricks-gui2.png`; inventory `minecraft:bricks` x4 | **Pass** |
| Placed bricks look like vanilla bricks (not stone or another repurposed look) | `placed-bricks.png` (half size) | **Pass** |
| Mined bricks drop themselves | item entity `{id: "minecraft:bricks", count: 1}` after breaking (447, 75, -1103) | **Pass** |
| Plain paper in place of mortar gives nothing | `neg-paper-gui3.png` | **Pass** |
| Vanilla 2x2 bricks still gives nothing (TFC disables it) | `neg-vanilla-2x2-gui3.png` | **Pass** |

| Criterion | Result |
|---|---|
| `minecraft:bricks` is not repurposed in any visual profile, and the datagen guard test passes | **Pass** (`CraftedResultsNotRepurposedTest`, 2 tests) |
| `tfp:crafting/bricks` crafts 4 Bricks in a crafting table in a TFC world (screenshot), placed bricks look like vanilla bricks | **Pass** |
