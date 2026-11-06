---
id: PAR-PROC-04
title: "Quern: block, handstone, grinding, GUI-less interaction"
area: PROC
status: open
wave: 7
deps: [PAR-INFRA-02, PAR-INFRA-06, PAR-INFRA-13]
size: M
aliases: ["M3-PROC-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/quern"
files_owned:
  - "core/src/main/java/dev/tfp/core/quern/**"
  - "core/src/test/java/dev/tfp/core/quern/**"
  - "core/src/test/resources/dev/tfp/core/quern/**"
  - "paper/src/main/java/dev/tfp/paper/quern/**"
  - "paper/src/test/java/dev/tfp/paper/quern/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/quern/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/quern/**"
  - "content/generated/content/craftengine/tfp/configuration/quern/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
ui: true
in_game_steps:
  - "tfp debug place tfp:quern"
  - "add handstone; add grain; right-click handstone; screenshot"
plan:
  - "UNBLOCKED (manager, 2026-11-06): the hitbox files are now in files_owned. FIRST add the two tfc:quern rows (regenerated through the TfcShapes evaluator, not hand-written) and the tfp:quern demands + GeneratedCarrierTest id, then build the quern on that carrier."
  - "Blocked (2026-11-06): the CE block tfp:quern needs a hitbox carrier, and the carrier can only come from the selector (PAR-INFRA-13: HardcodedCarrierTest forbids a vanilla state or auto_state chosen outside dev/tfp/datagen/hitbox/). The selector has nothing for the quern: tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json has no tfc:quern rows (TFC QuernBlock: has_handstone=false selection = collision = BASE_SHAPE 16x10x16; has_handstone=true selection = FULL_SHAPE (base + handstone 3..13 x 10..13.76 + handle 4.34..5.36 up to 16.24, minus the input slot 6..10 above 13.76), collision = base + handstone), and tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java has no tfp:quern demands. Both files are outside this item files_owned (owner: the hitbox selector, PAR-INFRA-13 area). Needed first: a hitbox item that adds the two tfc:quern rows through the TfcShapes evaluator (regenerated, not hand-written) and the tfp:quern demands (look = our quern base model; the handstone is an entity_renderer overlay), plus the tfp:quern id in GeneratedCarrierTest. Then this item can start. Design notes for the next worker: tfp:quern would be the first production DeviceKind (3 slots, handstone limit 1, TFC slot filter tfc:quern_handstones, DROP); the record holds the grind start tick and finishes lazily at start + QuernRules.TICKS_PER_GRIND in the kind advance; output through Recipes.first(RecipeTypes.QUERN, input) + Recipes.output(result, input); block state has_handstone (TFC) plus a turn property (e.g. 0..15, the handstone display rotated 22.5 degrees per step) written with Block#setBlockData(BlockVisuals data, false) from a region task after the handler (CE keeps the block entity across state changes of the same block); CE renderers snap (no interpolation), which is a ledger divergence."
  - "CE quern with handstone item display (entity_renderer) spinning only while used (interpolated rotation steps on interaction); right-click with input places it, right-click handstone grinds (QuernRules 90 ticks), handstone damage per grind, output pops out; RotationSink hook left for PAR-MECH-06."
tests:
  - "paper: MockBukkit grind"
  - "core: QuernGoldenTest exists"
done_criteria:
  - "Grinding wheat grain gives flour in-game"
---

# PAR-PROC-04: Quern: block, handstone, grinding, GUI-less interaction

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
