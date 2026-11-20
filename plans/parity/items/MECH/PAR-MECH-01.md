---
id: PAR-MECH-01
title: "Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor"
area: MECH
status: done
wave: 49
deps: [PAR-INFRA-10, PAR-INFRA-08, PAR-INFRA-17]
size: M
aliases: ["M6-MECH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/rotation"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AxleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/GearBoxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/ClutchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rotation/**"
  - "core/src/test/java/dev/tfp/core/rotation/**"
  - "core/src/test/resources/dev/tfp/core/rotation/**"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rotation/**"
  - "content/generated/content/craftengine/tfp/configuration/rotation/**"
ui: true
in_game_steps:
  - "place creative motor + 5 axles + gearbox; tfp debug rotation (new)"
plan:
  - "Blocks persist their node; topology changes enqueue RotationNetworks updates (decide async, apply sync), split per region on Folia; port the 4 remaining RotationNetworkTest cycle cases."
  - "Full-cube mechanical blocks (encased axle, gearbox, clutch) drawn as renderer cubes use PAR-INFRA-17's `tfp:block/rendered_cube` template with destroy_stages and TFC sounds."
  - "Done: core `rotation/` (MechanicalBlock nodes of the five blocks, GearBoxRules hammer + canEnable, CreativeMotor steps and speed, RotationAxis, RotationWorld = TFC performNetworkAction driven by block states with the invalid flag; RotationNetworks gains a package-private transfer seam, parentOf, nodes); paper `rotation/` (RotationModule, five tfp:device kinds, RotationService = one serial network actor per world on the CPU pool, PaperRotationWorld: clutch powered and gearbox faces written next tick, TFC's 4-tick break of invalid blocks with their drop, chunk index tfp:rotation/nodes re-adds on chunk load and removes on unload, /tfp debug rotation); datagen `rotation/RotationBlocks` (configuration/rotation/{axle,encased_axle,clutch,gear_box,creative_motor}.yml: 20 woods, 1523 states; renderer cubes = tfp:block/rendered_cube expanded with states, TFC models under tfp:block/rotation/, display rotations as quaternions of TFC's blockstate x/y; axle on transparent lightning_rod carriers). Tests: core RotationNetworkCycleTest 4 (the 4 TFC cycle cases), MechanicalBlockTest 7, RotationNetworkManagerPortTest 24; paper RotationMockBukkitTest 6; datagen RotationBlocksTest 7. Pending in-game check and itest Folia stress: PAR-MECH-11 (with the axle carrier demands and the startup re-add)."
tests:
  - "core: RotationNetworkManagerPortTest + 4 cycle cases"
  - "itest: Folia stress"
done_criteria:
  - "A creative motor drives an axle line and gearbox (debug readout of speeds) (MockBukkit: RotationMockBukkitTest.creativeMotorDrivesAxleLineAndGearbox; pending in-game check, PAR-MECH-11)"
  - "Pending in-game check (PAR-MECH-11). Feel checklist of docs/research/ore-entity-renderer-design.md 6 passes for every renderer-drawn cube of this item (cracks via destroy_stages track progress, the material's particles and sounds, TFC mining time/tool tier, light at noon/midnight/torch, no z-fighting or seams, full-block outline and hitbox, no leftover display after /fill or TNT, pick-block gives the block's item, no ring at distance), screenshots in docs/verification/PAR-MECH-01/"
---

# PAR-MECH-01: Rotation network blocks: axle, encased axle, gearbox, clutch, creative motor


> see_not_parity: NP-VIS-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
