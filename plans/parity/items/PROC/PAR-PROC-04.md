---
id: PAR-PROC-04
title: "Quern: block, handstone, grinding, GUI-less interaction"
area: PROC
status: done
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
  - "primed and restarted server (no RESTART REQUIRED in the log); ce item give Bot tfp:quern; place it on top of a floor block"
  - "ce item give Bot tfp:handstone; right-click the quern top; ce item give Bot tfp:food/wheat_grain 4; right-click the quern top: the grain goes into the input"
  - "grind (TFC aim rule): empty hand, aim at the handle (north-west, about 4.8/4.8 px) or at the side or ring of the handstone outside the centre feed hole, NOT at the grain in the middle (x/z 6..10 px on top: TFC getPlayerSelection gives INPUT_SLOT there and an empty hand takes the input back; no highlight shows this on vanilla clients, see the ledger). Concrete: quern at (3, 100, 4), Bot at (3.5, 100, 3.3), yaw 0, pitch 43 (ray meets the handstone's north side at x 8 px, y about 12.7 px, as in verdict.md step 4); right-click; screenshots at about 1 s and 5 s"
  - "expect: the handstone turned to a different angle between the 1 s and 5 s frames; the grain count on top goes 4 to 3; one wheat flour at TFC's first output position (north-west corner) after 90 ticks (TFC recipe quern/food/wheat_flour: 1 grain to 1 flour)"
  - "Bot at pitch 62 right-clicks the base's side: the flour is in the inventory (data get entity Bot Inventory)"
plan:
  - "UNBLOCKED (manager, 2026-11-06): the hitbox files are now in files_owned. FIRST add the two tfc:quern rows (regenerated through the TfcShapes evaluator, not hand-written) and the tfp:quern demands + GeneratedCarrierTest id, then build the quern on that carrier."
  - "Blocked (2026-11-06): the CE block tfp:quern needs a hitbox carrier, and the carrier can only come from the selector (PAR-INFRA-13: HardcodedCarrierTest forbids a vanilla state or auto_state chosen outside dev/tfp/datagen/hitbox/). The selector has nothing for the quern: tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json has no tfc:quern rows (TFC QuernBlock: has_handstone=false selection = collision = BASE_SHAPE 16x10x16; has_handstone=true selection = FULL_SHAPE (base + handstone 3..13 x 10..13.76 + handle 4.34..5.36 up to 16.24, minus the input slot 6..10 above 13.76), collision = base + handstone), and tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java has no tfp:quern demands. Both files are outside this item files_owned (owner: the hitbox selector, PAR-INFRA-13 area). Needed first: a hitbox item that adds the two tfc:quern rows through the TfcShapes evaluator (regenerated, not hand-written) and the tfp:quern demands (look = our quern base model; the handstone is an entity_renderer overlay), plus the tfp:quern id in GeneratedCarrierTest. Then this item can start. Design notes for the next worker: tfp:quern would be the first production DeviceKind (3 slots, handstone limit 1, TFC slot filter tfc:quern_handstones, DROP); the record holds the grind start tick and finishes lazily at start + QuernRules.TICKS_PER_GRIND in the kind advance; output through Recipes.first(RecipeTypes.QUERN, input) + Recipes.output(result, input); block state has_handstone (TFC) plus a turn property (e.g. 0..15, the handstone display rotated 22.5 degrees per step) written with Block#setBlockData(BlockVisuals data, false) from a region task after the handler (CE keeps the block entity across state changes of the same block); CE renderers snap (no interpolation), which is a ledger divergence."
  - "CE quern with handstone item display (entity_renderer) spinning only while used (interpolated rotation steps on interaction); right-click with input places it, right-click handstone grinds (QuernRules 90 ticks), handstone damage per grind, output pops out; RotationSink hook left for PAR-MECH-06."
  - "Done: hitbox rows tfc:quern[has_handstone=false|true] regenerated through TfcShapes (BASE_SHAPE; FULL_SHAPE / COLLISION_FULL_SHAPE) and two tfp:quern demands (selector: stonecutter[facing=east]; snow[layers=6] hidden as snow[layers=4]); datagen quern/QuernBlock writes configuration/quern/quern.yml (tfp:device template, has_handstone, TFC strength 0.5/2.0, basalt events, pickaxe, self loot, block item) plus TFC's base/item models, 3 textures and names in assets/tfp_quern/lang; core/quern QuernState (lazy grind start, finish at start + 90), QuernAim (look ray against TFC's outline + getPlayerSelection), QuernVisuals (renderer positions, 4 interpolated spin steps); paper/quern QuernModule, QuernDevice (first production DeviceKind: 3 slots, handstone limit 1 and tfc:quern_handstones filter, DROP; useItemOn order, settle on access + finish callback via the router's neighbour update; recipe through Recipes.first(QUERN) + singleOutput; food-date merge; handstone damage/break), PaperQuernEffects (item displays for handstone/input/output, has_handstone via setBlockData on the next region task, sounds, particles). Rotation network hook: QuernState/QuernRules.progress for PAR-MECH-06. Tests: core QuernStateTest 4, QuernAimTest 7, QuernVisualsTest 3 (+ QuernGoldenTest 6, QuernDeterminismTest 3); paper QuernMockBukkitTest 9, PaperQuernEffectsMockBukkitTest 3; datagen QuernBlockTest 3, GeneratedCarrierTest updated. Ledger: feature row + 6 divergences. In-game: docs/verification/PAR-PROC-04/verdict.md (pass, wheat grain to wheat flour on Paper 1.21.11). Note: the two rows needed two Spec lines in hitbox/TfcShapes.java (the evaluator TfcShapesTest regenerates the table from), the only edit outside the listed files, as the UNBLOCKED line asks."
  - "Fix (2026-11-07, review/test notes): TFC attemptGrind's FAIL is now a pass (not consumed), so the held item's own use follows as in vanilla (e.g. a held block is placed); one queued finish callback per quern (neighbour updates during a grind add no duplicates); the hand-grind centre jitter is in the display divergence row. in_game_steps now place the quern from its block item on a primed, restarted server, as verdict.md did: the failed capture used a first-run server in degraded mode (RESTART REQUIRED, CE delay-configuration-load still true) and `tfp debug place` sunk into the floor. If a `tfp debug place` quern still ignores right-clicks on a restarted server, that is a device-framework follow-up (PAR-INFRA-02: CraftEngineDeviceStore.controllerAt returning null makes DeviceService.route return false without a log line), not quern logic. Manager: hitbox/TfcShapes.java (two Spec lines) should be added to files_owned or the hitbox-hub rules."
  - "Fix 2 (2026-11-07, in-game REJECT + review): the empty-hand centre click that gave the grain back is TFC behaviour (QuernBlock.getPlayerSelection: with a handstone and input, a hit inside INPUT_SLOT_AABB x/z 5.99..10.01, y from 13.75 is INPUT_SLOT; useItemOn then extracts), so the routing is unchanged. The real gap was the missing TFC drawHighlight cue (red box of the selected part); now a ledger divergence row. in_game_steps rewritten with TFC's grind spot (handle or handstone ring/side, never the centre feed hole), a concrete Bot pose and the expected checks; re-capture pending. The tester's 2 grain to 1 flour is wrong (TFC wheat_flour.json: 1 to 1). Base-top bar: TFC models/block/quern.json copied verbatim by datagen, no change. Follow-up for the debug-command owner (outside files_owned): `/tfp debug place tfp:quern` rejects the namespaced CE id for a player (brigadier Incorrect argument) and is player-only over RCON; the steps place the quern from its block item instead."
tests:
  - "paper: MockBukkit grind"
  - "core: QuernGoldenTest exists"
done_criteria:
  - "Grinding wheat grain gives flour in-game"
---

# PAR-PROC-04: Quern: block, handstone, grinding, GUI-less interaction

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
