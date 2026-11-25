---
id: PAR-MECH-20
title: "Bladed axle as TFC: the axle with a steel blade, its network node and spinning blade, the trip hammer's drive"
area: MECH
status: open
wave: 53
deps: [PAR-MECH-12, PAR-MECH-15]
size: S
aliases: ["PAR-MECH-10 (bladed axle; its plan did not match TFC)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/BladedAxleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/BladedAxleBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/BladedAxleBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/crafting/wood/bladed_axle/"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/**"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "core/src/main/java/dev/tfp/core/rotation/MechanicalBlock.java"
  - "core/src/test/java/dev/tfp/core/rotation/MechanicalBlockTest.java"
  - "docs/verification/PAR-MECH-20/**"
ui: true
in_game_steps:
  - "craft an oak bladed axle (oak axle + steel ingot, shapeless); place it in a line driven by the creative motor at step 8: it turns with the line at 30 rpm, its steel blade turning with the shaft; /tfp debug rotation lists it; break it: it drops itself"
plan:
  - "Created by PAR-MECH-10, whose plan (holds a saw, damages entities) does not match TFC at the pinned commit: BladedAxleBlock is an AbstractShaftAxleBlock (TFC's axle outline, the axle's connections, noOcclusion, strength 2.5, flammable like logs, push reaction destroy) with no behaviour of its own; BladedAxleBlockEntity is an AxleBlockEntity; the renderer draws the axle shaft plus a steel cuboid (7,10,6 to 9,17.5,10 px, texture tfc:block/metal/block/steel) turning with it; TripHammerBlockEntity.getRotation reads the rotation of the bladed axle directly above it. Recipe: shapeless, the wood's axle + c:ingots/steel."
  - "Datagen: tfp:wood/bladed_axle/<wood> (20 woods) in RotationBlocks with TFC's loot, settings and the axle's hitbox carriers (RotationBlocks.axleCarrier, PAR-MECH-15), no static element; the blade's render item (a model of TFC's cuboid, model-authoring skill). Paper: RotationBlockStates/RotationDevices parse it as an axle node (MechanicalBlock: a BLADED_AXLE that acts as AXLE in the network); the shaft hook reports it; SpinShafts draws the shaft and the blade (one display each, same segments) from the block's render items. Expose the rotation at a position for PAR-MECH-07 (trip hammer: the bladed axle above)."
tests:
  - "datagen: 20 bladed axle blocks, axle carriers, TFC loot and recipe"
  - "core/paper: a bladed axle joins a network like an axle; the shaft hook reports it; the trip hammer read of the axle above"
done_criteria:
  - "Bladed axles craft, place, join networks and spin with their blade (frames in docs/verification/PAR-MECH-20/)"
---

# PAR-MECH-20: Bladed axle as TFC

> Claimed: session_011osUpAUVyyokAxf5Kiw72D, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
