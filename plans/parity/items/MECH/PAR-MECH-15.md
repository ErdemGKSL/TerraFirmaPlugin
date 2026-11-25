---
id: PAR-MECH-15
title: "Axle carriers from the hitbox allocation: TFC axle outline rows, demands, RotationBlocks reads the agreed carriers"
area: MECH
status: open
wave: 52
deps: [PAR-MECH-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AbstractShaftAxleBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/TfcShapesTest.java"
ui: false
plan:
  - "Split from PAR-MECH-11 (which is not the owner of TfcShapes and tfc-shapes.json): tfc-shapes.json has only the Y outline of the axle (6,0,6,10,16,10: rock spike tip, anemometer); the X and Z outlines (AbstractShaftAxleBlock.SHAPE_X = box(0,6,6,16,10,10), SHAPE_Z = box(6,6,0,10,10,16)) have no row, so HitboxDemands cannot demand the three axle carriers yet."
  - "Add rows tfc:wood/axle[axis=x|y|z] to TfcShapes (spec from AbstractShaftAxleBlock, selection = collision) and regenerate tfc-shapes.json (TfcShapesTest compares); add three Demands in HitboxDemands (blockId tfp:wood/axle, appearances x, y, z, look TRANSPARENT, rare, a late weight so no other block's carrier moves; check GeneratedCarrierTest and HitboxAllocationTest); replace RotationBlocks.AXLE_CARRIERS with HitboxAllocation.agreed(...).state() (the stack must give the same lightning_rod east/up/south states, or the diff is a ledger divergence); this removes the three vanilla state literals that HardcodedCarrierTest reports in rotation/RotationBlocks.java."
tests:
  - "datagen: HitboxReport lists the three axle demands; RotationBlocksTest reads the agreed carriers; HardcodedCarrierTest passes"
done_criteria:
  - "The axle carriers come from the allocation (no vanilla state literal in RotationBlocks)"
---

# PAR-MECH-15: Axle carriers from the hitbox allocation

> Claimed: worker F (session_011osUpAUVyyokAxf5Kiw72D), 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
