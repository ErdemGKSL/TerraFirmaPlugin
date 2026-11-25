---
id: PAR-MECH-28
title: "Crankshaft outlines as TFC: tfc-shapes rows for BASE_SHAPES and SHAFT_SHAPES ((index + 3) % 4), HitboxDemands rows and the carriers from HitboxAllocation.agreed instead of the barrier"
area: MECH
status: open
wave: 56
deps: [PAR-MECH-21]
size: S
aliases: ["follow-up of PAR-MECH-26 (2026-11-25): the TFC shape table (TfcShapes, tfc-shapes.json) has no crankshaft rows and is outside PAR-MECH-26's files"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java"
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/TfcShapesTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/crankshaft/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crankshaft/**"
  - "content/generated/content/craftengine/tfp/configuration/crankshaft/**"
  - "docs/verification/PAR-MECH-28/**"
ui: true
in_game_steps:
  - "place a crankshaft base and its shaft (a steel rod on the base) for each facing; look at each with F3 or at the selection box: the base outlines TFC's plate and post (BASE_SHAPES), the shaft TFC's thin rod (SHAFT_SHAPES with (index + 3) % 4); screenshots of each"
plan:
  - "TfcShapes: one Spec per facing for tfc:crankshaft[facing=..,part=base] (BASE_SHAPES[get2DDataValue]: rotateShape(dir, 0,0,4,16,2,12) | rotateShape(dir, 0,2,4,4,12,12)) and part=shaft (SHAFT_SHAPES[(index + 3) % 4]: rotateShape(dir, 0,7,8,16,9,10)) with needles on CrankshaftBlock.getShape; regenerate tfc-shapes.json (TfcShapesTest)."
  - "HitboxDemands: tfp:crankshaft demands base_<facing> and shaft_<facing> (hitbox-only, TRANSPARENT, its own weight served after every other demand so no carrier of another block moves)."
  - "CrankshaftBlock: carriers from HitboxAllocation.agreed(ID, appearance) instead of RenderedTemplates.CARRIER (the base keeps its static base display; the shaft is drawn only by the PAR-MECH-26 packet displays, so its appearance stays hitbox-only); remove the outline divergence row in the ledger."
tests:
  - "datagen: the carrier of every state is the agreed allocation's; TfcShapesTest has the crankshaft rows"
done_criteria:
  - "The crankshaft's base and shaft outlines are TFC's (frames in docs/verification/PAR-MECH-28/)"
---

# PAR-MECH-28: Crankshaft outlines as TFC


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

> 2026-11-25 attempt (not committed): rows for BASE_SHAPES/SHAFT_SHAPES plus demands made the allocation fail with "hitbox overflow on 1.21: no free carrier for tfp:crankshaft#base_east" (20 datagen tests failed): the 8 crankshaft states need carrier states the 1.21 pool cannot spare. Needs a carrier-budget decision (fewer distinct shapes, or a shared carrier per facing pair) before retrying. Claim released.
