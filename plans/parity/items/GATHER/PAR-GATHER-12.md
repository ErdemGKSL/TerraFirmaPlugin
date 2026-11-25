---
id: PAR-GATHER-12
title: "CraftEngine blocks for loose rocks and groundcover, so pickup and stacking work in the world"
area: GATHER
status: blocked
wave: 22
deps: [PAR-GATHER-01, PAR-WORLD-09, PAR-INFRA-13]
size: L
aliases: ["found while doing PAR-GATHER-01: GatherListener keys on tfp:rock/loose/<rock>[count], tfp:rock/mossy_loose/<rock> and tfp:groundcover/<type>, but terrain writes vanilla button/carpet placeholders and no such CE blocks exist"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/groundcover/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/groundcover/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/gather/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/gather/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/GroundcoverBlocks.java"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/GroundcoverCeIdTest.java"
  - "content/generated/content/craftengine/tfp/configuration/gather/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:rock/loose/granite"
  - "right-click it with an empty hand"
  - "screenshot hotbar"
plan:
  - "UNBLOCKED (worker, 2026-11-25): PAR-WORLD-31 is done; files_owned widened to its groundcover generator and test for the two gaps the Blocked note records (the fluid property and canSurvive). Shared hitbox files: only own rows."
  - "Blocked (2026-11-25): PAR-WORLD-31 (open, wip commit 8bf9276) already generates these blocks (tools/datagen/.../groundcover/GroundcoverCeBlocks, hitbox demands in HitboxDemands): the same loose rocks, mossy loose rocks and groundcover on shared hitbox-only carriers. A second generator would emit the same ids twice, so this item's datagen half was dropped (the work stays in the author's scratchpad only). Done here: TfcTerrain writes GroundcoverBlocks.ceId through customBlockData with the vanilla placeholder as fallback, and GroundcoverCeIdTest pins the ids against PAR-GATHER-01's pieces. Unblock: PAR-WORLD-31 done, then run the in-game steps. Open points for PAR-WORLD-31's owner: TFC's blocks have the `fluid` property (FluidProperty.WATER: empty, water, salt water) that PAR-GATHER-07's ItemPlaceListener sets to `water` for waterlogged placement, and a canSurvive (sturdy_base_block below: center for one rock, full for two or three and for groundcover); the WORLD-31 generator has neither yet."
  - "Shared hitbox files (HitboxDemands, TfcShapes, tfc-shapes.json, GeneratedCarrierTest): edit only your own rows and pins, as in PAR-CLIM-04's UNBLOCKED note."
  - "Datagen: tfp:rock/loose/<rock> and tfp:rock/mossy_loose/<rock> (count 1-3, fluid property as TFC), tfp:groundcover/<type> from the TFC blockstates, models and loot; canSurvive as GroundcoverBlock / LooseRockBlock (below sturdy; count 1 centre support)."
  - "Swap GroundcoverBlocks.ceId users from the vanilla placeholders to the CE blocks (terrain writer) once the blocks exist."
  - "Then run the in-game steps; PAR-GATHER-01's pickup and stacking apply unchanged."
tests:
  - "datagen: state counts and loot vs TFC blockstates; hitbox carrier pins"
done_criteria:
  - "In-game pickup of a loose rock and a stick"
---

# PAR-GATHER-12: CraftEngine blocks for loose rocks and groundcover

> Claimed: outline worker, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
