---
id: PAR-WORLD-04
title: "TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel"
area: WORLD
status: done
wave: 17
deps: [PAR-INFRA-08]
size: M
aliases: ["M2-GEN-08 (block set)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/DirtBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/TFCRootedDirtBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/PathBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/soil/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/soil/**"
  - "content/generated/content/craftengine/tfp/configuration/soil/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/RockPalette.java"
ui: false
in_game_steps: []
plan:
  - "Current: full-cube dirt/clay/sand per soil are generated; grass variants, coarse dirt, mud, peat, kaolin, rooted dirt, rock gravel are written as vanilla placeholders."
  - "Generate the missing soil blocks per soil variant with the visual allocator (grass uses repurposed vanilla grass_block states so the biome tint still applies; record which states are taken)."
  - "Map TfcTerrain's surface kinds to the CE ids in RockPalette (cached BlockData, no per-block lookups)."
  - "Grass: every grass block (tfp:grass|clay_grass/<soil>, peat_grass, kaolin_clay_grass) has one property `snowy` and shares the two vanilla states minecraft:grass_block[snowy=false|true] (HideTargets.KEEP_VANILLA, never allocated or mapped away) with one shared model each (TFC grass_top on top and sides, tintindex 0 so the biome/seasonal colour applies; alfisol dirt below; four random top turns). TFC's north/east/south/west connections are not drawn (ledger divergence). Full cubes (coarse_dirt, mud, rooted_dirt per soil; peat; red/pink/white kaolin clay) take one allocator visual each (VisualDemand appended). Rock gravel already exists (tfp:rock/gravel/<rock>, rock families)."
  - "RockPalette: SOILS (TFC Variant order), surfaceId, surfaceIds(rawRockIds) (4 soil kinds x 8 soils then gravel per rock, appended to the palette ids so the refuse rule covers them) and surfaceIndex (plain arithmetic, no per-block lookups). Wiring into TfcTerrain and carrying the soil variant through core surface rules is outside files_owned: follow-up PAR-WORLD-41."
  - "Duff, clay duff, grass path and muddy roots (assigned here by BlockCoverage but not in this item's title) moved to follow-up PAR-WORLD-42 (one-line owner split in tools/datagen coverage/BlockCoverage.java). Minimal edits outside files_owned for this item's own rules: hitbox/GeneratedCarrierTest (grass blocks on the kept grass_block states are a visual choice, not selector carriers)."
  - "Done: soil/SoilBlocks generates 46 blocks (5 types x 8 soils, peat, peat grass, 3 kaolin clays, kaolin grass) with TFC settings, loot, names, peat sounds; RockPalette surface ids. Tests: SoilBlocksTest (9), RockPaletteTest (+4). Divergences: connected grass sides, mud speed/collision. Pending in-game check (container cannot run client): tracked in PAR-WORLD-41 once the terrain writes these blocks."
tests:
  - "tools/datagen: counts per soil vs TFC registry"
  - "paper: palette refuse rule test"
done_criteria:
  - "Deferred check tracked in PAR-WORLD-41 (the terrain only writes the soil blocks once PAR-WORLD-41 wires them into TfcTerrain)"
---

# PAR-WORLD-04: TFC soil and gravel block set in generation: grass, dirt, coarse dirt, clay, peat, kaolin, mud, rock gravel

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
