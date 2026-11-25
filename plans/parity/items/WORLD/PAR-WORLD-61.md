---
id: PAR-WORLD-61
title: "Plant stage looks at packet time: blooming/dormant client states per plant from the selector's left-over pool states, chosen per viewer by PlantLifecycle"
area: WORLD
status: open
wave: 29
deps: [PAR-WORLD-48, PAR-WORLD-59]
size: L
aliases: ["PAR-WORLD-48 follow-up (visual part)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java"
  - "src/main/resources/assets/tfc/models/block/plant"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/stage/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/plant/stage/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/PlantBlockGen.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/plant/shape/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "paper/src/main/java/dev/tfp/paper/plant/lifecycle/**"
  - "paper/src/test/java/dev/tfp/paper/plant/lifecycle/**"
ui: true
in_game_steps:
  - "Same meadow in spring, summer, autumn, winter (tfp debug calendar): flowers that got a dormant state show it in winter, the blooming look otherwise; screenshot each season"
plan:
  - "Design: ARCHITECTURE.md section 9, 'Plant seasonal stages and tint (PAR-WORLD-48)'. Core rule `core/plant/lifecycle/PlantLifecycle` and data `data/tfp/data_maps/plant_lifecycle.json` exist (PAR-WORLD-48)."
  - "Datagen: for each tfc:plant loader model, a second look (the dormant model, tint baked as today) on a client state the PAR-WORLD-59 selector hands out from what the plant pools have left, flowers first; write the base-state -> dormant-state table to the lifecycle data map."
  - "Paper: an async packet rewriter on chunk and block-change packets (next to the seasonal biome remap) that maps a plant's base client state to its dormant state when `PlantLifecycle.stage` (column chunk climate, viewer hemisphere, calendar) is DORMANT or DYING. No world writes."
tests:
  - "datagen: every dormant look on a distinct spare state; total within the pools"
  - "paper: rewriter maps a plant state by season; never touches non-plant states"
done_criteria:
  - "Plants with a spare state change look with the seasons as in TFC's dormant/blooming split; the rest keep the blooming look (ledger divergence)"
---

# PAR-WORLD-61: Plant stage looks at packet time

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
