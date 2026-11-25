---
id: PAR-DECO-30
title: "Palm mosaic stairs and slabs, tree roots, light block, golden bamboo stripping (follow-up of PAR-DECO-23)"
area: DECO
status: open
wave: 50
deps: [PAR-DECO-23]
size: M
aliases: ["split from PAR-DECO-23: the golden bamboo block shipped there; the rest needs hitbox rows and carriers"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/BambooBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TreeRootsBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCLightBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/misc/MiscShaped*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/misc/MiscShaped*"
  - "content/generated/content/craftengine/tfp/configuration/misc/shaped*"
ui: true
in_game_steps:
  - "place tfp:golden_bamboo_block on all three axes, tfp:wood/planks/palm_mosaic_stairs and _slab, tfp:tree_roots in and out of water and tfp:light at levels 0 and 15; screenshot"
plan:
  - "golden bamboo block: stripping with an axe like BambooBlock (a paper behaviour or CE item event)."
  - "palm_mosaic_stairs and palm_mosaic_slab: hitbox-only on shared vanilla twins like the wood stairs and slabs (PAR-DECO-01), BAMBOO_MOSAIC strength and sounds."
  - "tree_roots: waterloggable, 0.7 strength, MANGROVE_ROOTS sounds (add to RenderedSound), random-tick; light: level 0-15 (light emission = level, waterloggable, replaceable, 1/20 random tick removes it); both need a carrier in HitboxDemands."
tests:
  - "tools/datagen: MiscShapedTest"
done_criteria:
  - "golden bamboo block, palm mosaic stairs and slabs, tree roots and the light block are CE blocks with TFC strength, sounds, drops and names"
---

# PAR-DECO-30: Palm mosaic stairs and slabs, tree roots, light block (follow-up of PAR-DECO-23)


All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-12 shipped the crates and the palm mosaic as renderer cubes (`dev.tfp.datagen.misc.MiscCubes`, `configuration/misc/cubes.yml`); `BlockCoverage` names this item as owner of what is left.
