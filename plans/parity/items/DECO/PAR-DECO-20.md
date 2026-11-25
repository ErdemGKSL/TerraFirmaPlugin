---
id: PAR-DECO-20
title: "Wood log, stripped log, wood and leaves block models missing from the pack: the blocks draw as missing-texture cubes in game"
area: DECO
status: open
wave: 21
deps: []
size: S
aliases: ["found in the PAR-INFRA-65 in-game check (2026-11-25): placing tfp:wood/log/oak and tfp:wood/leaves/oak shows the purple-black cube; client log 'Missing block model: tfp:block/wood/log/chestnut', 'tfp:block/wood/stripped_log/birch', 'tfp:block/wood/leaves/dense_leaves/sequoia'"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/wood/log/oak.json"
  - "src/main/resources/assets/tfc/models/block/wood/log/oak.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/AssetPipelineTest.java"
  - "docs/verification/PAR-DECO-20/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/log/oak, tfp:wood/stripped_log/oak, tfp:wood/wood/oak, tfp:wood/leaves/oak; place each: TFC's bark and ring textures (logs on all three axes), cutout leaves with the foliage tint; screenshot at GUI scale 2 and 3; the client log has no 'Missing block model: tfp:block/wood/...' line"
plan:
  - "The configuration/wood/<wood>.yml blocks name appearances/models (tfp:visual/wood/log/<wood>, ...) whose block model files are not in the CraftEngine pack (assets/tfp/models/block/wood/log/*.json absent; only the item models and the textures are). The wood blocks are written by AssetPipeline (configuration/wood/<wood>.yml, args wood_hardness ...). Find where it copies TFC's models and textures for the wood families (stairs, slabs, fences have theirs through WoodFamilies) and add the log, stripped log, wood, stripped wood and leaves models for the 20 woods."
tests:
  - "datagen: every model a wood CE block names exists in the output"
done_criteria:
  - "The wood log, stripped log, wood and leaves blocks show TFC's models and textures in game on 1.21.11; no Missing block model line for tfp:block/wood/log, stripped_log, wood or leaves in the client log"
---

# PAR-DECO-20: Wood log and leaves block models missing from the pack

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`). Follow `plans/parity/PROTOCOL.md`.
