---
id: PAR-PROC-19
title: "Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs"
area: PROC
status: done
wave: 5
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/common/items/Powder.java"
  - "src/main/java/net/dries007/tfc/common/items/GemSawItem.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/material/**"
  - "content/generated/content/craftengine/tfp/configuration/material/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/models/item/{alabaster_brick.json,alfalfa.json,blank_disc.json,blubber.json,bone_needle.json,brass_mechanisms.json,burlap_cloth.json,cactus_wood.json,canola.json,canola_paste.json,compost.json,daub.json,dirty_jute_net.json,dried_cactus_wood.json,empty_jar.json,empty_jar_with_lid.json,fire_clay.json,gem_saw.json,glue.json,goat_horn.json,handstone.json,jar_lid.json,jute.json,jute_fiber.json,jute_net.json,kaolin_clay.json,mortar.json,olive_paste.json,papyrus.json,papyrus_strip.json,pure_nitrogen.json,pure_phosphorus.json,pure_potassium.json,sandpaper.json,silk_cloth.json,soaked_papyrus_strip.json,soot.json,spindle.json,stick_bunch.json,stick_bundle.json,straw.json,treated_hide.json,unrefined_paper.json,wool.json,wool_cloth.json,wool_yarn.json,wrought_iron_grill.json}"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/models/item/gem/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/models/item/ore/{bituminous_coal.json,halite.json,lignite.json}"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/models/item/powder/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/models/item/spawn_egg/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/block/devices/quern/{handstone_bottom.png,handstone_side.png,handstone_top.png}"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/block/wood/sheet/oak.png"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/item/{alabaster_brick.png,alfalfa.png,blank_disc.png,blubber.png,bone_needle.png,brass_mechanisms.png,burlap_cloth.png,cactus_wood.png,canola.png,canola_paste.png,compost.png,daub.png,dirty_jute_net.png,dried_cactus_wood.png,empty_jar.png,empty_jar_with_lid.png,fire_clay.png,gem_saw.png,glue.png,goat_horn.png,jar_lid.png,jute.png,jute_fiber.png,jute_net.png,kaolin_clay.png,mortar.png,olive_paste.png,papyrus.png,papyrus_strip.png,pure_nitrogen.png,pure_phosphorus.png,pure_potassium.png,sandpaper.png,silk_cloth.png,soaked_papyrus_strip.png,soot.png,spindle.png,stick_bunch.png,stick_bundle.png,straw.png,treated_hide.png,unrefined_paper.png,wool.png,wool_cloth.png,wool_yarn.png,wrought_iron_grill.png}"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/item/gem/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/item/ore/{bituminous_coal.png,halite.png,lignite.png}"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/item/powder/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp/textures/item/spawn_egg/**"
  - "content/generated/content/craftengine/tfp/resourcepack/assets/tfp_material/lang/**"
  - "content/generated/content/reports/tfc-item-coverage.json"
ui: false
in_game_steps: []
plan:
  - "Generate every remaining plain TFC item (graded ore items, small ores, gems cut/uncut, powders, ore powders, fibres, cloths, papyrus/paper chain, glue, soot, mortar, sandpaper, straw, sticks bunch/bundle, blubber, wool/yarn, daub, handstone, spindle, brass mechanisms, jars and lids, spawn eggs) with names, models, item_size and item_heat data; gem saw tool."
  - "Scope notes: the graded and normal ore pieces already exist in drops.yml (asset pipeline); their stack size is PAR-PROC-21. Small ores are TFCBlocks groundcover block items (PAR-WORLD-09), not TFCItems. Lumber needs the wood paletted atlas (PAR-DECO-01). Basket is PAR-PROC-22. Item heat comes from TFC's item_heat data at runtime, not from the CE item. The in-game steps and the screenshot criterion moved to PAR-PROC-23 (PROTOCOL §6.5)."
  - "Done: datagen material/ (TfcItemRegistry reads all 1618 TFCItems ids; MaterialItems writes 175 CE items in configuration/material/*.yml with models, textures, names in assets/tfp_material/lang, item_size stack sizes, durabilities, gem saw at bronze tier; ItemCoverage writes reports/tfc-item-coverage.json: 887 generated, 731 owned by named parity items, 0 unowned, and fails datagen on an unowned id). Tests: tools/datagen material/MaterialItemsTest (10). Verification: deferred to PAR-PROC-23 (docs/verification/PAR-PROC-19/)."
  - "Review fixes: the gem saw carries TFC's productAttributes(BRONZE, -0.2, -2.0) as attribute_modifiers (ledger row reworded); the datagen sample adds handstone and gem_saw; files_owned lists the generated models, textures, lang and coverage report; PAR-PROC-23 no longer lists this item in deps (PROTOCOL §6.5)."
tests:
  - "tools/datagen: every TFCItems registration has a CE item or a documented owner item"
done_criteria:
  - "Deferred check tracked in PAR-PROC-23 (`ce item give` works for a sampled 20); coverage report shows 0 unowned TFC items"
---

# PAR-PROC-19: Misc materials as CE items: ores, gems, powders, fibres, cloth, paper, glue, straw, mortar, spawn eggs

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
