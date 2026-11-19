---
id: PAR-WORLD-44
title: "Wood plant blocks: saplings, potted saplings, fallen leaves and twigs as CE blocks (datagen, carriers, visuals)"
area: WORLD
status: open
wave: 22
deps: [PAR-WORLD-10, PAR-INFRA-13]
size: M
aliases: ["PAR-WORLD-10 follow-up: f50c44d closed PAR-WORLD-10 with logs, wood, stripped forms, planks and leaves only; the four wood plant kinds need carriers registered in HitboxDemands/VanillaCandidates, outside PAR-WORLD-10's files_owned. LeafDecay (PAR-GATHER-04) already places tfp:wood/fallen_leaves/<wood> and tfp:wood/twig/<wood>, which do not exist yet."]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/resources/assets/tfc/blockstates/wood/sapling"
  - "src/main/resources/assets/tfc/blockstates/wood/potted_sapling"
  - "src/main/resources/assets/tfc/blockstates/wood/fallen_leaves"
  - "src/main/resources/assets/tfc/blockstates/wood/twig"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/wood/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/wood/**"
  - "content/generated/content/craftengine/tfp/configuration/wood/plants/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/VanillaCandidates.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
ui: true
in_game_steps:
  - "Place tfp:wood/sapling/oak, tfp:wood/potted_sapling/oak, tfp:wood/fallen_leaves/oak[layers=1..8] and tfp:wood/twig/oak; screenshot each; walk through the sapling, fallen leaves and twig (no collision); tfp debug block on each"
plan:
  - "Generate, in a new datagen/wood generator writing configuration/wood/plants/<wood>.yml, the 20 woods x sapling, potted_sapling, fallen_leaves (layers 1..8), twig with TFC's ids, settings from Wood.BlockType (sapling strength 0, grass sound, no collision; potted sapling = vanilla potted sapling; fallen leaves 0.05/0.0, crop sound, no collision; twig 0.05/0.0, wood sound, no collision), TFC loot, and block items (sapling and twig items become block items: drop the plain-item path for them in AssetPipeline)."
  - "VISUAL BUDGET DECISION (made in PAR-WORLD-10 per the craftengine-visuals-pack skill; ledger divergences): (1) saplings: CE auto_state tripwire pool, 20 states, TFC's cross model; (2) fallen leaves: tripwire pool, one look per (wood, layer pair) = 80 states (layers 1+2 drawn at height 4 px, 3+4 at 8, 5+6 at 12, 7+8 at 16; TFC has 8 heights in 2 px steps), the CE layers property stays 1..8 for loot and decay; (3) twigs: one shared look for all woods (the twig texture is already untinted, PAR-WORLD-10 divergence) on the TfcShapes `twig` groundcover row, coordinated with PAR-WORLD-31's button budget, or one more tripwire state if the buttons are full; (4) potted saplings: explicit repurposed vanilla potted plant states that cannot occur in a TFC world (potted vanilla saplings/flowers; vanilla flowers and saplings never generate and are not obtainable), 20 of them, each hidden by a block_state_mappings line. Tripwire total 100 (or 101) of 126 (63 + 63). Add the tripwire pool to VanillaCandidates first; the allocator must fail, not degrade, on overflow."
  - "Leaves tint: tripwire and potted states are not foliage-tinted on the client; bake the summer foliage colour into the fallen leaves and sapling textures in the generator (TFC tints by season) and record the divergence; seasonal looks follow the leaves item (PAR-GATHER-09 / seasonal visuals)."
tests:
  - "tools/datagen: counts vs Wood.java (20 woods x 4 kinds; fallen leaves layers 1..8); every id LeafDecay can place exists; hitbox/visual report: tripwire usage <= 126, no overflow on 1.21.x and 26.x; CeYamlCheck clean"
done_criteria:
  - "Saplings, potted saplings, fallen leaves and twigs exist as CE blocks with TFC textures and no collision"
  - "Leaf decay drops visible fallen leaves and twigs"
---

# PAR-WORLD-44: Wood plant blocks: saplings, potted saplings, fallen leaves and twigs as CE blocks (datagen, carriers, visuals)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
