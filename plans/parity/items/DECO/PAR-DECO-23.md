---
id: PAR-DECO-23
title: "Golden bamboo block, palm mosaic stairs and slabs, tree roots, light block (follow-up of PAR-DECO-12)"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-12]
size: M
aliases: ["split from PAR-DECO-12: the axis block needs three renderer displays on one barrier carrier (rendered_cube is single state), the stairs and slabs need the shared hitbox twins (HitboxDemands rows), tree roots and the light block need their vanilla look as carriers in the hitbox allocation"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/BambooBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TreeRootsBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCLightBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/misc/MiscShaped*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/misc/MiscShaped*"
  - "content/generated/content/craftengine/tfp/configuration/misc/shaped*"
ui: true
in_game_steps:
  - "place tfp:golden_bamboo_block on all three axes, tfp:wood/planks/palm_mosaic_stairs and _slab, tfp:tree_roots in and out of water and tfp:light at levels 0 and 15; screenshot"
plan:
  - "golden_bamboo_block: axis x|y|z (stripped with an axe like BambooBlock), three render items (tfc:block/golden_bamboo_x|y|z) on the barrier carrier with destroy_stages, strength and bamboo sounds of Blocks.BAMBOO_BLOCK, drops itself."
  - "palm_mosaic_stairs and palm_mosaic_slab: hitbox-only on shared vanilla twins like the wood stairs and slabs (PAR-DECO-01), BAMBOO_MOSAIC strength and sounds."
  - "tree_roots: waterloggable, 0.7 strength, MANGROVE_ROOTS sounds (add to RenderedSound), random-tick; light: level 0-15 (light emission = level, waterloggable, replaceable, 1/20 random tick removes it); both need a carrier in HitboxDemands."
tests:
  - "tools/datagen: MiscShapedTest"
done_criteria:
  - "golden bamboo block, palm mosaic stairs and slabs, tree roots and the light block are CE blocks with TFC strength, sounds, drops and names"
---

# PAR-DECO-23: Golden bamboo block, palm mosaic stairs and slabs, tree roots, light block (follow-up of PAR-DECO-12)

> Claimed: claude session_011osUp8LgspZkGFjFNWFDqQ (DECO helper), 2026-11-25

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-12 shipped the crates and the palm mosaic as renderer cubes (`dev.tfp.datagen.misc.MiscCubes`, `configuration/misc/cubes.yml`); `BlockCoverage` names this item as owner of what is left.
