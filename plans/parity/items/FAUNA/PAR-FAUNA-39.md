---
id: PAR-FAUNA-39
title: "Water around a wet lobster trap: a waterlogged carrier needs a selector override (follow-up of PAR-FAUNA-37)"
area: FAUNA
status: blocked
wave: 48
deps: [PAR-FAUNA-37]
size: S
aliases: ["follow-up of PAR-FAUNA-37 (2026-11-25): HitboxCarrier and the carrier overrides are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxCarrier.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lobstertrap/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "docs/verification/PAR-FAUNA-39/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:lobster_trap; place it in water and on land; screenshot both: the wet one is surrounded by water"
plan:
  - "Blocked on PAR-FAUNA-41 (2026-11-25): no sound carrier exists inside files_owned. A selector rule in HitboxCarrier that serves the waterlogged row only from fluid states has nothing good to pick: among the dumped explicit vanilla states the best waterlogged one scores about 0.06 (lightning rod), waterlogged leaves are policy `reference`, and a weak hitbox would break the trap's full-block outline and collision. The sound carrier is CraftEngine's auto_state group `waterlogged_tintable_leaves` (65 states, see-through full cube, score 1.0, the client draws the water; no cherry/pale-oak particles, tinted-leaf particles removed by CE on 1.21.5+), which needs (1) a fluid pool in carrier-policy.json and pool flags in VanillaCandidates, and (2) HitboxAllocation.free to allow a pool for a hitbox-only demand (it answers false for TRANSPARENT looks), both outside files_owned. Nothing was changed in code; PAR-FAUNA-41 owns those files and the whole fix (it can close this item's criterion)."
  - "Found in PAR-FAUNA-37: a tfc:lobster_trap[waterlogged=true] row (full block, noOcclusion) added to TfcShapes and a second demand set for the fluid=water appearances still select the dry carrier (minecraft:dirt_path, 15 px, score 0.91): HitboxCarrier only forbids waterlogged states for a dry TFC state, it never requires one for a waterlogged TFC state (only twin demands do). The best waterlogged vanilla states score about 0.06 (lightning rod) and the double slab occludes its own fluid faces, so the selector cannot pick one by itself."
  - "Options: (a) a selector rule that serves a waterlogged TFC row only from fluid states, with a minimum outline score, and an owner decision on a weak hitbox; (b) draw the water as our own translucent item-display cube (water texture, no vanilla tint) next to the TFC model in the wet appearances, which needs art and a biome tint decision. Pick with the owner before coding; the placement state (facing, fluid) is PAR-FAUNA-37."
tests:
  - "datagen: the fluid=water states take their own carrier or renderer, the empty ones the dry one; carrier pin"
done_criteria:
  - "A trap placed in water shows water around TFC's model; screenshot reviewed"
---

# PAR-FAUNA-39: Water around a wet lobster trap

Follow `plans/parity/PROTOCOL.md`.
