# PAR-PROC-02 verification: CE loads the workbench recipes

- Date: 2026-11-05
- Base commit: f47a3fa plus the PAR-PROC-02 change (datagen regenerated, `:dist:shadowJar`)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel and BetterHud as installed by `spikes/client/server.sh`
  (vanilla world, no client)

## Steps

1. `./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc` and `./gradlew :dist:shadowJar`.
2. `spikes/client/server.sh paper 1.21.11 <run> <port> dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar`: priming start,
   stop, second start with the same jar.
3. Read CraftEngine's load lines in the server log.

## Results

| Criterion | Result |
|---|---|
| CE loads the recipes with 0 issues | **Pass** on the second run (see below) |
| A sample of 10 crafts in-game | PAR-PROC-25, section below: 9/10 pass, bricks fail (PAR-PROC-30) |

First run (before the fixes it led to): CraftEngine printed
`Found 1 issue(s) in file '.../configuration/crafting/general_recipes.yml'`:
`recipes.tfp:crafting/detector_rail.ingredients.P - Item tag '#minecraft:stone_pressure_plates' doesn't contain any item`,
and then `Failed to load recipes` with `Cannot parse component minecraft:tool ... Missing tag: 'tfp:mineable/glass_saw' in
'minecraft:block'` while building the result of `tfp:crafting/gem_saw`. Fixes:
- optional (`"required": false`) vanilla tag references in TFC's tags are left out (`ItemTags.add`): vanilla has
  `stone_pressure_plates` only as a block tag;
- a recipe whose result is a CE item the server cannot build (a tool rule on a `tfp:` block tag, `CraftingRecipes.unbuildable`)
  stays pending; follow-up PAR-PROC-24 makes those tags server tags.

Second run, same server folder with the fixed jar:

```
[CraftEngine] Loaded items in 531.56ms (2971)
[CraftEngine] Loaded recipes in 156.87ms (159)
Done (40.604s)!
```

No `Found N issue(s)` line and no `Failed to load` line. 159 = the 114 emitted `tfp:crafting/*` recipes plus CraftEngine's
own default-pack recipes (45 in the first run's count as well: 160 there with 116 emitted).

## In-game crafts (PAR-PROC-25)

- Date: 2026-11-06. Base commit 5eb498c (no code change; dist jar built from it after `tools/worktree/share-build.sh`).
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0
  (`spikes/client/server.sh`, priming start then restart; the default world counts as a TFC world for suppression).
  `Loaded recipes in 122.85ms (159)`, no `Found N issue(s)` line.
- Client: vanilla 1.21.11 under Xvfb (`spikes/client/session.sh`), player `Bot`, survival, GUI scale 3, pack applied 3 s after
  join. Only GUI scale 3 was captured (PROTOCOL §6.1 asks for 2 and 3); the scale 2 crops are taken in the PAR-PROC-30
  bricks re-check.
- Run by the worker session itself: no cheap-model agent could be started from this session (PROTOCOL §6.1); the main
  session judged every screenshot.

### Steps

1. `setblock` a crafting table in front of Bot, right-click it open.
2. For each recipe: close and reopen the table, `clear Bot`, give the ingredients (`give` for vanilla items, `ce item give`
   for `tfp:` items), place one item per grid cell by right-click (xdotool), screenshot, shift-click the result slot, then
   `data get entity Bot Inventory[i]` (id, count, `minecraft:item_model`).
3. Crops of grid + result slot: `crafts/<recipe>.png`; all of them in one sheet: `crafts-gui3.png` (row order: black dye,
   blank disc, glue / fire clay, daub, handstone / jute net, activator rail, granite cobble / bricks, vanilla 2x2 bricks,
   paper jute net / paper blank disc).

`tfp:crafting/firestarter` is not emitted at 5eb498c (`tfc:firestarter` is not a CE item yet, PAR-HEAT-05), so it was not
crafted.

### Results

| Recipe | Grid | Shown result | Taken (inventory) | TFC result | Verdict |
|---|---|---|---|---|---|
| `tfp:crafting/black_dye` | 1 `tfp:powder/charcoal` | Black Dye | `minecraft:black_dye` x1 | black dye x1 | **Pass** |
| `tfp:crafting/blank_disc` | `tfp:soot` + glass pane | blank disc icon | `tfp:blank_disc` x1 | x1 | **Pass** |
| `tfp:crafting/glue` | slime block | glue x9 | `tfp:glue` x9 | x9 | **Pass** |
| `tfp:crafting/fire_clay` | 4 kaolinite powder, 4 graphite powder, clay ball | fire clay | `tfp:fire_clay` x1 | x1 | **Pass** |
| `tfp:crafting/daub` | `tfp:straw`, dirt, clay ball | daub x2 | `tfp:daub` x2 | x2 | **Pass** |
| `tfp:crafting/handstone` | stick over 3 `tfp:rock/raw/granite` | handstone | `tfp:handstone` x1 | x1 | **Pass** |
| `tfp:crafting/jute_net` | 5 `tfp:jute_fiber` in an X | jute net | `tfp:jute_net` x1 | x1 | **Pass** |
| `tfp:crafting/activator_rail` | 6 `tfp:metal/rod/steel`, 2 sticks, redstone torch | Activator Rail x12 | `minecraft:activator_rail` x12 | x12 | **Pass** |
| `tfp:crafting/rock/cobble/granite` | 4 `tfp:rock/loose/granite` (2x2) | granite cobble | `tfp:rock/cobble/granite` x1 | x1 | **Pass** |
| `tfp:crafting/bricks` | 5 brick + 4 `tfp:mortar` (XYX/YXY/XYX) | **nothing** | nothing | bricks x4 | **Fail** |

Bricks: the result slot stays empty. Vanilla's 2x2 brick recipe gives nothing either (`crafts/vanilla_bricks_2x2.png`), but
that is TFC-correct on its own (TFC disables it: `data/minecraft/recipe/bricks.json` is `neoforge:false`), so it does not
support the diagnosis; PAR-PROC-30 checks it stays empty after the fix. The Y cells hold `tfp:mortar` (grey-brown lump,
TFC `textures/item/mortar.png`), not kaolinite powder (pink, used only in the fire clay grid). `minecraft:bricks` is classified `absent` in `docs/research/vanilla-fullcube-supply.csv`, so every visual profile repurposes
it (`visual_mappings.yml`: `minecraft:bricks: minecraft:stone`; `suppressed_blocks.json` lists it with creation path `item`),
and `SuppressionListener.onCraft` clears a crafting result that is a repurposed block in a TFC world. A scan of all emitted
`configuration/crafting/*_recipes.yml` results against the 12 profiles' `suppressed_blocks.json` finds `minecraft:bricks`
as the only such result. Not fixable inside this item's files: follow-up **PAR-PROC-30** (reclassify, regenerate, a datagen
guard, bricks re-check in game); recorded in the ledger's Divergences.

Material check: the CE items here are `minecraft:paper` with CraftEngine data. 5 plain paper in the jute net X
(`crafts/neg_jute_net_paper.png`) and plain paper + glass pane in the blank disc slots (`crafts/neg_blank_disc_paper.png`)
give no result: **Pass**. (The mortar variant named in the steps cannot show anything while bricks are suppressed; it moves
to PAR-PROC-30.)

| Criterion | Result |
|---|---|
| The 10 sampled recipes craft in a crafting table with the TFC result and count | **9/10 pass**; bricks fail (repurposed `minecraft:bricks`), fix and re-check in PAR-PROC-30 |
| The vanilla material of a CE ingredient alone does not satisfy the recipe | **Pass** (jute net, blank disc) |
