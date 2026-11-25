---
id: PAR-WORLD-32
title: "Wire groundcover, boulders, cave loose rocks and the new soil features into TfcTerrain with CE blocks, and the in-game groundcover check"
area: WORLD
status: done
wave: 22
deps: [PAR-WORLD-31]
size: M
aliases: ["PAR-WORLD-09 follow-up: TfcTerrain is outside that item's files_owned; deferred in-game check of PAR-WORLD-09"]
tfc_refs:
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/large_features"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/underground_decoration.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/GroundcoverBlocks.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/GroundcoverWiringTest.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/TfcTerrainGroundcover*"
ui: false
in_game_steps: []
plan:
  - "PAR-WORLD-09 left the pure rules ready in core: Boulders (6 features, first large features, before the forest), UndergroundLooseRocks (underground decoration step: after the vein indicators, before the boulders; needs a CaveView of the chunk: minecraft:air, legacy solid, #minecraft:ice), ShoreGroundcover guano (needs GroundView.topBlockStone), Deposits water clay disc and rooted dirt, PowderSnow (needs DepositMaterial.SNOW for snow_block/packed_ice/blue_ice in depositMaterials), MollisolDiscs (needs GroundView.forestDensity from the chunk data's ForestType density; kind MOLLISOL = set the soil variant to mollisol when it is entisol, aridisol or oxisol; GroundcoverBlocks.placeholder returns null for it: keep the block)."
  - "TfcTerrain.Ground: implement forestDensity and topBlockStone; build the CaveView over ChunkData; call boulders (GroundcoverBlocks.writeBoulders; rock blocks via the palette ids tfp:rock/raw|cobble|mossy_cobble/<rock>, GroundcoverBlocks.ceId) and underground loose rocks in TFC's step order; handle the null MOLLISOL placeholder."
  - "Write CE blocks instead of placeholders: resolve GroundcoverBlocks.ceId through BlockVisuals like the rock palette (fallback to the placeholder while CE is not loaded). Lift the PAR-INFRA-43 worldgen-placeholder exclusions of button states once the placeholders are gone."
tests:
  - "paper: MockBukkit/unit test that the write order follows TFC's steps and that MOLLISOL keeps the block; GroundcoverWiringTest covers every Groundcover.Kind and DepositKind"
  - "Done: TfcTerrain writes the PAR-WORLD-31 CE blocks (groundcover, loose rocks with count, small ores, ore deposits per mineral and rock; placeholder while CraftEngine is not loaded; waterlogged decisions keep the placeholder until PAR-WORLD-58), the boulders (first large features, before the forest; CE raw/cobble/mossy cobble rock, else the palette rock or vanilla cobblestone), the cave floor loose rocks (after the vein indicators, CaveView over this chunk: air, legacy solid, #minecraft:ice), Ground.forestDensity (ForestType.density) and Ground.topBlockStone (raw rock or sandstone top, the guano ground), DepositMaterial.SNOW for snow block, packed ice and blue ice (powder snow); MOLLISOL keeps the block. The PAR-INFRA-43 button exclusions stay: the placeholders remain the fallback. Test: GroundcoverWiringTest (+1: CE ids of covers, deposits, boulders; MOLLISOL keeps the block). In-game check moved to PAR-WORLD-57."
done_criteria:
  - "Deferred check tracked in PAR-WORLD-57"
---

# PAR-WORLD-32: Wire groundcover, boulders, cave loose rocks and the new soil features into TfcTerrain with CE blocks, and the in-game groundcover check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
