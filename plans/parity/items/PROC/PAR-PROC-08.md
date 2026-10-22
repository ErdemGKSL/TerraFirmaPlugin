---
id: PAR-PROC-08
title: "Barrel block: fluids, sealing, sealed and instant recipes, preservation"
area: PROC
status: open
wave: 9
deps: [PAR-INFRA-02, PAR-INFRA-04, PAR-INFRA-06, PAR-FLUID-01]
size: M
aliases: ["M3-PROC-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/InstantBarrelRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/InstantFluidBarrelRecipe.java"
  - "src/generated/resources/data/tfc/recipe/barrel"
files_owned:
  - "core/src/main/java/dev/tfp/core/barrel/**"
  - "core/src/test/java/dev/tfp/core/barrel/**"
  - "core/src/test/resources/dev/tfp/core/barrel/**"
  - "paper/src/main/java/dev/tfp/paper/barrel/**"
  - "paper/src/test/java/dev/tfp/paper/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrel/**"
  - "content/generated/content/craftengine/tfp/configuration/barrel/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:wood/barrel/oak"
  - "fill with brine (bucket), add meat, seal"
  - "tfp time add hours 5; unseal; hover meat"
plan:
  - "Per-wood barrel CE block (sealed state); fill/drain by bucket/jug; item slot; seal toggles via sneak-right-click (GUI in PAR-PROC-09); BarrelProgress.settle on interaction; instant recipes on insert; on_seal food traits (brined, pickled...) via FoodState."
tests:
  - "paper: MockBukkit seal + settle completes a recipe"
  - "core: BarrelGoldenTest exists"
  - "itest: all 331 barrel recipes load"
done_criteria:
  - "Brining meat in a sealed barrel adds the Brined trait"
---

# PAR-PROC-08: Barrel block: fluids, sealing, sealed and instant recipes, preservation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
