---
id: PAR-MECH-15
title: "Axle carriers from the hitbox allocation: TFC axle outline rows, demands, RotationBlocks reads the agreed carriers"
area: MECH
status: done
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
  - "Done: TfcShapes rows tfc:wood/axle[axis=x|y|z] (AbstractShaftAxleBlock SHAPE_X/Y/Z, collision same; tfc-shapes.json regenerated), three HitboxDemands (AXLE_ID tfp:wood/axle, appearances x/y/z, TRANSPARENT, rare, AXLE_WEIGHT -22, served last so no other carrier moves), RotationBlocks.axleCarrier reads HitboxAllocation.agreed (no vanilla state literal left). The allocation does not give the lightning rods: they are crafted in TFC and keep their vanilla look (PAR-CLIM-27 KEEPS_VANILLA_LOOK), so all three axes are hitbox-only on minecraft:cactus[age=10] (ledger divergence; MECH-01's hardcoded rods drew real crafted lightning rods empty). Tests: RotationBlocksTest.axleCarriersComeFromTheAllocation, TfcShapesTest, HardcodedCarrierTest, CarrierPolicyRecipeTest pass (HitboxAllocationTest.neverSharesAStateWithTheFullCubeAllocation fails on HEAD from PAR-WORLD-31's GroundcoverCeBlocks nether_ore sound, unrelated)."
tests:
  - "datagen: HitboxReport lists the three axle demands; RotationBlocksTest reads the agreed carriers; HardcodedCarrierTest passes"
done_criteria:
  - "The axle carriers come from the allocation (no vanilla state literal in RotationBlocks)"
---

# PAR-MECH-15: Axle carriers from the hitbox allocation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
