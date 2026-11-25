---
id: PAR-FARM-27
title: "Worldgen placement of fruit trees, bananas and wild spreading crop fruit"
area: FARM
status: done
wave: 34
deps: [PAR-FARM-16, PAR-FARM-17, PAR-FARM-23, PAR-WORLD-13]
size: M
aliases: ["split from PAR-FARM-16, PAR-FARM-17 and PAR-FARM-23 (their files_owned cannot reach the worldgen runner)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/plant/BananaFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/SpreadingCropFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/FruitTreeFeature.java"
  - "src/generated/resources/data/tfc/worldgen/placed_feature/plant"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/fruittree/Gen*"
  - "paper/src/main/java/dev/tfp/paper/banana/Gen*"
  - "paper/src/main/java/dev/tfp/paper/crop/wild/Gen*"
  - "paper/src/test/java/dev/tfp/paper/fruittree/Gen*"
  - "paper/src/test/java/dev/tfp/paper/banana/Gen*"
  - "paper/src/test/java/dev/tfp/paper/crop/wild/Gen*"
ui: false
in_game_steps: []
plan:
  - "Place natural fruit trees (growing branch with natural=true, leaves of tfp:plant/<tree>_leaves) and tfp:plant/banana_plant where TFC's placed features put them, and the wild pumpkin and melon fruit blocks (tfp:fruit/pumpkin and melon with FruitState wild=true, creation date at generation) beside tfp:wild_crop/pumpkin and melon, through the worldgen feature runner with CustomBlocks."
  - "Done: paper fruittree/{GenFeature,GenPlanner,GenTrees,GenModule}, banana/GenBananas, crop/wild/GenWildFruit; module fruit-gen (service line). A new chunk of a TFC world snapshots, a worker runs TFC's placement modifiers (climate with the real chunk data and forest density, heightmap, in_square, rarity 1/50, per-feature FeatureRandom) for the biome's fruit tree and banana features, the region thread re-validates and writes natural growing branches (300-day head start, settled at once), banana stalks and the wild pumpkin/melon fruit (placeholders converted to wild tfp:fruit). Tests: GenPlannerTest 9, GenCommitTest 5, GenWildFruitTest 5 (paper). Divergences in the ledger. Note: files_owned cannot reach the terrain or the wild crop placeholders, so the wild crops themselves stay placeholders until the terrain writes CE blocks (PAR-WORLD-46). Pending in-game check (container cannot run client)"
tests:
  - "paper: a generated chunk holds the trees, bananas and wild fruit in the biomes TFC lists"
done_criteria:
  - "Fruit trees, bananas and wild pumpkin patches with fruit generate in the TFC biomes"
---

# PAR-FARM-27: Worldgen placement of fruit trees, bananas and wild spreading crop fruit

Follow-up of PAR-FARM-16, PAR-FARM-17 and PAR-FARM-23. Follow `plans/parity/PROTOCOL.md`.
