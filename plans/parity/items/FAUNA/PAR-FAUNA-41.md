---
id: PAR-FAUNA-41
title: "Wet lobster trap on CraftEngine's waterlogged leaves pool: a fluid pool in the carrier policy and pool carriers for hitbox-only demands (follow-up of PAR-FAUNA-39)"
area: FAUNA
status: open
wave: 49
deps: [PAR-FAUNA-37]
size: S
aliases: ["follow-up of PAR-FAUNA-39 (2026-11-25): the carrier policy, VanillaCandidates and HitboxAllocation are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/VanillaCandidates.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxAllocation.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxCarrier.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
  - "docs/verification/PAR-FAUNA-41/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:lobster_trap; place it in water and on land; screenshot both: the wet one is surrounded by water, the dry one is not; walk into the wet one (full collision, no swim prediction)"
plan:
  - "Found in PAR-FAUNA-39: no explicit waterlogged vanilla state in the dumped candidates serves a full-block outline (best about 0.06, lightning rod; waterlogged leaves are policy `reference`, the double slab occludes its own fluid faces). CraftEngine's auto_state group `waterlogged_tintable_leaves` (65 states: oak, jungle, acacia, dark_oak, mangrove leaves with waterlogged=true; no cherry/pale-oak particles; tinted-leaf particles are removed by CE's remove-tinted-leaves-particle on 1.21.5+) is a see-through full cube whose fluid the client draws: score 1.0 for TFC's Shapes.block() row and water around the trap."
  - "Carrier policy (carrier-policy.json pools + VanillaCandidates): add the pool `waterlogged_tintable_leaves` with a `fluid` flag (pool VStates carry flags beyond occludes). HitboxCarrier.filter: pools go through the fluid rules too (a dry TFC row never takes a fluid pool; a waterlogged row takes only fluid states, a minimum outline score kept)."
  - "HitboxAllocation.free answers false for a pool when the demand is hitbox-only (TRANSPARENT): allow a pool for a hitbox-only demand (the appearance is an empty model on the pool; Mode.POOL with an empty model) or give the wet demands an empty-model look. Keep every other allocation unchanged (GeneratedCarrierTest pins)."
  - "HitboxDemands: a second demand set for the fluid=water appearances (row tfc:lobster_trap[waterlogged=true] in TfcShapes / tfc-shapes.json, full block, noOcclusion); LobsterTrapBlock: fluid=water states take the wet appearances (pool carrier, empty model, the same item display), fluid=empty the dry shared carrier."
tests:
  - "datagen: the fluid=water states take the waterlogged pool, the empty ones the dry carrier; no other demand changes carrier (GeneratedCarrierTest)"
done_criteria:
  - "A trap placed in water shows water around TFC's model and a dry one none; screenshots reviewed"
---

# PAR-FAUNA-41: Wet lobster trap on CraftEngine's waterlogged leaves pool

> Claimed: session_011osUp8LArwstSDt7X82o4E, 2026-11-25

Follow `plans/parity/PROTOCOL.md`.
