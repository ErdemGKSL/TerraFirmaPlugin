---
id: PAR-FAUNA-17
title: "Hunting drops, butchering, hides"
area: FAUNA
status: done
wave: 44
deps: [PAR-FAUNA-09, PAR-INFRA-09]
size: M
aliases: ["M5-HUNT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java"
  - "src/main/java/net/dries007/tfc/common/items/HideItemType.java"
  - "src/generated/resources/data/tfc/loot_table/entities"
files_owned:
  - "core/src/main/java/dev/tfp/core/hunt/**"
  - "core/src/test/java/dev/tfp/core/hunt/**"
  - "core/src/test/resources/dev/tfp/core/hunt/**"
  - "paper/src/main/java/dev/tfp/paper/hunt/**"
  - "paper/src/test/java/dev/tfp/paper/hunt/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hunt/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hunt/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn deer adult; kill; screenshot drops"
plan:
  - "Death drops from HuntDrops (size, familiarity, player kill); hide items (raw/soaked/scraped/prepared, 3 sizes) and meat items generated."
  - "Meat drops are the food items FoodItems already generates (HideItemsTest checks every HuntDrops item is a generated CE item or vanilla); hides are datagen hunt/HideItems (15 items incl. sheepskin)."
  - "Done: PAR-FAUNA-17 commit: paper/hunt/HuntModule death drops (decide async, apply sync; predator/sex/size/familiarity/player kill; carrier loot replaced, babies and doMobLoot honoured), datagen hunt/HideItems; tests core HuntDropsGoldenTest+HuntDropsTest, paper HuntModuleMockBukkitTest 8, datagen HideItemsTest 2; ledger row + 3 divergences. Pending in-game check (container cannot run client)."
tests:
  - "core: HuntDropsGoldenTest exists"
done_criteria:
  - "Killing a deer drops venison and a medium raw hide"
---

# PAR-FAUNA-17: Hunting drops, butchering, hides

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
