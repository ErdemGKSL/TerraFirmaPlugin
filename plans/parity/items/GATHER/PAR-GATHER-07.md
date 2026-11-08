---
id: PAR-GATHER-07
title: "Item placement interactions: vanilla stick, bone, feather, flint, rotten flesh and salt place as TFC groundcover; a snow item over plants places a snow pile"
area: GATHER
status: open
wave: 22
deps: [PAR-GATHER-01, PAR-WORLD-09, PAR-CLIM-04]
size: S
aliases: ["parity audit 2026-11-08: InteractionManager.registerDefaultInteractions registers BlockItemPlacement for every GroundcoverBlockType with a vanilla item and the Items.SNOW snow-pile placement; PAR-GATHER-01 covers pickup and loose-rock placement only, PAR-CLIM-04 only weather-made piles"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/InteractionManager.java"
  - "src/main/java/net/dries007/tfc/util/BlockItemPlacement.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/itemplace/**"
  - "core/src/test/java/dev/tfp/core/itemplace/**"
  - "paper/src/main/java/dev/tfp/paper/itemplace/**"
  - "paper/src/test/java/dev/tfp/paper/itemplace/**"
ui: true
in_game_steps:
  - "give Bot minecraft:stick 4; give Bot minecraft:bone 2; give Bot minecraft:feather 2; give Bot minecraft:flint 2; give Bot minecraft:rotten_flesh 2; give Bot tfp:powder/salt 2"
  - "As Bot right-click the top of a grass block with each item; screenshot the six groundcover blocks (TFC models and facing)"
  - "give Bot minecraft:snow 8; As Bot right-click a grass plant (short grass) and a TFC flower; screenshot: snow piles over the plants; break one: the plant comes back"
plan:
  - "Current (audit 2026-11-08): right-clicking the ground with a vanilla stick, bone, feather, flint or rotten flesh (and TFC salt) places TFC's groundcover block (`GroundcoverBlockType.getVanillaItem`, registered as `BlockItemPlacement` in InteractionManager), and a vanilla snow layer item used on a plant, grass or another pile-able block places `SnowPileBlock.placeSnowPile` (the covered block is kept and restored on melt or break) with TFC's place sound. Neither is in any item: PAR-GATHER-01 places loose rocks back, PAR-CLIM-04 only turns weather snow into piles."
  - "Core (pure): the placement decision from a snapshot of the clicked face, the target and its neighbours: TFC's `BlockItemPlacement` rules (replaceable target, the groundcover block's `canSurvive` on a sturdy top face, waterlogging from the target fluid, facing from the player) and `SnowPileBlock.canPlaceSnowPile` (target in TFC's snow-pile-able set, layers stacking as vanilla snow otherwise)."
  - "Paper glue (FeatureModule): PlayerInteractEvent RIGHT_CLICK_BLOCK with these items (HIGH, ignoring cancelled; skip when the clicked block has its own interaction, as InteractionManager runs after the block); the item's vanilla use (eat rotten flesh, ...) still happens when the placement does not apply. Place through the CE adapter with the PAR-WORLD-09 groundcover ids and PAR-CLIM-04's snow pile (covered block stored as CLIM-04 does); consume one item unless creative; play the block's place sound (snow: TFC's extra pitch-0.8 sound); respect build permission (adventure mode `mayBuild`)."
  - "Ledger: feature row; divergence row if a target cannot be replaced on the CE carrier."
tests:
  - "core: ItemPlacementRulesTest: each vanilla item -> its groundcover type; non-sturdy face, water and replaceable targets as TFC; snow on grass, on a flower, on a full block (vanilla layer), on an existing pile (layer + 1)"
  - "paper: MockBukkit: stick on grass places tfp:groundcover/stick and consumes one; creative keeps the stack; adventure mode does nothing; rotten flesh on air still eats"
done_criteria:
  - "The six vanilla/TFC items place their groundcover and snow over plants places a snow pile that restores the plant (screenshots reviewed); tests green; ledger rows added"
---

# PAR-GATHER-07: Item placement interactions: vanilla stick, bone, feather, flint, rotten flesh and salt place as TFC groundcover; a snow item over plants places a snow pile

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
