---
id: PAR-MECH-26
title: "Crankshaft as TFC: the turning wheel and the shaft's stroke (TFC CrankshaftBlockEntityRenderer), the axle stub, TFC's base and shaft outlines"
area: MECH
status: open
wave: 55
deps: [PAR-MECH-21]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crankshaft/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crankshaft/**"
  - "content/generated/content/craftengine/tfp/configuration/crankshaft/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "paper/src/main/java/dev/tfp/paper/rotation/spin/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/spin/**"
  - "docs/verification/PAR-MECH-26/**"
ui: true
in_game_steps:
  - "the PAR-MECH-25 layout at 30 rpm: the wheel turns about the base's facing axis and the shaft's arm and piston follow the stroke (calculateShaftMovement); an axle stub shows toward the connected axle; the base and shaft outlines are TFC's (hover with F3 or look at the selection box); screenshots of two frames"
plan:
  - "PAR-MECH-21 draws the base, its wheel and the shaft at rest (static entity_renderer item displays) on full-cube barrier carriers. TFC's renderer turns the wheel (Axis.XP of calculateRealRotationAngle + pi), moves the shaft (lengthEH, raiseAngle) and draws a 6/16 axle stub on the sink face while connected."
  - "Moving parts: packet displays like the spinning shafts (PAR-MECH-12), fed by the network actor's rotation of the base (CrankSinks / RotationService.rotation): the wheel as a turning display, the shaft as interpolated transforms of its three cuboids."
  - "Outlines: demands for tfp:crankshaft (BASE_SHAPES per facing, SHAFT_SHAPES with TFC's (index + 3) % 4) in HitboxDemands, carriers from HitboxAllocation.agreed instead of the barrier."
tests:
  - "datagen: the carriers per state; paper: the wheel display's angle follows the rotation"
done_criteria:
  - "The crankshaft's wheel and shaft move with the network, the outlines are TFC's (frames in docs/verification/PAR-MECH-26/)"
---

# PAR-MECH-26: Crankshaft as TFC

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
