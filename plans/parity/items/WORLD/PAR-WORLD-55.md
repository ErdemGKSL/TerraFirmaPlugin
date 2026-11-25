---
id: PAR-WORLD-55
title: "In-game verification of PAR-WORLD-11 and PAR-WORLD-44: dead forests and mangroves in their biomes; sapling, potted sapling, fallen leaves and twig blocks"
area: WORLD
status: open
wave: 24
deps: [PAR-WORLD-11, PAR-WORLD-44]
size: S
aliases: ["PAR-WORLD-11 and PAR-WORLD-44 deferred screenshot checks (the worker had no subagent to drive the headless client and the recycled container had no cached server/client jars)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/tree/ForestFeature.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
files_owned:
  - "docs/verification/PAR-WORLD-55/**"
ui: true
in_game_steps:
  - "Locate a lowlands chunk and a salt marsh chunk via tfp debug chunk; screenshot the dead forest and the mangroves (roots in the water)"
  - "Place tfp:wood/sapling/oak, tfp:wood/potted_sapling/oak, tfp:wood/fallen_leaves/oak[layers=1..8] and tfp:wood/twig/oak; screenshot each; walk through the sapling, fallen leaves and twig (no collision); tfp debug block on each"
  - "Break oak leaves until LeafDecay drops fallen leaves and a twig; screenshot"
plan:
  - "Run the headless Paper 1.21.11 server + client (docs/testing/headless.md) with a fresh datagen + shadowJar, run the steps, store crops and verdict.md under docs/verification/PAR-WORLD-55/."
tests:
  - "none (in-game check)"
done_criteria:
  - "Dead forests and mangroves appear in their biomes (screenshot)"
  - "Saplings, potted saplings, fallen leaves and twigs show TFC's textures and have no collision (screenshot)"
  - "Leaf decay drops visible fallen leaves and twigs (screenshot)"
---

# PAR-WORLD-55: In-game verification of PAR-WORLD-11 and PAR-WORLD-44

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
