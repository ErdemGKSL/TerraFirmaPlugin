---
id: PAR-WORLD-31
title: "Groundcover CE blocks: loose rocks, small ores, groundcover types and deposits (datagen, hitbox demands, visuals)"
area: WORLD
status: done
wave: 21
deps: [PAR-WORLD-09, PAR-INFRA-13]
size: M
aliases: ["PAR-WORLD-09 follow-up: the CE block half of that item needs hitbox demands in HitboxDemands, outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/OreDeposit.java"
  - "src/main/resources/assets/tfc/blockstates/rock/loose"
  - "src/main/resources/assets/tfc/blockstates/groundcover"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/groundcover/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/groundcover/**"
  - "content/generated/content/craftengine/tfp/configuration/groundcover/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
ui: false
in_game_steps: []
plan:
  - "Generate CE blocks with TFC's ids under tfp: (the ids GroundcoverBlocks.ceId already writes): tfp:rock/loose/<rock> and tfp:rock/mossy_loose/<rock> with property count 1..3, tfp:ore/small_<ore> (12), tfp:groundcover/<type> for every GroundcoverBlockType (bone, clam, dead_grass, driftwood, feather, flint, guano, humus, mollusk, mussel, pinecone, podzol, rotten_flesh, salt_lick, seaweed, stick, ...), tfp:deposit/<mineral>/<rock> (4 x 21, gravel-like full cubes; rock-agnostic visual at level-2 per ARCHITECTURE 9.1), plus tfp:rooted_dirt/<soil> if not made elsewhere. Settings, sounds, hardness and loot from TFC (loose rock drops the existing tfp:rock/loose/<rock> item, small ore its item). Blocks and items are separate CE registries: keep drops.yml's items as they are."
  - "Hitbox carriers: register demands in HitboxDemands for the shape rows TfcShapes already has (loose_rock_1..3, small_ore, groundcover_<shape>). VISUAL BUDGET DECISION NEEDED FIRST: floor button states give 14 types x 4 facings = 56 looks (fewer after the PAR-INFRA-43 worldgen-placeholder exclusions are lifted by PAR-WORLD-32), but loose rocks alone need 21 rocks x plain/mossy x 3 counts = 126 looks, plus 12 small ores and ~16 groundcover types. Options: (a) HITBOX_ONLY carriers (one shared transparent state per shape) with the look from entity_renderer (packet cost: ~5 loose rocks per chunk), (b) one look per (rock, mossy) with count shown only by the hitbox, (c) a mix: small ores and groundcover on button looks, loose rocks on entity_renderer. Record the choice in ARCHITECTURE 9 and a NOT-PARITY/divergence row; get the owner's call if (b)."
  - "Remove the skip reasons for ore/small_*, rock/loose and rock/mossy_loose from AssetPipeline.skipReason once emitted; copy TFC's models and textures through the generator (as CharcoalBlocks does); random rotation through a weighted model list where CE allows it."
tests:
  - "tools/datagen: counts vs TFC registries (loose rocks 42 blocks x 3 counts, small ores 12, groundcover types = GroundcoverBlockType values, deposits 84); CeYamlCheck clean; hitbox report has no overflow"
  - "VISUAL BUDGET DECISION (2026-11-25, option c): loose rocks hitbox-only on a shared carrier per count (red mushroom, heavy weighted pressure plate; same on both families), the look TFC's model as an item display; small ores and the 17 groundcover types with TFC's model list (random y turns, the driftwood and stick model sets) on a carrier of their outline where the selector has one free (buttons, pink petals on 1.21.x, wildflowers and leaf litter on 26.x), else hitbox-only with the first model; where the families' carriers differ the appearance is a `$$>=26.1` / `$$fallback` version selector and the hide mappings are split under `$$>=26.1` / `$$<26.1`. Deposits: 84 gravel-like cubes in the AssetPipeline (family `deposit`, TFC strength rock.category().hardness(2.0f), gravel sounds), drawn by the visual allocator (flattened gravel + specks model; the level-2 rock-agnostic deposit rule stays pending). No fluid state (follow-up PAR-WORLD-58)."
  - "Done: tools/datagen groundcover/GroundcoverCeBlocks (3 files: loose_rocks.yml 42 blocks x count 1..3, small_ores.yml 12, groundcover.yml 17; models, render items, block items for small ores, mossy loose rocks and the 11 groundcover types without a vanilla item, names in tfp_groundcover lang, drops through LootTableExport.delegateBlock), HitboxDemands groundcover demands (weight -15, served last), AssetPipeline deposit family and skip list, Datagen run line. Test GroundcoverCeBlocksTest (5). In-game check: PAR-WORLD-57."
done_criteria:
  - "Every id GroundcoverBlocks.ceId can return exists as a CE block in content/generated"
  - "Hitbox report lists the groundcover demands with a carrier on 1.21.x and 26.x"
---

# PAR-WORLD-31: Groundcover CE blocks: loose rocks, small ores, groundcover types and deposits (datagen, hitbox demands, visuals)

> see_not_parity: NP-VIS-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
