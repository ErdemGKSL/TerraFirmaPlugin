---
id: PAR-MECH-12
title: "Spinning shafts on the wire: speed hook, packet sender, axle without a static element, in-game check"
area: MECH
status: done
wave: 51
deps: [PAR-MECH-02, PAR-MECH-11]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render/blockentity/AxleBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/util/rotation/Rotation.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/rotation/spin/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/spin/**"
  - "paper/src/main/java/dev/tfp/paper/rotation/RotationModule.java"
  - "paper/src/main/java/dev/tfp/paper/rotation/RotationService.java"
  - "paper/src/main/java/dev/tfp/paper/rotation/PaperRotationWorld.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/RotationBlocks.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/RotationBlocksTest.java"
  - "docs/verification/PAR-MECH-12/**"
ui: false
in_game_steps:
  - "the PAR-MECH-01 setup (creative motor, 5 axles, gearbox, encased axle) at speed step 8; record 10 frames of each axle: the shafts turn at 30 rpm, the two sides of the gearbox in opposite hands; step 0 stops them; a joining player sees them already turning"
plan:
  - "Wire what PAR-MECH-02 built (core `RotationAnglesSpin`, `integration/craftengine/rotation/SpinVisualService` + `SpinSink`): (1) the rotation actor reports each shaft's direction, speed and the source phase (unbounded angle at a tick, shared by the network) to a `SpinSink` implementation in `paper/rotation/spin` that encodes PacketEvents `ADD_ENTITY` + metadata (item_display, `tfp:block/rotation/axle/<wood>` item, translation 0, left and right rotation both = quaternion about the axis by `halfAngle`, interpolation delay 0 and duration `duration`: metadata indices 8 delay, 9 duration of 1.21.11, check 26.x), entity ids from the server counter as the overlay sender does; (2) viewers from the chunk-send / unload packets as PAR-INFRA-16 does; (3) the shaft's static `entity_renderer` item display is removed from the axle appearances (the spin display is the only drawing, spawned also at rest) and the encased-axle/gearbox casing keeps its cube; (4) the per-tick `SpinVisualService.tick` call from the global scheduler; (5) in-game: confirm the left/right split interpolates to 340 degrees per packet on 1.21.11 and 26.x (if a client interpolates the pair differently, lower `RotationAnglesSpin.MAX_SWEEP` to one arc)."
  - "Done: SpinVisualService/SpinSink live in integration/craftengine, which depends on paper (not the reverse), so the paper side has its own engine on the same core planner: paper/rotation/spin (SpinShafts: shafts, network phases, due queue, chunk-packet viewers, spawn at the angle of now then the segment one tick later; PacketSpinWire: ADD_ENTITY + metadata 8/9/11-14/17/23/24, render item tfp:render/rotation/wood/axle/<wood>; SpinPacketHandler: JOIN_GAME/RESPAWN/CHUNK_DATA/UNLOAD_CHUNK; SpinVisuals: reflective PacketEvents side, the global spin clock, quit), RotationService.ShaftHook (each actor batch reports changed and removed axles), PaperRotationWorld.axleItem (the wood read on the region thread), RotationModule wiring, RotationBlocks.withoutShaftDisplays (no static element). Tests: SpinShaftsTest 9, SpinQuaternionsTest 3, SpinPacketsTest 2 (1.21.11 and 26.3 mappings), SpinWiringTest 2, RotationBlocksTest.axleAppearancesCarryNoStaticElement. Pending in-game check: moved to PAR-MECH-19 (steps and criterion), ui false here."
tests:
  - "paper: sink encodes the metadata of the golden segment; actor to service hook; MockBukkit viewers"
  - "datagen: the axle appearances carry no static element"
done_criteria:
  - "Deferred check tracked in PAR-MECH-19 (axles visibly spin in the right direction at the right speed on 1.21.11 and 26.x; the in-game criterion deferred from PAR-MECH-02)"
---

# PAR-MECH-12: Spinning shafts on the wire

> Created by PAR-MECH-02, which owns only the pure planner and the service: the speed hook in the rotation actor, the packet sender and the axle YAML belong to files PAR-MECH-11 owns (`paper/rotation/**`, `datagen/rotation/**`). Start after PAR-MECH-11 is done.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
