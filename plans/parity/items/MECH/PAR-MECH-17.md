---
id: PAR-MECH-17
title: "Water wheel and windmill as rotation network sources: source states in core/rotation, the WaterWheelNetwork and WindmillNetwork services"
area: MECH
status: open
wave: 53
deps: [PAR-MECH-03, PAR-MECH-04, PAR-MECH-12]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/util/rotation/SourceNode.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rotation/**"
  - "core/src/test/java/dev/tfp/core/rotation/**"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
ui: false
plan:
  - "Split from PAR-MECH-03 and PAR-MECH-04, which own only their own packages: `core/rotation/MechanicalBlock` knows the five PAR-MECH-01 blocks, so a water wheel or windmill cannot join a network yet. Their services sample every 40 ticks and call `dev.tfp.paper.waterwheel.WaterWheelNetwork.source(world, pos, axis, speed)` / `remove` and `dev.tfp.paper.windmill.WindmillNetwork` (same shape) when the speed changes; until a module provides these services they report to `NONE`."
  - "Add source states for both (a SourceNode on `ShaftAxis.faces()`, positive face, any float speed: `dev.tfp.core.power.Transmission.sourceNode`; the water wheel's speed may be negative) to `MechanicalBlock` (or a generic source state), let `RotationService` take them (`put` of a source state, then speed-only updates through `RotationNetworks.setSourceSpeed` so a speed change does not rebuild the network), and provide `WaterWheelNetwork` and `WindmillNetwork` from `RotationModule` (they enqueue on the world's actor). `/tfp debug rotation` names them (water_wheel, windmill). The windmill's axle replacement (`WindmillDevices.update` places the axle back when the last blade comes out; a blade on an axle places the windmill) must re-add the axle and remove the windmill node: check that CraftEngine's place fires the device placed/removed callbacks, else submit them here."
tests:
  - "core: a water wheel source at -MAX_SPEED drives an axle line (rpm and hand), a windmill source at 2 pi / 80 drives a gearbox"
  - "paper: WaterWheelNetwork/WindmillNetwork provided by RotationModule move the network (MockBukkit), a speed change keeps the network id"
done_criteria:
  - "A water wheel or windmill speed reported through its service turns the axles of its network (debug readout of speeds)"
---

# PAR-MECH-17: Water wheel and windmill as rotation network sources

> Created by PAR-MECH-03/PAR-MECH-04. Start after PAR-MECH-12 (which owns `paper/rotation/RotationService.java`, `RotationModule.java`) is done.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
