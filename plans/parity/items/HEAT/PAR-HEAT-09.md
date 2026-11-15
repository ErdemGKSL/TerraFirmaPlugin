---
id: PAR-HEAT-09
title: "Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI"
area: HEAT
status: done
wave: 15
deps: [PAR-HEAT-07, PAR-INFRA-04, PAR-INFRA-13]
size: M
aliases: ["M3-HEAT-03 (pot)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/PotScreen.java"
  - "src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java"
  - "src/generated/resources/data/tfc/recipe/pot"
files_owned:
  - "core/src/main/java/dev/tfp/core/pot/**"
  - "core/src/test/java/dev/tfp/core/pot/**"
  - "core/src/test/resources/dev/tfp/core/pot/**"
  - "paper/src/main/java/dev/tfp/paper/pot/**"
  - "paper/src/test/java/dev/tfp/paper/pot/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Pot.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PotTest.java"
  - "content/generated/content/craftengine/tfp/devices/pot.yml"
  - "content/generated/content/craftengine/tfp/configuration/devices/pot.yml"
  - "core/src/main/java/dev/tfp/core/cooking/Pot*"
  - "core/src/test/java/dev/tfp/core/cooking/Pot*"
  - "core/src/main/java/dev/tfp/core/cooking/SoupMix*"
  - "core/src/test/java/dev/tfp/core/cooking/SoupMix*"
ui: false
in_game_steps: []
plan:
  - "Ceramic pot on a firepit; water filled by bucket/jug; PotRules boil counters; soup output into bowls (SoupMix), jams into jars; simple pot recipes; GUI per catalogue with progress sprites; `tfp:pot` damage on touching boiling pot (TfpDamageType)."
  - "Deferred (PROTOCOL 6.5): loading the pot in CraftEngine (configuration/devices/pot.yml) changes DatagenTest's non-occluding fixture list (outside files_owned), and the screen art needs its own datagen step; both, with the in-game steps and the screenshot criterion, moved to PAR-HEAT-25."
  - "Done: core/pot (PotData + codec, PotOutput, PotSim lazy boil over FirepitSim, PotMatch = TFC perfectMatchExists), paper/pot (PotDevice kind tfp:pot: TFC useItemOn order, fluid transfer, soup into bowls, jam into jars, fuel, sneak take-off with tfp:pot damage, fire-start lighting; PotRecipeBook: 145 simple + 66 jam + 3 soup recipes, selection with soup as fallback, outputs; PotConversion firepit <-> pot; PotInteractListener; PotScreens/PotScreenLink GUI with thermometer, fluid and status buttons). Tests: core PotSimTest (lazy = stepped live tick, 400 cases; split spans), PotMatchTest (against TFC's determinant), PotDataTest; paper PotContentDataMockBukkitTest (real data: recipes load, 3 vegetables -> 2 vegetable soups with SoupMix nutrition taken into bowls, boiled eggs, sneak take-off, a vanilla water bucket fills the pot and is refused while an output waits, creative soup taking uses the bowl as TFC, the firepit <-> pot conversion drops the old device's ash as wood ash), PotScreenLayoutTest. In-game check: PAR-HEAT-25 (the earlier commit message named it PAR-HEAT-24 by mistake)."
  - "Review fix: the sneak take-off hands out the pot item, the burn and the cool sound only after the scheduled conversion back to a firepit succeeded (PotEffects.toFirepit takes the follow-ups, as the grill); a refused conversion keeps the pot and its record and hands out nothing (MockBukkit case)."
tests:
  - "core: PotGoldenTest, SoupMixGoldenTest exist"
  - "paper: soup assembled into bowls"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-25"
---

# PAR-HEAT-09: Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
