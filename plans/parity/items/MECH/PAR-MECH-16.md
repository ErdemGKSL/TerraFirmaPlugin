---
id: PAR-MECH-16
title: "Rotation in game: PAR-MECH-01 in-game steps and the rotation.stress itest on Paper and Folia"
area: MECH
status: open
wave: 53
deps: [PAR-MECH-01, PAR-MECH-15]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/rotation/**"
  - "paper/src/test/java/dev/tfp/paper/rotation/**"
  - "itest/src/main/java/dev/tfp/itest/rotation/**"
ui: true
in_game_steps:
  - "place a creative motor (axis x) + 5 oak axles + an oak gearbox (hammer the west and south faces) + an encased axle south of it; right-click the motor's south face 8 times; /tfp debug rotation on the encased axle: every block 30.00 rpm"
  - "a sixth axle in the line breaks after 4 ticks and drops itself; a powered clutch cuts the line (debug: disconnected), unpowered joins it"
  - "feel checklist (docs/research/ore-entity-renderer-design.md 6) on the encased axle, clutch, gearbox and motor: cracks, wood/metal sounds and particles, mining time, light noon/midnight/torch, no seams, outline, /fill and TNT leave no display, pick-block, no ring at distance; the axle shaft on its lightning-rod carrier: outline and no vanilla rod drawn"
  - "restart the server with the line standing: /tfp debug rotation on the line without touching it shows the network (the startup re-add of PAR-MECH-11)"
  - "run the itest case rotation.stress (itest/.../rotation/RotationStressCase) with spikes/run-matrix.sh on Paper 1.21.11 and Folia: passes and the server log has no thread-ownership findings"
plan:
  - "Moved from PAR-MECH-11 (PROTOCOL 6.5): its code is done (RotationStressCase written and registered in SelfTestSuite, PaperRotationWorld.reAddLoaded called from RotationModule.enable) but the server runs and the in-game steps were not run in that session. Run them with a cheap-model agent (exact commands in docs/testing/headless.md and spikes/run-matrix.sh), look at the screenshots in the main session, store evidence in docs/verification/PAR-MECH-16/ (and note PAR-MECH-01 and PAR-MECH-11 in the verdict). SelfTestSuiteTest counts the cases: raise its expected count by one if it is still hard-coded."
tests:
  - "itest: rotation.stress on Paper and Folia"
done_criteria:
  - "In-game steps pass on Paper 1.21.11 with screenshots and verdict in docs/verification/PAR-MECH-16/"
  - "rotation.stress passes on Paper and Folia with no thread-ownership findings"
  - "Blocks loaded before enable are in their networks without an interaction (restart step)"
---

# PAR-MECH-16: Rotation in game: PAR-MECH-01 in-game steps and the rotation.stress itest

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
