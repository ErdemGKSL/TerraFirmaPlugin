---
id: PAR-METAL-14
title: "Bloomery multiblock and bloom"
area: METAL
status: done
wave: 35
deps: [PAR-METAL-11, PAR-INFRA-10, PAR-HEAT-04]
size: M
aliases: ["M4-BLOOM-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BloomBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/bloomery"
files_owned:
  - "core/src/main/java/dev/tfp/core/bloomery/**"
  - "core/src/test/java/dev/tfp/core/bloomery/**"
  - "core/src/test/resources/dev/tfp/core/bloomery/**"
  - "paper/src/main/java/dev/tfp/paper/bloomery/**"
  - "paper/src/test/java/dev/tfp/paper/bloomery/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bloomery/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bloomery/**"
  - "content/generated/content/craftengine/tfp/configuration/bloomery/**"
  - "itest/src/main/java/dev/tfp/itest/cases/BloomeryCase.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/MaterialItems.java"
  - "paper/src/main/resources/config.yml"
  - "docs/config.md"
ui: true
in_game_steps:
  - "build a 3-high bloomery chimney; throw hematite + charcoal; light; tfp time add hours 15; screenshot bloom"
plan:
  - "Bloomery door block; chimney of #bloomery_insulation scanned async; ore + charcoal thrown in (item entities consumed, alternating intake); lit -> lazy phase (14400 ticks) -> bloom block with layers; breaking the bloom with a pickaxe yields raw iron blooms; refine on the anvil."
  - "Done: core dev.tfp.core.bloomery gets BloomeryStructure (TFC's base multiblock per facing, chimney levels, gate rule, over a block probe), BloomeryData (litTick, duration) and BloomData (count, maxCount, layers) next to the existing BloomeryRules, which gains intakeSequence (TFC's catalyst/input alternation as an order) and moltenTower (manageMoltenBlockTower). Paper dev.tfp.paper.bloomery: BloomeryDevice (kind tfp:bloomery: run = complete the burn first, then capacity from the scanned chimney, pop excess and halt, gate break, intake of the chimney's item entities, molten tower; lightAt on a strong fire start; door toggle; placement facing; removal ejects in front), BloomDevice (kind tfp:bloom: one item, count/maxCount, dropOne) with BloomListener (BlockBreakEvent: one item a break), BloomeryDropListener (thrown items trigger five checks), BloomeryBook (tfc:bloomery recipes), PaperBloomeryEffects (region-task writes, calendar-jump sweep via BurnOutWatch), BloomeryModule (config bloomery.capacity and bloomery.max-chimney-height, fire lightable, chunk-load callback). Datagen: BloomeryBlocks writes tfp:bloomery (16 states), tfp:bloom (8) and tfp:molten (8) with TFC's models as item displays on hitbox-only carriers (new TfcShapes rows and HitboxDemands demands, listed in files_owned above), the bloomery item and name; MaterialItems gets the two bloom items. Tests: core BloomeryStructureTest 9 (+ the existing 22 bloomery tests); paper BloomeryMockBukkitTest 23; datagen BloomeryBlocksTest 8; itest case bloomery.bloom (raw bloom of 4 from 4 cast iron ingots and 8 charcoal after a calendar skip, anvil recipe raw -> refined present; not run here). Ledger: feature row and 10 divergences. Pending in-game check (container cannot run client): in_game_steps stay for the verification run."
tests:
  - "core: BloomeryGoldenTest exists"
  - "paper: chimney scan"
done_criteria:
  - "Wrought iron path: bloomery -> raw bloom -> refined bloom (self-test)"
---

# PAR-METAL-14: Bloomery multiblock and bloom

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
