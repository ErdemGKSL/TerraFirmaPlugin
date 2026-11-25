---
id: PAR-WORLD-42
title: "Remaining soil blocks: duff and clay duff, grass path, muddy roots per soil"
area: WORLD
status: done
wave: 18
deps: [PAR-WORLD-04]
size: M
aliases: ["PAR-WORLD-04 follow-up: BlockCoverage assigned duff, clay_duff, grass_path and muddy_roots to PAR-WORLD-04, whose title and plan do not list them"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedDuffBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/PathBlock.java"
  - "src/main/resources/assets/tfc/blockstates/duff/"
  - "src/main/resources/assets/tfc/blockstates/clay_duff/"
  - "src/main/resources/assets/tfc/blockstates/grass_path/"
  - "src/main/resources/assets/tfc/blockstates/muddy_roots/"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/soil/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/soil/**"
  - "content/generated/content/craftengine/tfp/configuration/soil/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-WORLD-04 generates grass, clay grass, coarse dirt, mud and rooted dirt per soil, peat, peat grass and the kaolin blocks in `tools/datagen` `soil/SoilBlocks` (TFC settings read from SoilBlockType, loot through tfp:loot_table/*, names from TFC lang). Duff, clay duff, grass path and muddy roots are still missing (BlockCoverage owner rule moved here)."
  - "Duff and clay duff (`ConnectedDuffBlock extends ConnectedGrassBlock`, strength 1.6 / 1.8, SoundType.GRASS, randomTicks, loot = the soil's dirt / 1-3 clay balls): TFC's `duff_top` overlay is untinted, so they cannot share the tinted grass_block look of PAR-WORLD-04; allocate one full-cube visual per snowy value (the connected look, all sides duff, alfisol or per-soil dirt below, decide by budget) through the visual allocator (VisualDemand)."
  - "Grass path (`PathBlock`, 15/16 high, strength 1.5, SoundType.GRASS, drops the soil's dirt, four random turns): needs a 15-pixel hitbox carrier from the hitbox selector (HitboxDemands, a dirt_path-like state) with TFC's per-soil model."
  - "Muddy roots (`RotatedPillarBlock`, ofFullCopy(MUDDY_MANGROVE_ROOTS) strength 4): an axis property, three visuals per soil from the allocator; TFC's cube_column model."
tests:
  - "tools/datagen: counts per soil vs TFC registry; settings and loot per type"
  - "BlockCoverageTest: PAR-WORLD-42 owns no ids once done"
  - "Done: tools/datagen soil/SoilExtraBlocks (called from SoilBlocks.run per soil into <soil>_set.yml): duff and clay duff (TFC strength 1.6 / 1.8, grass sounds, loot dirt / 1-3 clay balls, snowy property, one allocator visual per snowy value: the connected look, duff_top or snow on five faces over the soil's dirt or clay, the four top turns), grass path (strength 1.5, grass sounds, loot the soil's dirt; HitboxDemands demand per soil on the farmland row: alfisol got minecraft:farmland[moisture=7] with TFC's model list, the other seven the leaves auto_state pool, a full-cube hitbox; version selector code ready if the families differ), muddy roots (strength 4, muddy mangrove roots sounds, map colour PODZOL, axis property, one visual per axis). Names in tfp_soil lang. Tests: SoilExtraBlocksTest (4), SoilBlocksTest updated, GeneratedCarrierTest (PAR-WORLD-31/42 ids, $$ selectors and family mappings). In-game check: PAR-WORLD-57."
done_criteria:
  - "Every duff, clay duff, grass path and muddy roots block of TFC is a generated CE block with TFC's settings, loot and look"
  - "Deferred check tracked in PAR-WORLD-57"
---

# PAR-WORLD-42: Remaining soil blocks: duff and clay duff, grass path, muddy roots per soil

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
