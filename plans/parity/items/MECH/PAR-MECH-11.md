---
id: PAR-MECH-11
title: "Rotation blocks: axle carriers in the hitbox allocation, startup re-add, in-game check of PAR-MECH-01"
area: MECH
status: open
wave: 50
deps: [PAR-MECH-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AbstractShaftAxleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotation/**"
  - "itest/src/main/java/dev/tfp/itest/rotation/**"
ui: true
in_game_steps:
  - "place a creative motor (axis x) + 5 oak axles + an oak gearbox (hammer the west and south faces) + an encased axle south of it; right-click the motor's south face 8 times; /tfp debug rotation on the encased axle: every block 30.00 rpm"
  - "a sixth axle in the line breaks after 4 ticks and drops itself; a powered clutch cuts the line (debug: disconnected), unpowered joins it"
  - "feel checklist (docs/research/ore-entity-renderer-design.md 6) on the encased axle, clutch, gearbox and motor: cracks, wood/metal sounds and particles, mining time, light noon/midnight/torch, no seams, outline, /fill and TNT leave no display, pick-block, no ring at distance; the axle shaft on its lightning-rod carrier: outline and no vanilla rod drawn"
plan:
  - "PAR-MECH-01 picks the axle carriers itself (RotationBlocks.AXLE_CARRIERS: lightning_rod east/up/south unpowered, the selector's score-1.0 shaft carriers; facing=down left to the rock spike tip) because HitboxDemands is not its file. Register three demands (tfp:wood/axle/* axis x/y/z, transparent) so HitboxReport sees the states as taken, and read the carriers from HitboxAllocation.agreed in RotationBlocks."
  - "Rotation blocks in chunks that were loaded before the rotation module enabled (spawn chunks at startup) join their network only at their first interaction, neighbour update or debug readout. Re-add them at enable (each loaded chunk's index on its region thread), or listen to the world load."
  - "Run the itest Folia stress case of PAR-MECH-01's plan (many motors and axle lines across region borders, chunk unload/load churn; no wrong-thread access, networks equal to a fresh load) and the in-game steps above; evidence in docs/verification/PAR-MECH-01/."
tests:
  - "datagen: HitboxReport lists the three axle demands; RotationBlocksTest reads the agreed carriers"
  - "MockBukkit: enable with loaded chunks re-adds their blocks"
  - "itest: rotation.stress on Paper and Folia"
done_criteria:
  - "The axle carriers come from the allocation"
  - "Blocks loaded before enable are in their networks without an interaction"
  - "In-game steps pass on Paper 1.21.11 with screenshots and verdict in docs/verification/PAR-MECH-01/"
---

# PAR-MECH-11: Rotation blocks: axle carriers in the hitbox allocation, startup re-add, in-game check of PAR-MECH-01

> Claimed: session_011osUpAUVyyokAxf5Kiw72D, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
