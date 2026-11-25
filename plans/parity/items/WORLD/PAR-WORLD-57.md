---
id: PAR-WORLD-57
title: "In-game verification of PAR-WORLD-31, PAR-WORLD-32 and PAR-WORLD-42: groundcover, boulders and cave rocks in a fresh world; the remaining soil blocks"
area: WORLD
status: open
wave: 24
deps: [PAR-WORLD-31, PAR-WORLD-32]
size: S
aliases: ["PAR-WORLD-32 (and PAR-WORLD-09, PAR-WORLD-42) deferred screenshot checks: the worker had no subagent to drive the headless client"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/LooseRockFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/BouldersFeature.java"
files_owned:
  - "docs/verification/PAR-WORLD-57/**"
ui: true
in_game_steps:
  - "Fresh world; look at the ground near spawn (loose rocks, small ore indicators above a vein, shore groundcover, a boulder in highlands); tfp debug block on a loose rock shows tfp:rock/loose/<rock>[count=n]"
  - "Moved from PAR-WORLD-09: fresh world shows loose rocks and indicators above veins (screenshot)"
  - "Find a cave with an air floor below y 60: loose rocks lie on its floor (screenshot)"
  - "PAR-WORLD-42 (once done): give Bot tfp:duff/alfisol, tfp:clay_duff/alfisol, tfp:grass_path/alfisol, tfp:muddy_roots/alfisol; place them in a row (muddy roots on all three axes); screenshot at GUI scale 2; walk onto the grass path (1/16 lower)"
plan:
  - "Run the headless Paper 1.21.11 server + client (docs/testing/headless.md) with a fresh datagen + shadowJar, run the steps, store crops and verdict.md under docs/verification/PAR-WORLD-57/. Check also that the groundcover models on button, petal and leaf-litter carriers show TFC's models (no vanilla button), and that the hitbox-only loose rocks and small ores show their item display."
tests:
  - "none (in-game check)"
done_criteria:
  - "Fresh world shows loose rocks and indicators above veins (screenshot)"
  - "Boulders appear in highlands; cave floors carry loose rocks (screenshot)"
  - "Shore groundcover and small ores show TFC's models (screenshot)"
---

# PAR-WORLD-57: In-game verification of PAR-WORLD-31, PAR-WORLD-32 and PAR-WORLD-42

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
