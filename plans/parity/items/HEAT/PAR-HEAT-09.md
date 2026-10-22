---
id: PAR-HEAT-09
title: "Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI"
area: HEAT
status: open
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
ui: true
in_game_steps:
  - "tfp debug place tfp:pot[lit=true]"
  - "fill water; add 3 vegetables; wait (tfp time add hours 1)"
  - "take soup with a bowl"
plan:
  - "Ceramic pot on a firepit; water filled by bucket/jug; PotRules boil counters; soup output into bowls (SoupMix), jams into jars; simple pot recipes; GUI per catalogue with progress sprites; `tfp:pot` damage on touching boiling pot (TfpDamageType)."
tests:
  - "core: PotGoldenTest, SoupMixGoldenTest exist"
  - "paper: soup assembled into bowls"
done_criteria:
  - "Cooking a soup in-game yields soup items with mixed nutrition"
---

# PAR-HEAT-09: Cooking pot on firepit: boiling, soups and jams, pot fluid, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
