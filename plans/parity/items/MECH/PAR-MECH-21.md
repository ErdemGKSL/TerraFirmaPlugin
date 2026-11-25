---
id: PAR-MECH-21
title: "Crankshaft in game: the tfp:crankshaft CE block, its sink node in the rotation network (CrankRotation), the powered bellows in-game check"
area: MECH
status: done
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
ui: false
in_game_steps: []
plan:
  - "Created by PAR-MECH-05, which owns only core/crankshaft and paper/crankshaft: the crankshaft rules (CrankshaftRules: shape, sink face, stroke, TFC tickBoth push cadence), its device kind (rod grows the shaft, shaft goes with its base), the powered bellows task (CrankshaftService), the pump's ShaftSource and the bellows' crank lookup exist, but nothing can place the block and no network feeds it."
  - "Datagen: tfp:crankshaft (TFC CrankshaftBlock: facing=north|east|south|west, part=base|shaft, getStateForPlacement facing = the placer's horizontal look clockwise, TFC's base and shaft outlines from BASE_SHAPES / SHAFT_SHAPES (+3 index quirk), metal sounds, TFC loot), behaviour tfp:device so CrankshaftDevices gets placed/use/neighbour/removed; the moving shaft and wheel visuals (TFC CrankshaftBlockEntityRenderer; model-authoring)."
  - "Rotation: a CRANKSHAFT sink state in MechanicalBlock (TFC SinkNode(pos, FACING.getCounterClockWise()), only the base is a node), RotationDevices/RotationBlockStates parse tfp:crankshaft[part=base]; RotationModule provides dev.tfp.paper.crankshaft.CrankRotation: the rotation a base receives, kept in a concurrent map the network actor updates after each batch (as the shaft hook), read without blocking. PAR-MECH-14 (pump in game) needs this too."
  - "Done: datagen crankshaft/CrankshaftBlock (tfp:crankshaft, 8 states, TFC settings and per-part loot, the device behaviour, barrier carriers drawn by TFC's base and wheel models and the shaft's rest cuboids, item model, names); core MechanicalBlock.CRANKSHAFT (SinkNode on the facing's counter-clockwise face); paper rotation/CrankSinks (joins on placement with TFC's facing, on every device access through the tfp:crankshaft access hook, on its first read, in /tfp debug rotation; leaves on removal and chunk unload; provides CrankRotation from the actor's published rotations). Tests: CrankshaftBlockTest 5, CrankshaftSinkTest 3, MechanicalBlockTest +1, CrankSinksMockBukkitTest 6. Pending in-game check: PAR-MECH-25. Moving wheel and shaft, axle stub, TFC outlines: PAR-MECH-26."
tests:
  - "datagen: the crankshaft block (8 states, outlines, loot, device behaviour)"
  - "core/paper: a crankshaft base joins a network as a sink on its counter-clockwise face; CrankRotation reports its rotation and empties when the network goes"
done_criteria:
  - "Deferred check tracked in PAR-MECH-25 (a water wheel or creative motor turns a crankshaft that drives a bellows into a charcoal forge in game)"
---

# PAR-MECH-21: Crankshaft in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
