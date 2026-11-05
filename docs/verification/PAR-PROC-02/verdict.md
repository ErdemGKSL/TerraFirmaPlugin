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
| A sample of 10 crafts in-game | Deferred: PAR-PROC-25 |

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
