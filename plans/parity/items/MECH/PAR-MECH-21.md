---
id: PAR-MECH-21
title: "Crankshaft in game: the tfp:crankshaft CE block, its sink node in the rotation network (CrankRotation), the powered bellows in-game check"
area: MECH
status: open
wave: 54
deps: [PAR-MECH-05, PAR-MECH-20]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java"
  - "src/main/resources/data/tfc/loot_table/blocks/crankshaft.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crankshaft/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crankshaft/**"
  - "content/generated/content/craftengine/tfp/configuration/crankshaft/**"
  - "core/src/main/java/dev/tfp/core/rotation/MechanicalBlock.java"
  - "core/src/test/java/dev/tfp/core/rotation/MechanicalBlockTest.java"
  - "core/src/test/java/dev/tfp/core/rotation/CrankshaftSinkTest.java"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "docs/verification/PAR-MECH-21/**"
ui: true
in_game_steps:
  - "creative motor (axis z) -> axles -> crankshaft base (sink face = facing's counter-clockwise) with its shaft grown by a steel rod -> bellows facing away from the crank -> charcoal forge; the forge heats faster (debug air); screenshot the forge heat level and the bellows"
plan:
  - "Created by PAR-MECH-05, which owns only core/crankshaft and paper/crankshaft: the crankshaft rules (CrankshaftRules: shape, sink face, stroke, TFC tickBoth push cadence), its device kind (rod grows the shaft, shaft goes with its base), the powered bellows task (CrankshaftService), the pump's ShaftSource and the bellows' crank lookup exist, but nothing can place the block and no network feeds it."
  - "Datagen: tfp:crankshaft (TFC CrankshaftBlock: facing=north|east|south|west, part=base|shaft, getStateForPlacement facing = the placer's horizontal look clockwise, TFC's base and shaft outlines from BASE_SHAPES / SHAFT_SHAPES (+3 index quirk), metal sounds, TFC loot), behaviour tfp:device so CrankshaftDevices gets placed/use/neighbour/removed; the moving shaft and wheel visuals (TFC CrankshaftBlockEntityRenderer; model-authoring)."
  - "Rotation: a CRANKSHAFT sink state in MechanicalBlock (TFC SinkNode(pos, FACING.getCounterClockWise()), only the base is a node), RotationDevices/RotationBlockStates parse tfp:crankshaft[part=base]; RotationModule provides dev.tfp.paper.crankshaft.CrankRotation: the rotation a base receives, kept in a concurrent map the network actor updates after each batch (as the shaft hook), read without blocking. PAR-MECH-14 (pump in game) needs this too."
tests:
  - "datagen: the crankshaft block (8 states, outlines, loot, device behaviour)"
  - "core/paper: a crankshaft base joins a network as a sink on its counter-clockwise face; CrankRotation reports its rotation and empties when the network goes"
done_criteria:
  - "A water wheel (or creative motor) turns a crankshaft that drives a bellows into a charcoal forge in game (frames in docs/verification/PAR-MECH-21/)"
---

# PAR-MECH-21: Crankshaft in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
