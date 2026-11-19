---
id: PAR-FARM-09
title: "Bananas"
area: FARM
status: done
wave: 32
deps: [PAR-FARM-08]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaSaplingBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/banana/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/banana/**"
  - "core/src/main/java/dev/tfp/core/banana/**"
  - "core/src/test/java/dev/tfp/core/banana/**"
ui: true
in_game_steps:
  - "plant banana sapling; tfp time add months 6; screenshot"
plan:
  - "Done (core + Paper glue): core/banana BananaRules (lifecycle table, climate range, sapling growth, cycle scan, growth step, banked cycles, survival, product count, dead lingering); paper/banana device kinds tfp:plant/banana_sapling, banana_plant, dead_banana_plant (one handler), catch-up kind tfp:banana, BananaModule. The CE blocks, models, hitboxes, loot and worldgen could not be done inside files_owned (hitbox files are shared, content/generated is a datagen output): follow-up PAR-FARM-17. Pending in-game check (container cannot run client)"
  - "Banana plant growth stages and harvest as TFC."
tests:
  - "core: growth golden"
  - "core: BananaRulesTest (10); paper: BananaMockBukkitTest (15)"
done_criteria:
  - "A banana plant fruits"
---

# PAR-FARM-09: Bananas

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Done: core `BananaRules` and paper `banana` (`BananaDevice`, `BananaEffects`, `PaperBananaEffects`, `BananaModule`) with `BananaRulesTest` (10) and `BananaMockBukkitTest` (15); ledger row and divergences; CE visuals split to PAR-FARM-17. Pending in-game check (container cannot run client).
