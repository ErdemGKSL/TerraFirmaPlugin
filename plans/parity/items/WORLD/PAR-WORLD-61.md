---
id: PAR-WORLD-61
title: "Plant stage looks at packet time: blooming/dormant client states per plant from the selector's left-over pool states, chosen per viewer by PlantLifecycle"
area: WORLD
status: done
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
ui: false
in_game_steps: []
plan:
  - "Design: ARCHITECTURE.md section 9, 'Plant seasonal stages and tint (PAR-WORLD-48)'. Core rule `core/plant/lifecycle/PlantLifecycle` and data `data/tfp/data_maps/plant_lifecycle.json` exist (PAR-WORLD-48)."
  - "Datagen: for each tfc:plant loader model, a second look (the dormant model, tint baked as today) on a client state the PAR-WORLD-59 selector hands out from what the plant pools have left, flowers first; write the base-state -> dormant-state table to the lifecycle data map."
  - "Paper: an async packet rewriter on chunk and block-change packets (next to the seasonal biome remap) that maps a plant's base client state to its dormant state when `PlantLifecycle.stage` (column chunk climate, viewer hemisphere, calendar) is DORMANT or DYING. No world writes."
  - "Built 2026-11-25: datagen PlantBlockGen.dormantLooks serves whole plants from the left-over plant pool states (flowers first, enum order); 2 spare tripwire states give canna, water canna (shared empty dormant look) and goldenrod (goldenrod_2) on the never-placed tfp:plant/dormant_look[look=0..1]; the table dormant_states (base CE state -> {plant, state}) is in plant_lifecycle.json. Paper: PlantStageTable, PlantStageRemap (server-id to server-id swap, so CraftEngine's encoder after PacketEvents sends the dormant look; block() and section()), PlantStageClimate. The PacketEvents handler must live in dev.tfp.paper.packet (ArchitectureTest), outside files_owned: follow-up PAR-WORLD-63 (handler, CE id resolution, module, resend at season change, the in-game seasons check)."
  - "Pending in-game check (container cannot run client): moved to PAR-WORLD-63."
  - "Done: PAR-WORLD-61 commit; tests datagen plant/stage/PlantDormantLooksTest 4, paper plant/lifecycle/PlantStageRemapTest 8; ledger feature row and divergence updated; verification in PAR-WORLD-63."
tests:
  - "datagen: every dormant look on a distinct spare state; total within the pools"
  - "paper: rewriter maps a plant state by season; never touches non-plant states"
done_criteria:
  - "Plants with a spare state change look with the seasons as in TFC's dormant/blooming split; the rest keep the blooming look (ledger divergence). Deferred check tracked in PAR-WORLD-63"
---

# PAR-WORLD-61: Plant stage looks at packet time

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
